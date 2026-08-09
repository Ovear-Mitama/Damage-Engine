package damage.engine.hud;

import damage.engine.DamageEngineConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Camera;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class DamageIndicator {
    private static final List<Indicator> indicators = new ArrayList<>();
    private static final int MAX_INDICATORS = 60;
    private static final Random RANDOM = new Random();

    // Animation timing constants
    private static final float SHRINK_DURATION = 0.3f;   // shrink from big to small
    private static final float HOLD_DURATION = 2.0f;      // hold at small size
    private static final float FADE_OUT_DURATION = 2.0f;  // fade out
    private static final float TOTAL_DURATION = SHRINK_DURATION + HOLD_DURATION + FADE_OUT_DURATION; // ~4.3s

    private static final float START_SCALE = 2.0f;
    private static final float END_SCALE = 1.0f;

    /** Real game projection matrix, captured during world rendering. */
    private static Matrix4f capturedProjection = null;
    /** Real game view (modelview) matrix, captured during world rendering. */
    private static Matrix4f capturedViewMatrix = null;
    private static long lastCleanupTime = 0;

    public static void captureProjection(Matrix4f proj) {
        capturedProjection = proj;
    }

    public static void captureMatrices(Matrix4f proj, Matrix4f view) {
        capturedProjection = proj;
        capturedViewMatrix = view;
    }

    public static class Indicator {
        /** Victim entity to anchor the float to; -1 = fixed world position. */
        final int entityId;
        /** Fallback world position (server snapshot), used while the entity is gone. */
        final double x, y, z;
        final float damage;
        final boolean isCrit;
        final boolean isKill;
        final boolean isHeal;
        final long spawnTime;
        // Drift direction (consistent with ring spawn angle)
        final float moveDirX;
        final float moveDirY;
        final float moveSpeed;
        // Same angle used for both ring spawn position and drift direction
        final float spawnAngle;

        Indicator(int entityId, double x, double y, double z, float damage, boolean isCrit, boolean isKill, boolean isHeal) {
            this.entityId = entityId;
            this.x = x;
            this.y = y;
            this.z = z;
            this.damage = damage;
            this.isCrit = isCrit;
            this.isKill = isKill;
            this.isHeal = isHeal;
            this.spawnTime = System.currentTimeMillis();
            // Consistent angle for both ring spawn and drift direction
            int seed = (int)(this.spawnTime % 1000);
            this.spawnAngle = ((seed * 17) % 360) * (float) Math.PI / 180f;
            this.moveDirX = (float) Math.cos(spawnAngle);
            this.moveDirY = (float) Math.sin(spawnAngle);
            // Speed: 15-30 pixels per second
            this.moveSpeed = 15f + RANDOM.nextFloat() * 15f;
        }
    }

    /**
     * @param entityId victim entity to anchor the float to (its live position is used
     *                 every frame while it exists); pass -1 to pin to the given world
     *                 coordinates
     */
    public static void addIndicator(int entityId, double x, double y, double z, float damage, boolean isCrit, boolean isKill) {
        addIndicator(entityId, x, y, z, damage, isCrit, isKill, false);
    }

    public static void addIndicator(int entityId, double x, double y, double z, float damage, boolean isCrit, boolean isKill, boolean isHeal) {
        synchronized (indicators) {
            indicators.add(new Indicator(entityId, x, y, z, damage, isCrit, isKill, isHeal));
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
            indicators.removeIf(ind -> now - ind.spawnTime > TOTAL_DURATION * 1000);
        }
    }

    public static void render(GuiGraphics guiGraphics, float tickDelta) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) return;
        if (client.screen instanceof damage.engine.client.gui.DamageConfigScreen) return;
        if (client.screen instanceof damage.engine.client.gui.HudEditorScreen) return;

        DamageEngineConfig config = DamageEngineConfig.getInstance();
        if (!config.showDamage) return;
        if (config.hideOnF1 && client.options.hideGui) return;
        if (!config.showDamageIndicator) return;

        synchronized (indicators) {
            if (indicators.isEmpty()) return;

            // Build the view/projection matrices from the live camera every frame,
            // identical to what the game itself renders with. No matrix capture is
            // needed (capture timing/content caused all floats to collapse to one
            // screen position).
            Camera camera = client.gameRenderer.getMainCamera();
            Vec3 camPos = camera.getPosition();
            int screenW = client.getWindow().getGuiScaledWidth();
            int screenH = client.getWindow().getGuiScaledHeight();
            float aspect = (float) screenW / (float) screenH;

            // View/projection for MC 1.20.1. Two paths:
            // 1) Captured matrices (preferred): the game's modelview is camera-
            //    relative (pure rotation, m30/m31/m32 = 0), so the float world pos
            //    must be shifted by -camera pos BEFORE multiplying, exactly like the
            //    game's own entity vertices.
            // 2) Self-built fallback: full view matrix containing translation, so
            //    absolute world coords are used directly (with a clip-Y flip which
            //    user tests proved is needed for this fallback).
            boolean useCaptured = capturedProjection != null && capturedViewMatrix != null;
            Matrix4f viewProjMatrix;
            if (useCaptured) {
                viewProjMatrix = new Matrix4f(capturedProjection).mul(capturedViewMatrix);
            } else {
                Matrix4f viewMatrix = new Matrix4f()
                    .rotate(camera.rotation())
                    .translate((float) -camPos.x, (float) -camPos.y, (float) -camPos.z);
                Matrix4f projectionMatrix = capturedProjection != null
                    ? new Matrix4f(capturedProjection)
                    : new Matrix4f().perspective((float) Math.toRadians(client.options.fov().get()), aspect, 0.05f, 1000.0f);
                viewProjMatrix = new Matrix4f(projectionMatrix).mul(viewMatrix);
            }

            long now = System.currentTimeMillis();
            Font font = client.font;

            boolean isEnhanced = "enhanced".equals(config.indicatorMode);
            float baseScale = config.indicatorScale;
            float opacity = config.indicatorOpacity / 100f;

            List<RenderedIndicator> toRender = new ArrayList<>();

            for (Indicator ind : indicators) {
                float age = (now - ind.spawnTime) / 1000f;
                if (age < 0) continue;
                if (age > TOTAL_DURATION) continue;

                // The float is pinned to the world position where the hit happened
                // (server snapshot) - it does NOT follow the entity as it moves
                // (classic damage-number behaviour).
                double wx = ind.x, wy = ind.y, wz = ind.z;

                Vector4f worldPos;
                if (useCaptured) {
                    // camera-relative: shift by -camera pos first
                    worldPos = new Vector4f((float) (wx - camPos.x), (float) (wy - camPos.y), (float) (wz - camPos.z), 1.0f);
                    worldPos.mul(viewProjMatrix);
                } else {
                    worldPos = new Vector4f((float) wx, (float) wy, (float) wz, 1.0f);
                    worldPos.mul(viewProjMatrix);
                    // MC 1.20.1 pitch fix for the self-built fallback (logs proved X
                    // was correct but Y was inverted). Not needed for captured matrices.
                    worldPos.y = -worldPos.y;
                }
                // Skip only if the anchor is behind the camera
                if (worldPos.w() <= 0.001f) continue;

                float ndcX = worldPos.x() / worldPos.w();
                float ndcY = worldPos.y() / worldPos.w();

                // Clamp NDC to keep indicator within screen bounds
                ndcX = Mth.clamp(ndcX, -2.0f, 2.0f);
                ndcY = Mth.clamp(ndcY, -2.0f, 2.0f);

                // Entity screen position from world projection
                float entityScreenX = (ndcX * 0.5f + 0.5f) * screenW;
                float entityScreenY = (1.0f - (ndcY * 0.5f + 0.5f)) * screenH;

                // Distance factor: far entities get smaller drift/ring
                float distW = Math.abs(worldPos.w());
                float distFactor = Mth.clamp(5f / distW, 0.3f, 1.0f); // 0.3-1.0, small = far

                // Indicator follows entity screen position exactly - no center pull
                float screenX = entityScreenX;
                float screenY = entityScreenY;

                // Ring area offset from entity, scaled by distance (slightly offset from crosshair)
                int seed = (int)(ind.spawnTime % 1000);
                float ringRadius = (18f + (seed * 13) % 14) * distFactor;
                screenX += ind.moveDirX * ringRadius;
                screenY += ind.moveDirY * ringRadius;

                // Screen-space offset to place the float above the entity (scales with distance)
                screenY -= 20f * distFactor;

                // Animation phases
                float scale;
                float alpha;

                if (age < SHRINK_DURATION) {
                    // Phase 1: fade in + shrink from big to small
                    float t = age / SHRINK_DURATION;
                    // Fade in
                    alpha = Mth.lerp(t, 0.0f, 1.0f);
                    // Shrink from START_SCALE to END_SCALE
                    scale = Mth.lerp(t, START_SCALE, END_SCALE);
                } else if (age < SHRINK_DURATION + HOLD_DURATION) {
                    // Phase 2: hold at small size
                    scale = END_SCALE;
                    alpha = 1.0f;
                } else {
                    // Phase 3: fade out
                    float t = (age - SHRINK_DURATION - HOLD_DURATION) / FADE_OUT_DURATION;
                    scale = END_SCALE;
                    alpha = Mth.lerp(t, 1.0f, 0.0f);
                }

                alpha *= opacity;

                // Movement for enhanced mode - drift away, scaled by distance
                float moveOffsetX = 0f;
                float moveOffsetY = 0f;
                if (isEnhanced && !ind.isKill) {
                    float moveProgress = Math.min(age / (SHRINK_DURATION + HOLD_DURATION), 1.0f);
                    float easedT = 1.0f - (1.0f - moveProgress) * (1.0f - moveProgress);
                    float distance = ind.moveSpeed * easedT * distFactor;
                    moveOffsetX = ind.moveDirX * distance;
                    moveOffsetY = ind.moveDirY * distance;
                }

                // Kill text uses special handling
                String text;
                int argbColor;
                if (ind.isKill) {
                    text = config.killText;
                    argbColor = config.killTextColor;
                } else if (ind.isHeal) {
                    text = (config.indicatorPrefixSign ? "+" : "") + formatDamage(ind.damage, config.indicatorDecimalPlaces);
                    argbColor = config.healIndicatorColor;
                } else if (ind.isCrit) {
                    text = (config.indicatorPrefixSign ? "-" : "") + formatDamage(ind.damage, config.indicatorDecimalPlaces);
                    argbColor = config.damageIndicatorCritColor;
                } else {
                    text = (config.indicatorPrefixSign ? "-" : "") + formatDamage(ind.damage, config.indicatorDecimalPlaces);
                    argbColor = config.damageIndicatorNormalColor;
                }

                int baseAlpha = (int) (alpha * 255);
                if (baseAlpha < 5) continue;
                int color = (argbColor & 0x00FFFFFF) | (baseAlpha << 24);

                float finalX = screenX + moveOffsetX;
                float finalY = screenY + moveOffsetY;
                // Offset kill text above damage numbers to avoid overlap
                if (ind.isKill) {
                    finalY -= 12;
                }

                float finalScale = scale * baseScale;
                // Slight size variation based on damage amount
                float damageScaleFactor = 1.0f + Mth.clamp(ind.damage / 100f, 0f, 0.3f);
                finalScale *= damageScaleFactor;
                // Subtle distance-based scale: far entities slightly smaller (10% reduction at max distance)
                float distanceScaleFactor = 0.9f + distFactor * 0.1f;
                finalScale *= distanceScaleFactor;

                toRender.add(new RenderedIndicator(text, finalX, finalY, finalScale, color, ind.isKill));
            }

            // Render all indicators. TaCZ's crosshair hit feedback enables depth
            // test / blend and never restores them, so later HUD draws (ours at the
            // Gui TAIL included) inherit polluted state and flicker in sync with the
            // crosshair. Force a stable state for our text, then leave a clean GUI
            // state behind for anything rendered afterwards.
            RenderSystem.disableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            for (RenderedIndicator ri : toRender) {
                guiGraphics.pose().pushPose();
                guiGraphics.pose().translate(ri.x, ri.y, 0);
                guiGraphics.pose().scale(ri.scale, ri.scale, 1.0f);

                int textY = -(font.lineHeight / 2);

                if (config.indicatorBold) {
                    // Use Minecraft's built-in bold formatting
                    var boldText = Component.literal(ri.text).withStyle(ChatFormatting.BOLD).getVisualOrderText();
                    int textWidth = font.width(boldText);
                    guiGraphics.drawString(font, boldText, -textWidth / 2, textY, ri.color);
                } else {
                    int textWidth = font.width(ri.text);
                    guiGraphics.drawString(font, ri.text, -textWidth / 2, textY, ri.color);
                }

                guiGraphics.pose().popPose();
            }
            // Leave clean state for the rest of the frame / next frame.
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableDepthTest();
        }
    }

    private static String formatDamage(float damage, int decimalPlaces) {
        if (decimalPlaces <= 0) {
            return String.valueOf(Math.round(damage));
        }
        String format = "%." + decimalPlaces + "f";
        return String.format(format, damage);
    }

    private record RenderedIndicator(String text, float x, float y, float scale, int color, boolean isKill) {}

    public static void clearAll() {
        synchronized (indicators) {
            indicators.clear();
        }
    }
}
