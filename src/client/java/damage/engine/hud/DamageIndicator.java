package damage.engine.hud;

import damage.engine.DamageEngineConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class DamageIndicator {
    private static final List<Indicator> indicators = new ArrayList<>();
    private static final long LIFETIME_MS = 1800;
    private static final int MAX_INDICATORS = 60;
    private static final float BASE_SCALE = 1.2f;
    private static final Random RANDOM = new Random();
    private static long lastCleanupTime = 0;
    
    private static Matrix4f capturedProjection = null;
    
    public static void captureProjection(Matrix4f proj) {
        capturedProjection = proj;
    }

    public static class Indicator {
        final double x, y, z;
        final float damage;
        final boolean isCrit;
        final boolean isKill;
        final long spawnTime;
        final float randomOffsetX;
        final float randomOffsetY;

        Indicator(double x, double y, double z, float damage, boolean isCrit, boolean isKill) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.damage = damage;
            this.isCrit = isCrit;
            this.isKill = isKill;
            this.spawnTime = System.currentTimeMillis();
            this.randomOffsetX = (RANDOM.nextFloat() - 0.5f) * 70f;
            this.randomOffsetY = (RANDOM.nextFloat() - 0.5f) * 44f + (isKill ? 20f : 0f); // Kill! spawned higher
        }
    }

    public static void addIndicator(double x, double y, double z, float damage, boolean isCrit, boolean isKill) {
        synchronized (indicators) {
            indicators.add(new Indicator(x, y, z, damage, isCrit, isKill));
            while (indicators.size() > MAX_INDICATORS) {
                indicators.remove(0);
            }
        }
    }

    public static void tickAndCleanup() {
        long now = System.currentTimeMillis();
        if (now - lastCleanupTime < 500) return;
        lastCleanupTime = now;

        synchronized (indicators) {
            indicators.removeIf(ind -> now - ind.spawnTime > LIFETIME_MS);
        }
    }

    public static void render(GuiGraphics context, float tickDelta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;
        if (minecraft.screen instanceof damage.engine.client.gui.DamageConfigScreen) return;
        if (minecraft.screen instanceof damage.engine.client.gui.HudEditorScreen) return;

        DamageEngineConfig config = DamageEngineConfig.getInstance();
        if (!config.showDamage) return;
        if (config.hideOnF1 && minecraft.options.hideGui) return;
        if (!config.showDamageIndicator) return;

        synchronized (indicators) {
            if (indicators.isEmpty()) return;

            Camera camera = minecraft.gameRenderer.getMainCamera();
            Vec3 camPos = camera.position();

            // Get projection matrix from GameRenderer (takes FOV in degrees)
            double fov = minecraft.options.fov().get();
            Matrix4f projectionMatrix = minecraft.gameRenderer.getProjectionMatrix((float) fov);
            
            Matrix4f viewMatrix = new Matrix4f()
                .translate((float) camPos.x, (float) camPos.y, (float) camPos.z)
                .rotate(camera.rotation())
                .invert();

            Matrix4f viewProjMatrix = new Matrix4f(projectionMatrix).mul(viewMatrix);

            int screenW = minecraft.getWindow().getGuiScaledWidth();
            int screenH = minecraft.getWindow().getGuiScaledHeight();
            long now = System.currentTimeMillis();
            Font textRenderer = minecraft.font;

            // Pre-compute view-projection matrix for screen projection
            List<RenderedIndicator> toRender = new ArrayList<>();

            for (Indicator ind : indicators) {
                float age = (now - ind.spawnTime) / 1000f;
                if (age < 0) continue;
                float lifetime = LIFETIME_MS / 1000f;
                float progress = age / lifetime;
                if (progress > 1.0f) continue;

                // World to clip space
                Vector4f worldPos = new Vector4f((float) ind.x, (float) ind.y, (float) ind.z, 1.0f);
                worldPos.mul(viewProjMatrix);

                if (worldPos.w() <= 0.001f) continue; // behind camera

                float ndcX = worldPos.x() / worldPos.w();
                float ndcY = worldPos.y() / worldPos.w();

                // Clamp to screen with margin
                if (ndcX < -1.5f || ndcX > 1.5f || ndcY < -1.5f || ndcY > 1.5f) continue;

                // Screen position
                float screenX = (ndcX * 0.5f + 0.5f) * screenW;
                float screenY = (1.0f - (ndcY * 0.5f + 0.5f)) * screenH;

                // Distance-based scaling: farther = smaller, naturally compatible with zoom mods
                float distScale = 8.0f / Math.max(worldPos.w(), 8.0f);
                distScale = Mth.clamp(distScale, 0.4f, 1.0f);

                // Animation
                float scale;
                float alpha;
                if (ind.isKill) {
                    // Kill!: big -> small impact animation
                    float startScale = 2.5f;
                    float endScale = 0.8f;
                    scale = Mth.lerp(progress, startScale, endScale);
                    if (progress < 0.08f) {
                        alpha = Mth.lerp(progress / 0.08f, 0.0f, 1.0f);
                    } else if (progress > 0.68f) {
                        float t = (progress - 0.68f) / 0.32f;
                        alpha = Mth.lerp(t, 1.0f, 0.0f);
                    } else {
                        alpha = 1.0f;
                    }
                } else if (progress < 0.12f) {
                    // Scale in: 0 -> 1
                    float t = progress / 0.12f;
                    scale = Mth.lerp(t, 0.2f, 1.0f);
                    alpha = Mth.lerp(t, 0.0f, 1.0f);
                } else if (progress > 0.78f) {
                    // Scale out: 1 -> 0
                    float t = (progress - 0.78f) / 0.22f;
                    scale = Mth.lerp(t, 1.0f, 0.3f);
                    alpha = Mth.lerp(t, 1.0f, 0.0f);
                } else {
                    scale = 1.0f;
                    alpha = 1.0f;
                }

                // Float up (Kill! floats faster to separate from damage number)
                float floatOffset = age * (ind.isKill ? 55f : 35f);

                // Determine text and color
                String text;
                int argbColor;
                if (ind.isKill) {
                    text = "Kill!";
                    argbColor = config.damageIndicatorKillColor;
                } else if (ind.isCrit) {
                    text = formatDamage(ind.damage, config.decimalPlaces);
                    argbColor = config.damageIndicatorCritColor;
                } else {
                    text = formatDamage(ind.damage, config.decimalPlaces);
                    argbColor = config.damageIndicatorNormalColor;
                }

                int baseAlpha = (int) (alpha * 255);
                if (baseAlpha < 5) continue;
                int color = (argbColor & 0x00FFFFFF) | (baseAlpha << 24);

                float finalX = screenX + ind.randomOffsetX;
                float finalY = screenY + ind.randomOffsetY - floatOffset;

                toRender.add(new RenderedIndicator(text, finalX, finalY, scale * distScale, color));
            }

            // Render all indicators
            for (RenderedIndicator ri : toRender) {
                context.pose().pushMatrix();
                context.pose().translate(ri.x, ri.y);
                context.pose().scale(ri.scale * BASE_SCALE, ri.scale * BASE_SCALE);

                int textWidth = textRenderer.width(ri.text);
                context.drawString(textRenderer, ri.text, -textWidth / 2, -(textRenderer.lineHeight / 2), ri.color, true);

                context.pose().popMatrix();
            }
        }
    }

    private static String formatDamage(float damage, int decimalPlaces) {
        if (decimalPlaces <= 0) {
            return String.valueOf(Math.round(damage));
        }
        String format = "%." + decimalPlaces + "f";
        return String.format(format, damage);
    }

    private record RenderedIndicator(String text, float x, float y, float scale, int color) {}

    public static void clearAll() {
        synchronized (indicators) {
            indicators.clear();
        }
    }
}
