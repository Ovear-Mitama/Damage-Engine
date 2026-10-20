package damage.engine.hud.indicator;

import damage.engine.DamageEngineConfig;
import damage.engine.hud.format.DamageNumberFormat;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Camera;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
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

    /** 淡出起点(秒):入场(收缩)与保持结束、开始淡出的默认时刻。 */
    private static final float FADE_START = SHRINK_DURATION + HOLD_DURATION;
    /** 淡出起点(毫秒),合并跳字时用它重置「到出场」的倒计时。 */
    private static final float FADE_START_MS = FADE_START * 1000f;
    /** 合并跳字时的一次性跳动:1.18 → 1.0、360ms、缓出 easeOutQuad。 */
    private static final float MERGE_POP_MS = 360f;
    /** 无实体信息时,合并判定的出生点距离上限(格)与其平方。 */
    private static final double MERGE_DIST = 0.35;
    private static final double MERGE_DIST_SQ = MERGE_DIST * MERGE_DIST;

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
        /** 受害实体 id(-1 = 无/不可知;追踪实体与合并跳字依赖它)。 */
        final int victimId;
        /** 缓存的受害实体引用(渲染时刷新,实体移除后为 null)。 */
        Entity victimRef;
        /** 世界坐标:命中点(追踪实体时每帧按实体插值位置重算)。 */
        double x, y, z;
        float damage;
        boolean isCrit;
        final boolean isKill;
        final boolean isHeal;
        final long spawnTime;
        /** 追踪首帧算出的「实体脚下中心 → 出生点」相对偏移;null = 尚未初始化。 */
        double[] trackOff;
        /** 最近一次合并跳字的时刻(ms,-1 = 未发生过/动画已结束),用于合并时的跳动缩放。 */
        long mergePopStart = -1;
        /**
         * 淡出起点在「本体年龄」时间轴上的位置(ms,-1 = 用默认起点)。
         * 每次命中把它重置为「本次命中时刻 + 入场与保持时长」,淡出随之顺延;入场等其它阶段
         * 仍按本体年龄求值,所以不会重播入场动画。
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

        // 跳字帧率上限：同一步进内复用动画计算结果（文字仍然每帧绘制）
        int animStep = Integer.MIN_VALUE;
        float cachedScale;
        float cachedAlpha;
        float cachedMoveOffsetX;
        float cachedMoveOffsetY;
        float cachedMergePop = 1f;
        // 渲染文本缓存：伤害值或相关配置变化时才重新格式化
        private String cachedText;
        private float cachedDamage = Float.NaN;
        private int cachedDecimalPlaces = Integer.MIN_VALUE;
        private boolean cachedPrefixSign;
        private String cachedKillText = "";

        String text(DamageEngineConfig config) {
            String killText = config.killText == null ? "" : config.killText;
            if (cachedText == null
                || cachedDamage != damage
                || cachedDecimalPlaces != config.indicatorDecimalPlaces
                || cachedPrefixSign != config.indicatorPrefixSign
                || (isKill && !killText.equals(cachedKillText))) {
                if (isKill) {
                    cachedText = killText;
                } else if (isHeal) {
                    cachedText = (config.indicatorPrefixSign ? "+" : "") + formatDamage(damage, config.indicatorDecimalPlaces);
                } else {
                    cachedText = (config.indicatorPrefixSign ? "-" : "") + formatDamage(damage, config.indicatorDecimalPlaces);
                }
                cachedDamage = damage;
                cachedDecimalPlaces = config.indicatorDecimalPlaces;
                cachedPrefixSign = config.indicatorPrefixSign;
                cachedKillText = isKill ? killText : "";
            }
            return cachedText;
        }

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

    /** 旧方法(无实体信息),保留;追踪实体 / 合并跳字不可用。victim 传 null。 */
    public static void addIndicator(double x, double y, double z, float damage, boolean isCrit, boolean isKill) {
        addIndicator(null, x, y, z, damage, isCrit, isKill, false);
    }

    public static void addIndicator(double x, double y, double z, float damage, boolean isCrit, boolean isKill, boolean isHeal) {
        addIndicator(null, x, y, z, damage, isCrit, isKill, isHeal);
    }

    /** 带受害实体的版本:追踪实体 / 合并跳字依赖它识别「同一目标」。victim 可为 null。 */
    public static void addIndicator(Entity victim, double x, double y, double z, float damage, boolean isCrit, boolean isKill) {
        addIndicator(victim, x, y, z, damage, isCrit, isKill, false);
    }

    public static void addIndicator(Entity victim, double x, double y, double z, float damage, boolean isCrit, boolean isKill, boolean isHeal) {
        synchronized (indicators) {
            long now = System.currentTimeMillis();

            // 合并跳字:把「同一目标」短时间内连续命中的数字并成一个(默认关)。
            // 建议配合追踪实体使用——有实体 id 时按 id 精确识别;没有实体信息时退回按
            // 出生点相近(~0.35 格)判断。击杀与普通数字、治疗与伤害不会互相合并。
            if (DamageEngineConfig.getInstance().indicatorMerge) {
                int vid = victim != null ? victim.getId() : -1;
                for (Indicator ind : indicators) {
                    if (!isAlive(ind, now)) continue;           // 已过期的不参与合并
                    if (ind.isKill != isKill) continue;         // 击杀与普通数字分开
                    if (!isKill && ind.isHeal != isHeal) continue; // 治疗与伤害分开
                    if (!sameTarget(ind, vid, victim, x, y, z)) continue;
                    // 已经进入淡出的:不再合并(让它自然淡出),本次伤害另起一个新跳字
                    if (hasStartedExit(ind, now)) continue;
                    ind.damage += damage;
                    ind.isCrit |= isCrit;
                    ind.mergePopStart = now;                    // 合并更新 → 触发跳动动画
                    // 每次命中把「到淡出」的倒计时整个重置:淡出会在「本次命中 + 入场与保持时长」才开始。
                    ind.fadeStartOffsetMs = (now - ind.spawnTime) + FADE_START_MS;
                    return;
                }
            }
            // No performance cap on indicator count
            indicators.add(new Indicator(victim, x, y, z, damage, isCrit, isKill, isHeal));
            trimToLimit();
        }
    }

    /** 跳字数量上限：0 = 无限制；超过上限时移除最旧的跳字。 */
    private static void trimToLimit() {
        int maxCount = DamageEngineConfig.getInstance().indicatorMaxCount;
        if (maxCount <= 0) return;
        while (indicators.size() > maxCount) {
            indicators.remove(0);
        }
    }

    /** 该跳字是否已经进入淡出(淡出已开始,就不该再被合并)。 */
    private static boolean hasStartedExit(Indicator ind, long now) {
        float startAt = ind.fadeStartOffsetMs >= 0f ? ind.fadeStartOffsetMs : FADE_START_MS;
        return (now - ind.spawnTime) >= startAt;
    }

    /** 是否仍在生命周期内:淡出可能被每次命中往后推,寿命随之顺延。 */
    private static boolean isAlive(Indicator ind, long now) {
        float lifeMs = TOTAL_DURATION * 1000f;
        if (ind.fadeStartOffsetMs >= 0f) {
            lifeMs += ind.fadeStartOffsetMs - FADE_START_MS; // 淡出被推迟多少,寿命就延长多少
        }
        return now - ind.spawnTime <= lifeMs;
    }

    /** 两个跳字是否属于同一目标:优先实体 id,无实体信息时按出生点相近。 */
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

    public static void tickAndCleanup() {
        long now = System.currentTimeMillis();
        if (now - lastCleanupTime < 500) return;
        lastCleanupTime = now;

        synchronized (indicators) {
            // 淡出被推迟多少寿命就延长多少,持续合并的跳字不会被提前移除
            indicators.removeIf(ind -> !isAlive(ind, now));
            // 配置里调小数量上限后，即使没有新命中也要把超出的旧跳字清掉
            trimToLimit();
        }
    }

    // ---- 性能叠加层：跳字渲染帧率统计 ----
    private static long fpsWindowStartMs = 0L;
    private static int fpsFrames = 0;
    private static int currentFps = 0;

    private static void measureFps() {
        long now = System.currentTimeMillis();
        if (fpsWindowStartMs == 0L) {
            fpsWindowStartMs = now;
            return;
        }
        fpsFrames++;
        long elapsed = now - fpsWindowStartMs;
        if (elapsed >= 1000L) {
            currentFps = (int) Math.round(fpsFrames * 1000.0 / elapsed);
            fpsFrames = 0;
            fpsWindowStartMs = now;
        }
    }

    /** 跳字渲染回调的实际帧率（供性能叠加层显示）。 */
    public static int currentFps() {
        return currentFps;
    }

    /** 当前存活的跳字数量。 */
    public static int activeCount() {
        synchronized (indicators) {
            return indicators.size();
        }
    }

    public static void render(GuiGraphics guiGraphics, float tickDelta) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) return;
        if (client.screen instanceof damage.engine.client.gui.DamageConfigScreen) return;
        if (client.screen instanceof damage.engine.client.gui.HudEditorScreen) return;
        measureFps();

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
            boolean reduceAnimations = config.reduceAnimations;
            float baseScale = config.indicatorScale;
            float opacity = config.indicatorOpacity / 100f;
            // 跳字独立帧率上限：0 = 跟随游戏帧率；否则动画只在步进边界推进
            int maxFps = Mth.clamp(config.indicatorMaxFps, 0, 480);
            float stepMs = maxFps > 0 ? 1000f / maxFps : 0f;
            // 相机朝向：用于剔除位于相机背后的跳字
            Vector3f camLook = camera.getLookVector();

            List<RenderedIndicator> toRender = new ArrayList<>();

            for (Indicator ind : indicators) {
                long ageMs = now - ind.spawnTime;
                if (ageMs < 0) continue;
                // 淡出可能被合并时的命中往后推,寿命随之顺延;不再用固定的 TOTAL_DURATION 截断
                if (!isAlive(ind, now)) continue;
                // 帧率上限：把动画时间量化到步进边界，同一段内复用缓存结果
                int animStep = -1;
                float animMs = ageMs;
                if (stepMs > 0f) {
                    animStep = (int) (ageMs / stepMs);
                    animMs = animStep * stepMs;
                }
                float age = animMs / 1000f;
                // 淡出起点(秒):默认 = 入场 + 保持结束;合并过则用被重置的起点
                float fadeStartSec = (ind.fadeStartOffsetMs >= 0f ? ind.fadeStartOffsetMs : FADE_START_MS) / 1000f;

                // 追踪模式：
                // - 实体：跳字跟随目标移动。首帧记录「命中点相对实体脚底中心」的偏移，
                //   之后每帧按目标的插值位置重算世界坐标；实体消失后停在最后位置。
                // - 准心：跳过世界投影，跳字直接锚在屏幕准心（画面中心）。
                boolean trackEntity = "entity".equals(config.indicatorTrackMode);
                boolean trackCrosshair = "crosshair".equals(config.indicatorTrackMode);
                if (trackEntity && ind.victimId >= 0 && client.level != null) {
                    Entity target = client.level.getEntity(ind.victimId);
                    if (target != null) {
                        ind.victimRef = target;
                        if (ind.trackOff == null) {
                            Vec3 base = target.getPosition(0f);
                            double ddx = ind.x - base.x, ddy = ind.y - base.y, ddz = ind.z - base.z;
                            if (ddx * ddx + ddy * ddy + ddz * ddz > 6.25) {
                                // 出生点离实体太远(过期坐标),回退到实体附近的上方
                                ddx = 0; ddy = target.getEyeHeight() * 0.6; ddz = 0;
                            }
                            ind.trackOff = new double[]{ddx, ddy, ddz};
                        }
                        Vec3 tpos = target.getPosition(tickDelta);
                        ind.x = tpos.x + ind.trackOff[0];
                        ind.y = tpos.y + ind.trackOff[1];
                        ind.z = tpos.z + ind.trackOff[2];
                    }
                }

                // 相机背后的跳字直接跳过：不投影、不构建文本，省下无效绘制
                if (!trackCrosshair) {
                    double toX = ind.x - camPos.x;
                    double toY = ind.y - camPos.y;
                    double toZ = ind.z - camPos.z;
                    if (toX * camLook.x + toY * camLook.y + toZ * camLook.z <= 0.0) {
                        continue;
                    }
                }

                float entityScreenX;
                float entityScreenY;
                float distFactor;
                if (trackCrosshair) {
                    // 准心模式：直接使用屏幕中心
                    entityScreenX = screenW / 2f;
                    entityScreenY = screenH / 2f;
                    distFactor = 1.0f;
                } else {
                    // The float is pinned to the world position where the hit happened
                    // (server snapshot); with entity tracking on it is recomputed above.
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
                    entityScreenX = (ndcX * 0.5f + 0.5f) * screenW;
                    entityScreenY = (1.0f - (ndcY * 0.5f + 0.5f)) * screenH;

                    // Distance factor: far entities get smaller drift/ring
                    float distW = Math.abs(worldPos.w());
                    distFactor = Mth.clamp(5f / distW, 0.3f, 1.0f); // 0.3-1.0, small = far
                }

                // Spawn around the crosshair hit point (anchored in world space),
                // each indicator at its own random ring radius.
                // Kill 标记固定显示在命中点上方,避免与随机散布的伤害数字重叠。
                float screenX;
                float screenY;
                if (ind.isKill) {
                    screenX = entityScreenX;
                    screenY = entityScreenY - 20;
                } else {
                    screenX = entityScreenX + ind.moveDirX * ind.ringRadius;
                    screenY = entityScreenY + ind.moveDirY * ind.ringRadius;
                }

                // Animation phases
                float scale;
                float alpha;
                float moveOffsetX;
                float moveOffsetY;
                float mergePop;
                if (stepMs > 0f && ind.animStep == animStep) {
                    // 同一步进内直接复用上次算好的结果
                    scale = ind.cachedScale;
                    alpha = ind.cachedAlpha;
                    moveOffsetX = ind.cachedMoveOffsetX;
                    moveOffsetY = ind.cachedMoveOffsetY;
                    mergePop = ind.cachedMergePop;
                } else {
                    if (age < SHRINK_DURATION) {
                        // Phase 1: fade in + shrink from big to small
                        float t = age / SHRINK_DURATION;
                        // Fade in
                        alpha = Mth.lerp(t, 0.0f, 1.0f);
                        // Shrink from START_SCALE to END_SCALE
                        scale = Mth.lerp(t, START_SCALE, END_SCALE);
                    } else if (age < fadeStartSec) {
                        // Phase 2: hold at small size (保持到被重置的淡出起点)
                        scale = END_SCALE;
                        alpha = 1.0f;
                    } else {
                        // Phase 3: fade out (淡出推迟到 fadeStartSec 之后,时长不变)
                        float t = (age - fadeStartSec) / FADE_OUT_DURATION;
                        scale = END_SCALE;
                        alpha = Mth.lerp(t, 1.0f, 0.0f);
                    }

                    alpha *= opacity;

                    // Movement for enhanced mode - drift away, scaled by distance
                    moveOffsetX = 0f;
                    moveOffsetY = 0f;
                    if (isEnhanced && !ind.isKill && !reduceAnimations) {
                        float moveProgress = Math.min(age / (SHRINK_DURATION + HOLD_DURATION), 1.0f);
                        float easedT = 1.0f - (1.0f - moveProgress) * (1.0f - moveProgress);
                        float distance = ind.moveSpeed * easedT * distFactor;
                        moveOffsetX = ind.moveDirX * distance;
                        moveOffsetY = ind.moveDirY * distance;
                    }

                    // 合并跳字时的跳动:合并瞬间轻微放大再回落(1.18 → 1.0,360ms,easeOutQuad)。
                    // 每次合并都会重置时钟,连续命中时不断跳动;动画结束后停用。
                    mergePop = 1f;
                    if (ind.mergePopStart >= 0) {
                        float popMs = Math.max(0f, animMs - ind.mergePopStart);
                        if (popMs < MERGE_POP_MS && !reduceAnimations) {
                            float pt = popMs / MERGE_POP_MS;
                            float pe = 1f - (1f - pt) * (1f - pt);
                            mergePop = 1.18f - 0.18f * pe;
                        } else if (popMs >= MERGE_POP_MS) {
                            ind.mergePopStart = -1;
                        }
                    }

                    if (stepMs > 0f) {
                        ind.animStep = animStep;
                        ind.cachedScale = scale;
                        ind.cachedAlpha = alpha;
                        ind.cachedMoveOffsetX = moveOffsetX;
                        ind.cachedMoveOffsetY = moveOffsetY;
                        ind.cachedMergePop = mergePop;
                    }
                }

                // Kill text uses special handling
                String text = ind.text(config);
                int argbColor;
                if (ind.isKill) {
                    argbColor = config.killTextColor;
                } else if (ind.isHeal) {
                    argbColor = config.healIndicatorColor;
                } else if (ind.isCrit) {
                    argbColor = config.damageIndicatorCritColor;
                } else {
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
                finalScale *= mergePop;

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
        return DamageNumberFormat.formatDamage(damage, decimalPlaces);
    }

    private record RenderedIndicator(String text, float x, float y, float scale, int color, boolean isKill) {}

    public static void clearAll() {
        synchronized (indicators) {
            indicators.clear();
        }
    }
}
