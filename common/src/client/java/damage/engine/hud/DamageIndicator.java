package damage.engine.hud;

import damage.engine.DamageEngineConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.Camera;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class DamageIndicator {
    private static final List<Indicator> indicators = new ArrayList<>();
    private static final Random RANDOM = new Random();

    // Animation timing constants
    private static final float SHRINK_DURATION = 0.3f;   // shrink from big to small
    private static final float HOLD_DURATION = 2.0f;      // hold at small size
    private static final float FADE_OUT_DURATION = 2.0f;  // fade out
    private static final float TOTAL_DURATION = SHRINK_DURATION + HOLD_DURATION + FADE_OUT_DURATION; // ~4.3s

    private static final float START_SCALE = 2.0f;
    private static final float END_SCALE = 1.0f;

    /** 合并跳字时的一次性「跳动」：汇总数字微撑大再回落，360ms 内从 1.18 平滑回到 1.0。 */
    private static final float MERGE_POP_MS = 360f;
    private static final float MERGE_POP_SCALE = 1.18f;

    /** 无实体信息时，合并判定的出生点距离上限（格）。 */
    private static final double MERGE_DIST = 0.35;
    private static final double MERGE_DIST_SQ = MERGE_DIST * MERGE_DIST;

    private static long lastCleanupTime = 0;

    /** 未发生合并时，淡出在出生后的第几毫秒开始（入场 + 保持）。 */
    private static float naturalFadeStartMs() {
        return (SHRINK_DURATION + HOLD_DURATION) * 1000f;
    }

    /** 该跳字的淡出起点（出生后的秒数）；被合并推迟过则用推迟后的值。 */
    private static float fadeStartSeconds(Indicator ind) {
        return ind.fadeStartOffsetMs >= 0f ? ind.fadeStartOffsetMs / 1000f : (SHRINK_DURATION + HOLD_DURATION);
    }

    /** 该跳字是否已经进入淡出阶段（已淡出的不再参与合并）。 */
    private static boolean hasStartedFade(Indicator ind, long now) {
        float startAtMs = ind.fadeStartOffsetMs >= 0f ? ind.fadeStartOffsetMs : naturalFadeStartMs();
        return (now - ind.spawnTime) >= startAtMs;
    }

    /** 是否仍在生命周期内：淡出被合并推迟多少，寿命就延长多少。 */
    private static boolean isAlive(Indicator ind, long now) {
        float lifeMs = TOTAL_DURATION * 1000f;
        if (ind.fadeStartOffsetMs >= 0f) {
            lifeMs += ind.fadeStartOffsetMs - naturalFadeStartMs();
        }
        return now - ind.spawnTime <= lifeMs;
    }

    /** 两个跳字是否属于同一目标：优先实体 id，无实体信息时按出生点相近。 */
    private static boolean sameTarget(Indicator ind, int vid, Entity victim, double x, double y, double z) {
        if (vid >= 0) {
            if (ind.victimId == vid) return true;
            if (ind.victimRef != null && victim != null && ind.victimRef == victim) return true;
            return false;
        }
        if (ind.victimId < 0) {
            double dx = ind.x - x, dy = ind.y - y, dz = ind.z - z;
            return dx * dx + dy * dy + dz * dz < MERGE_DIST_SQ;
        }
        return false;
    }

    public static class Indicator {
        double x, y, z;
        float damage;
        boolean isCrit;
        final boolean isKill;
        final boolean isHeal;
        final long spawnTime;
        /** 受害实体 id（-1 = 无/不可知；追踪实体与合并跳字依赖它）。 */
        final int victimId;
        /** 缓存的受害实体引用（渲染时刷新，实体移除后为 null）。 */
        Entity victimRef;
        /** 追踪首帧算出的「实体脚下中心 → 出生点」相对偏移；null = 尚未初始化。 */
        double[] trackOff;
        /** 最近一次合并跳字的时刻（ms，-1 = 未发生过/动画已结束），用于合并时的跳动缩放。 */
        long mergePopStart = -1;
        /**
         * 淡出起点在「出生时间」时间轴上的位置（ms，-1 = 用原时间线）。
         * 每次攻击把它重置为 {@code 本次攻击时刻 + 入场+保持时长} —— 相当于「到淡出的倒计时整个重来」。
         * 入场仍按出生时间求值，所以不会重播入场动画。
         */
        float fadeStartOffsetMs = -1f;
        // Drift direction (consistent with ring spawn angle)
        final float moveDirX;
        final float moveDirY;
        final float moveSpeed;
        // Same angle used for both ring spawn position and drift direction
        final float spawnAngle;
        // Random ring radius: distance from the crosshair center where this indicator spawns
        final float ringRadius;

        Indicator(Entity victim, double x, double y, double z, float damage, boolean isCrit, boolean isKill, boolean isHeal) {
            this.victimId = victim != null ? victim.getId() : -1;
            this.victimRef = victim;
            this.x = x;
            this.y = y;
            this.z = z;
            this.damage = damage;
            this.isCrit = isCrit;
            this.isKill = isKill;
            this.isHeal = isHeal;
            this.spawnTime = System.currentTimeMillis();
            // Random angle for both ring spawn and drift direction
            this.spawnAngle = RANDOM.nextFloat() * (float) (Math.PI * 2);
            this.moveDirX = (float) Math.cos(spawnAngle);
            this.moveDirY = (float) Math.sin(spawnAngle);
            // Speed: 15-30 pixels per second
            this.moveSpeed = 15f + RANDOM.nextFloat() * 15f;
            // Random ring radius: 12-28 px from the crosshair center, no fixed inner-to-outer distance
            this.ringRadius = 12f + RANDOM.nextFloat() * 16f;
        }
    }

    public static void addIndicator(double x, double y, double z, float damage, boolean isCrit, boolean isKill) {
        addIndicator(null, x, y, z, damage, isCrit, isKill, false);
    }

    public static void addIndicator(double x, double y, double z, float damage, boolean isCrit, boolean isKill, boolean isHeal) {
        addIndicator(null, x, y, z, damage, isCrit, isKill, isHeal);
    }

    /** 带受害实体的版本：追踪实体 / 合并跳字依赖它识别「同一目标」。victim 可为 null。 */
    public static void addIndicator(Entity victim, double x, double y, double z, float damage, boolean isCrit, boolean isKill) {
        addIndicator(victim, x, y, z, damage, isCrit, isKill, false);
    }

    public static void addIndicator(Entity victim, double x, double y, double z, float damage, boolean isCrit, boolean isKill, boolean isHeal) {
        synchronized (indicators) {
            long now = System.currentTimeMillis();

            // 合并跳字：把「同一目标」短时间内连续命中的数字并成一个（默认关）。
            // 有实体 id 时按 id 精确识别；没有实体信息时退回按出生点相近（~0.35 格）判断。
            // 击杀与普通数字、治疗与伤害不会互相合并。
            if (DamageEngineConfig.getInstance().indicatorMerge) {
                int vid = victim != null ? victim.getId() : -1;
                for (Indicator ind : indicators) {
                    if (!isAlive(ind, now)) continue;              // 已过期的不参与合并
                    if (ind.isKill != isKill) continue;            // 击杀与普通数字分开
                    if (!isKill && ind.isHeal != isHeal) continue; // 治疗与伤害分开
                    if (!sameTarget(ind, vid, victim, x, y, z)) continue;
                    // 已经进入淡出阶段的：不再合并，让它自然淡出，本次伤害另起一个新的
                    if (hasStartedFade(ind, now)) continue;
                    ind.damage += damage;
                    if (isCrit) ind.isCrit = true;
                    ind.mergePopStart = now;                       // 合并更新 → 触发跳动动画
                    // 每次攻击把「到淡出」的倒计时整个重置：淡出会在「本次攻击 + 入场+保持时长」才开始
                    ind.fadeStartOffsetMs = (now - ind.spawnTime) + naturalFadeStartMs();
                    return;
                }
            }
            indicators.add(new Indicator(victim, x, y, z, damage, isCrit, isKill, isHeal));
        }
    }

    public static void tickAndCleanup() {
        long now = System.currentTimeMillis();
        if (now - lastCleanupTime < 500) return;
        lastCleanupTime = now;

        synchronized (indicators) {
            // 淡出被推迟多少，寿命就延长多少：持续合并的跳字不会被提前移除
            indicators.removeIf(ind -> !isAlive(ind, now));
        }
    }

    public static void render(GuiGraphicsExtractor guiGraphics, float tickDelta) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) return;
        if (client.gui.screen() instanceof damage.engine.client.gui.DamageConfigScreen) return;
        if (client.gui.screen() instanceof damage.engine.client.gui.HudEditorScreen) return;

        DamageEngineConfig config = DamageEngineConfig.getInstance();
        if (!config.showDamage) return;
        if (config.hideOnF1 && client.gui.hud.isHidden()) return;
        if (!config.showDamageIndicator) return;

        synchronized (indicators) {
            if (indicators.isEmpty()) return;

            Camera camera = client.gameRenderer.mainCamera();
            Vec3 camPos = camera.position();

            // 26.1: projection matrices are managed by the render pipeline.
            // Build view-projection = projection * viewRotation * T(-cameraPos)
            Matrix4f viewProjMatrix = new Matrix4f()
                .translate((float) -camPos.x, (float) -camPos.y, (float) -camPos.z);
            viewProjMatrix = camera.getViewRotationProjectionMatrix(new Matrix4f()).mul(viewProjMatrix, new Matrix4f());

            int screenW = client.getWindow().getGuiScaledWidth();
            int screenH = client.getWindow().getGuiScaledHeight();
            long now = System.currentTimeMillis();
            Font font = client.font;

            boolean isEnhanced = "enhanced".equals(config.indicatorMode);
            float baseScale = config.indicatorScale;
            float opacity = config.indicatorOpacity / 100f;

            List<RenderedIndicator> toRender = new ArrayList<>();

            for (Indicator ind : indicators) {
                if (!isAlive(ind, now)) continue; // 淡出被合并推迟时，寿命随之顺延
                float age = (now - ind.spawnTime) / 1000f;
                if (age < 0) continue;

                // 追踪实体（默认关）：跳字跟随目标移动。首帧记录「命中点相对实体脚底中心」的
                // 偏移，之后每帧按目标的插值位置重算世界坐标；实体消失后停在最后位置。
                if (config.indicatorTrackEntity && ind.victimId >= 0) {
                    Entity target = client.level != null ? client.level.getEntity(ind.victimId) : null;
                    if (target != null) {
                        ind.victimRef = target;
                        if (ind.trackOff == null) {
                            Vec3 base = target.getPosition(0f);
                            double ddx = ind.x - base.x, ddy = ind.y - base.y, ddz = ind.z - base.z;
                            if (ddx * ddx + ddy * ddy + ddz * ddz > 6.25) {
                                // 出生点离实体太远（>2.5 格），回退到实体附近的上方
                                ddx = 0; ddy = target.getEyeHeight() * 0.6; ddz = 0;
                            }
                            ind.trackOff = new double[]{ddx, ddy, ddz};
                        }
                        Vec3 pos = target.getPosition(tickDelta);
                        ind.x = pos.x + ind.trackOff[0];
                        ind.y = pos.y + ind.trackOff[1];
                        ind.z = pos.z + ind.trackOff[2];
                    }
                }

                // 世界坐标投影到屏幕:跳字锚定在准心命中点(世界 pos),而非实体中心
                Vector4f worldPos = new Vector4f((float) ind.x, (float) ind.y, (float) ind.z, 1.0f);
                worldPos.mul(viewProjMatrix);
                if (worldPos.w() <= 0.001f) continue; // 命中点在相机后则不显示

                // NDC -> 屏幕坐标;不过度剔除屏幕外位置(clamp 保底)
                float ndcX = worldPos.x() / worldPos.w();
                float ndcY = worldPos.y() / worldPos.w();
                ndcX = Mth.clamp(ndcX, -2.0f, 2.0f);
                ndcY = Mth.clamp(ndcY, -2.0f, 2.0f);
                float anchorX = (ndcX * 0.5f + 0.5f) * screenW;
                float anchorY = (1.0f - (ndcY * 0.5f + 0.5f)) * screenH;

                // Distance factor affects drift distance / text scale
                float distW = Math.abs(worldPos.w());
                float distFactor = Mth.clamp(5f / distW, 0.3f, 1.0f); // 0.3-1.0, small = far

                // Spawn around the crosshair hit point (anchored in world space),
                // each indicator at its own random ring radius.
                // Kill 标记固定显示在命中点上方,避免与随机散布的伤害数字重叠。
                float screenX;
                float screenY;
                if (ind.isKill) {
                    screenX = anchorX;
                    screenY = anchorY - 20;
                } else {
                    screenX = anchorX + ind.moveDirX * ind.ringRadius;
                    screenY = anchorY + ind.moveDirY * ind.ringRadius;
                }

                // Animation phases:入场(缩小+淡入)按出生时间算；保持段延长到淡出起点；
                // 淡出从淡出起点开始（合并会把它推迟），时长仍为 FADE_OUT_DURATION。
                float fadeStart = fadeStartSeconds(ind);
                float scale;
                float alpha;

                if (age < SHRINK_DURATION) {
                    // Phase 1: fade in + shrink from big to small
                    float t = age / SHRINK_DURATION;
                    // Fade in
                    alpha = Mth.lerp(t, 0.0f, 1.0f);
                    // Shrink from START_SCALE to END_SCALE
                    scale = Mth.lerp(t, START_SCALE, END_SCALE);
                } else if (age < fadeStart) {
                    // Phase 2: hold at small size (extended by merges)
                    scale = END_SCALE;
                    alpha = 1.0f;
                } else {
                    // Phase 3: fade out
                    float t = (age - fadeStart) / FADE_OUT_DURATION;
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

                float finalScale = scale * baseScale;
                // Slight size variation based on damage amount
                float damageScaleFactor = 1.0f + Mth.clamp(ind.damage / 100f, 0f, 0.3f);
                finalScale *= damageScaleFactor;
                // Subtle distance-based scale: far entities slightly smaller (10% reduction at max distance)
                float distanceScaleFactor = 0.9f + distFactor * 0.1f;
                finalScale *= distanceScaleFactor;

                // 合并跳字时的跳动：汇总数字更新瞬间撑大再回落（1.18 → 1.0，360ms 缓出）。
                // 每次合并都会重置时钟，连续命中时不断跳动。
                if (ind.mergePopStart >= 0) {
                    float popMs = now - ind.mergePopStart;
                    if (popMs < MERGE_POP_MS) {
                        float pt = popMs / MERGE_POP_MS;
                        float eased = 1f - (1f - pt) * (1f - pt);
                        finalScale *= Mth.lerp(eased, MERGE_POP_SCALE, 1.0f);
                    } else {
                        ind.mergePopStart = -1; // 动画结束，停用
                    }
                }

                toRender.add(new RenderedIndicator(text, finalX, finalY, finalScale, color, ind.isKill));
            }

            // Render all indicators
            for (RenderedIndicator ri : toRender) {
                guiGraphics.pose().pushMatrix();
                guiGraphics.pose().translate(ri.x, ri.y);
                guiGraphics.pose().scale(ri.scale, ri.scale);

                int textY = -(font.lineHeight / 2);

                if (config.indicatorBold) {
                    // Use Minecraft's built-in bold formatting
                    var boldText = Component.literal(ri.text).withStyle(ChatFormatting.BOLD).getVisualOrderText();
                    int textWidth = font.width(boldText);
                    guiGraphics.text(font, boldText, -textWidth / 2, textY, ri.color);
                } else {
                    int textWidth = font.width(ri.text);
                    guiGraphics.text(font, ri.text, -textWidth / 2, textY, ri.color);
                }

                guiGraphics.pose().popMatrix();
            }
        }
    }

    private static String formatDamage(float damage, int decimalPlaces) {
        return DamageNumberFormat.formatDamage(damage, decimalPlaces);
    }

    private record RenderedIndicator(String text, float x, float y, float scale, int color, boolean isKill) {}

    public static void clearAll() {
        synchronized (indicators) {
            indicators.clear();
        }
    }
}