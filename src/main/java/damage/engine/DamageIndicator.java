package damage.engine;

import damage.engine.hud.format.DamageNumberFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** 世界坐标锚定的伤害跳字（1.12.2 版，自行做投影）。 */
public final class DamageIndicator {
    private static final long LIFETIME_MS = 1200L;
    /** 扩散漂移持续时间（毫秒），与 1.21.1 一致。 */
    private static final float DRIFT_DURATION_MS = 2300f;
    /** 击杀标记固定显示在命中点上方，避免与随机散布的伤害数字重叠。 */
    private static final float KILL_TEXT_OFFSET = 20f;
    private static final java.util.Random RANDOM = new java.util.Random();
    private static final List<Entry> ENTRIES = new CopyOnWriteArrayList<>();

    private DamageIndicator() {
    }

    private static final class Entry {
        final double x;
        final double y;
        final double z;
        final float damage;
        final boolean crit;
        final boolean kill;
        final int victimId;
        final long spawnTime;
        /** 环状散布方向（单位向量）与漂移速度 / 初始环半径。 */
        final float moveDirX;
        final float moveDirY;
        final float moveSpeed;
        final float ringRadius;

        Entry(double x, double y, double z, float damage, boolean crit, boolean kill, int victimId) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.damage = damage;
            this.crit = crit;
            this.kill = kill;
            this.victimId = victimId;
            this.spawnTime = System.currentTimeMillis();
            // 随机角度同时用于环状出生位置与扩散方向
            float angle = RANDOM.nextFloat() * (float) (Math.PI * 2.0);
            this.moveDirX = (float) Math.cos(angle);
            this.moveDirY = (float) Math.sin(angle);
            this.moveSpeed = 15f + RANDOM.nextFloat() * 15f;
            this.ringRadius = 12f + RANDOM.nextFloat() * 16f;
        }
    }

    public static void add(double x, double y, double z, float damage, boolean crit, boolean kill) {
        add(x, y, z, damage, crit, kill, -1);
    }

    public static void add(double x, double y, double z, float damage, boolean crit, boolean kill, int victimId) {
        DamageEngineConfig config = DamageEngineConfig.getInstance();
        int max = Math.max(0, config.indicatorMaxCount);
        // 击杀时同时生成「本次伤害」与「击杀文本」两条，避免最后一击只剩 Kill
        if (config.showDamageIndicator && damage > 0f) {
            push(new Entry(x, y, z, damage, crit, false, victimId), max);
        }
        if (kill && config.showKillIndicator) {
            push(new Entry(x, y, z, damage, crit, true, victimId), max);
        }
    }

    private static void push(Entry entry, int max) {
        while (max > 0 && ENTRIES.size() >= max) {
            ENTRIES.remove(0);
        }
        ENTRIES.add(entry);
    }

    public static void clear() {
        ENTRIES.clear();
    }

    private static float clamp01(float value) {
        return value < 0f ? 0f : (value > 1f ? 1f : value);
    }

    /** 当前存活的跳字数量（调试叠加层用）。 */
    public static int activeCount() {
        return ENTRIES.size();
    }

    /** 在 HUD 覆盖层绘制（此时是 2D 坐标空间）。 */
    public static void render(float partialTicks) {
        if (ENTRIES.isEmpty()) return;

        Minecraft mc = Minecraft.getMinecraft();
        Entity view = mc.getRenderViewEntity();
        if (view == null) return;

        // 明确开启混合，保证按 alpha 淡入淡出（其它 HUD 模块可能留下不同的混合状态）
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);

        double camX = view.lastTickPosX + (view.posX - view.lastTickPosX) * partialTicks;
        double camY = view.lastTickPosY + (view.posY - view.lastTickPosY) * partialTicks + view.getEyeHeight();
        double camZ = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * partialTicks;

        float yaw = view.prevRotationYaw + (view.rotationYaw - view.prevRotationYaw) * partialTicks;
        float pitch = view.prevRotationPitch + (view.rotationPitch - view.prevRotationPitch) * partialTicks;
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);

        // 相机基向量（MC: yaw 0 = +Z，pitch 正 = 向下看）
        double fx = -Math.sin(yawRad) * Math.cos(pitchRad);
        double fy = -Math.sin(pitchRad);
        double fz = Math.cos(yawRad) * Math.cos(pitchRad);
        // 右向量 = forward × up，符号与正向相反，否则水平方向会跟着视角同向漂移
        double rx = -Math.cos(yawRad);
        double rz = -Math.sin(yawRad);
        double ux = -Math.sin(yawRad) * Math.sin(pitchRad);
        double uy = Math.cos(pitchRad);
        double uz = Math.cos(yawRad) * Math.sin(pitchRad);

        ScaledResolution resolution = new ScaledResolution(mc);
        int width = resolution.getScaledWidth();
        int height = resolution.getScaledHeight();
        double focal = (height / 2.0) / Math.tan(Math.toRadians(mc.gameSettings.fovSetting) / 2.0);

        DamageEngineConfig config = DamageEngineConfig.getInstance();
        FontRenderer font = mc.fontRenderer;
        long now = config.indicatorNow();

        for (Entry entry : ENTRIES) {
            long age = now - entry.spawnTime;
            if (age > LIFETIME_MS) {
                ENTRIES.remove(entry);
                continue;
            }

            // 追踪模式：off = 停在命中点，entity = 跟随受害实体，crosshair = 跟随准心
            double px = entry.x;
            double py = entry.y;
            double pz = entry.z;
            if ("entity".equals(config.indicatorTrackMode) && entry.victimId >= 0 && mc.world != null) {
                Entity victim = mc.world.getEntityByID(entry.victimId);
                if (victim != null) {
                    px = victim.posX;
                    py = victim.posY + victim.height * 0.6;
                    pz = victim.posZ;
                }
            } else if ("crosshair".equals(config.indicatorTrackMode)) {
                px = camX + fx * 6.0;
                py = camY + fy * 6.0;
                pz = camZ + fz * 6.0;
            }

            double dx = px - camX;
            double dy = py - camY;
            double dz = pz - camZ;

            double depth = dx * fx + dy * fy + dz * fz;
            if (depth <= 0.1) continue;

            double sx = dx * rx + dz * rz;
            double sy = dx * ux + dy * uy + dz * uz;

            double screenX = width / 2.0 + sx / depth * focal;
            double screenY = height / 2.0 - sy / depth * focal;

            // 环状散布 + 增强模式向外扩散（与 1.21.1 一致），不再整体上浮
            float offsetX;
            float offsetY;
            if (entry.kill) {
                offsetX = 0f;
                offsetY = -KILL_TEXT_OFFSET;
            } else {
                float spread = entry.ringRadius;
                if (!config.reduceAnimations && "enhanced".equals(config.indicatorMode)) {
                    float driftT = clamp01(age / DRIFT_DURATION_MS);
                    float eased = 1.0f - (1.0f - driftT) * (1.0f - driftT) * (1.0f - driftT);
                    spread += entry.moveSpeed * (DRIFT_DURATION_MS / 1000f) * eased;
                }
                offsetX = entry.moveDirX * spread;
                offsetY = entry.moveDirY * spread;
            }
            screenX += offsetX;
            screenY += offsetY;

            float t = age / (float) LIFETIME_MS;
            // 淡入 8%，后半段渐隐（避免数字在末段硬闪一下就消失）
            float alpha = t < 0.08f ? (t / 0.08f)
                : (t > 0.5f ? 1.0f - (t - 0.5f) / 0.5f : 1.0f);
            alpha *= config.indicatorOpacity / 100.0f;
            if (alpha <= 0.02f) continue;

            String text;
            int color;
            if (entry.kill) {
                text = config.killText;
                color = config.killTextColor;
            } else {
                String value = DamageNumberFormat.formatDamage(entry.damage, config.indicatorDecimalPlaces);
                text = config.indicatorPrefixSign ? "-" + value : value;
                color = entry.crit ? config.damageIndicatorCritColor : config.damageIndicatorNormalColor;
            }
            if (config.indicatorBold) {
                text = "\u00a7l" + text;
            }

            int argb = (Math.round(alpha * 255.0f) << 24) | (color & 0x00FFFFFF);

            GlStateManager.pushMatrix();
            GlStateManager.translate(screenX, screenY, 0.0);
            GlStateManager.scale(config.indicatorScale, config.indicatorScale, 1.0f);
            font.drawStringWithShadow(text, -font.getStringWidth(text) / 2.0f, 0.0f, argb);
            GlStateManager.popMatrix();
        }

        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }
}
