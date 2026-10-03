package damage.engine.hud;

import anima.api.AnimaApi;
import anima.api.Easing;
import anima.client.world.ClipParticles;
import anima.client.world.TextSpread;
import anima.client.world.WorldText3D;
import anima.effects.ScaleEffect;
import anima.engine.RenderModifier;
import anima.text.CharClips;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import damage.engine.DamageEngineConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class DamageIndicator {
    private static final List<Indicator> indicators = new ArrayList<>();
    private static final Random RANDOM = new Random();

    /** 增强模式下漂移完成所需的时间（秒）——只影响跳字在屏幕上的移动节奏。 */
    private static final float DRIFT_DURATION = 2.3f;

    /** 一个剪辑都没有时的兜底显示时长（秒），免得跳字一闪而过。 */
    private static final float FALLBACK_LIFETIME = 1.5f;

    /**
     * 合并跳字时的一次性「跳动」动画：汇总数字微撑大再回落（缩放走库的 {@link ScaleEffect} 求值）。
     * from>to（1.18 → 1.0）+ easeOutQuad：t=0 时放大 1.18 倍，360ms 内平滑回到原大小。
     */
    private static final float MERGE_POP_MS = 360f;
    private static final anima.text.TextAnimationSpec MERGE_POP_SPEC = new anima.text.TextAnimationSpec(
        java.util.List.of(new ScaleEffect(1.18f, 1f, MERGE_POP_MS, Easing.EASE_OUT_QUAD, false)));

    /** 默认剪辑的出场起点（ms）；实际值按当前剪辑动态推算，见 {@link #exitStartMs()}。 */
    private static final float DEFAULT_EXIT_START_MS = 3300f;

    /** Kill 标记固定显示在命中点上方的像素距离。 */
    private static final float KILL_TEXT_OFFSET = 20f;

    /**
     * 跳字默认的逐字剪辑（定义写在 DE 这边，逐字求值由 Anima 库负责）：飘入入场 + 打字机出场。
     * 玩家可以在 配置界面 → 伤害跳字 → 动画编辑器 里调整，结果保存在配置里。
     */
    private static final String DEFAULT_CHAR_CLIPS =
        "[{\"effect\":\"char_drift_in\",\"start\":0,\"duration\":600},"
            + "{\"effect\":\"typewriter_out\",\"start\":3300,\"duration\":1000}]";

    private static String clipsCacheKey = null;
    private static List<CharClips.Clip> clipsCache = List.of();
    /** 从当前剪辑推算的出场起点（ms）：取「最晚开始的那条剪辑」。 */
    private static float clipsExitStartMs = DEFAULT_EXIT_START_MS;

    /**
     * 出场起点（ms）：取「最晚开始的那条剪辑」当作出场动画（默认剪辑里就是 typewriter_out 的 3300）。
     * 玩家自定义剪辑后依然适用，不用写死时间。
     */
    private static float exitStartMs() {
        charClips(); // 确保缓存已按当前配置刷新
        return clipsExitStartMs;
    }

    /** 当前生效的逐字剪辑 JSON（配置为空时用默认值）——动画编辑器用它载入初始剪辑。 */
    public static JsonArray charClipsJson() {
        try {
            return JsonParser.parseString(rawCharClips()).getAsJsonArray();
        } catch (Exception e) {
            // 配置里写坏了就退回默认，保证编辑器还能打开
            return JsonParser.parseString(DEFAULT_CHAR_CLIPS).getAsJsonArray();
        }
    }

    private static String rawCharClips() {
        String configured = DamageEngineConfig.getInstance().indicatorCharClips;
        return configured == null || configured.isBlank() ? DEFAULT_CHAR_CLIPS : configured;
    }

    /** 逐字剪辑（按配置内容缓存，避免每帧解析 JSON）。 */
    private static List<CharClips.Clip> charClips() {
        String raw = rawCharClips();
        if (!raw.equals(clipsCacheKey)) {
            List<CharClips.Clip> parsed = new ArrayList<>();
            try {
                JsonArray arr = JsonParser.parseString(raw).getAsJsonArray();
                for (JsonElement el : arr) {
                    if (!el.isJsonObject()) continue;
                    var o = el.getAsJsonObject();
                    if (!o.has("effect")) continue;
                    parsed.add(new CharClips.Clip(o.get("effect").getAsString(),
                        o.has("start") ? o.get("start").getAsFloat() : 0f,
                        o.has("duration") ? o.get("duration").getAsFloat() : 1000f,
                        o.has("speed") ? o.get("speed").getAsFloat() : 1f));
                }
            } catch (Exception ignored) {
                // 解析失败 = 没有逐字动画，整段绘制
            }
            clipsCacheKey = raw;
            clipsCache = parsed;
            // 出场起点 = 最晚开始的那条剪辑（默认剪辑里就是 typewriter_out）
            float exitStart = -1f;
            for (CharClips.Clip c : parsed) {
                if (c.startMs() > exitStart) exitStart = c.startMs();
            }
            clipsExitStartMs = exitStart > 0f ? exitStart : DEFAULT_EXIT_START_MS;
        }
        return clipsCache;
    }

    /** 文本对象属性（编辑器里调的位置 / 缩放 / 透明度 / 时长 / 距离缩放）；配置为空或写坏返回 null。 */
    public static JsonObject textPropsJson() {
        String configured = DamageEngineConfig.getInstance().indicatorTextProps;
        if (configured == null) {
            configured = "";
        }
        if (!configured.equals(textPropsCacheKey)) {
            textPropsCacheKey = configured;
            textPropsCache = null;
            if (!configured.isBlank()) {
                try {
                    JsonElement el = JsonParser.parseString(configured);
                    if (el.isJsonObject()) {
                        textPropsCache = el.getAsJsonObject();
                    }
                } catch (Exception ignored) {
                    // 写坏了就当没有文本属性，用默认
                }
            }
        }
        return textPropsCache;
    }

    /** 编辑器预览里显示的示例数字（DE 自己的样例，不用库的示例文本）。 */
    public static final String PREVIEW_TEXT = "123,456.789";

    private static String particleClipsCacheKey = null;
    private static ClipParticles particleClipsCache = null;

    /**
     * 配置里的粒子剪辑（与逐字剪辑是同一份 JSON，编辑器里拖进来的粒子就在里面）。
     * 返回可复用的模板：每次跳字用它建一个播放器，按剪辑时间发射真正的 3D 粒子 ——
     * 否则粒子只会在编辑器预览里出现，实际打怪时什么都看不到。
     */
    private static ClipParticles particleClips() {
        String raw = rawCharClips();
        if (particleClipsCache == null || !raw.equals(particleClipsCacheKey)) {
            particleClipsCacheKey = raw;
            particleClipsCache = AnimaApi.particleClips(charClipsJson());
        }
        return particleClipsCache;
    }

    private static String textPropsCacheKey = null;
    private static JsonObject textPropsCache = null;

    /** 跳字存活时长（秒）：由逐字剪辑与文本时长自己决定（取两者较晚的结束时间）。 */
    private static float lifetimeSeconds() {
        float endMs = 0f;
        for (CharClips.Clip c : charClips()) {
            endMs = Math.max(endMs, c.startMs() + c.durationMs());
        }
        JsonObject props = textPropsJson();
        if (props != null && props.has("durationMs")) {
            endMs = Math.max(endMs, props.get("durationMs").getAsFloat());
        }
        return endMs > 0f ? endMs / 1000f : FALLBACK_LIFETIME;
    }

    private static long lastCleanupTime = 0;
    /** 临时诊断用（定位"跳字不显示"）——排查完可以删。 */
    private static long lastDebugLog = 0;

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
         * 出场起点在「本体年龄」时间轴上的位置（ms，-1 = 用剪辑自身的起点）。
         * <p>
         * 每次攻击把它重置为 {@code 本次攻击时刻 + 出场起点} —— 相当于「出场倒计时整个重来」。
         * 入场等其它剪辑仍按本体年龄求值，所以<b>不会重播入场动画</b>（入场时长由玩家自定义）。
         */
        float exitOffsetMs = -1f;
        /** 按 {@link #exitOffsetMs} 平移过出场剪辑的剪辑表（合并时置空，渲染时懒重建）。 */
        List<CharClips.Clip> adjustedClips;
        /** 这个跳字自己的粒子播放器（按配置里的粒子剪辑发射真实 3D 粒子），实例间互不影响。 */
        final ClipParticles.Player particles = particleClips().newPlayer();
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

        /**
         * 渲染用剪辑表：没被重置过就是原始剪辑；被重置过则把<b>出场剪辑</b>的起点平移到
         * {@link #exitOffsetMs}，其余剪辑（入场等）保持自身起点 —— 这样入场只按本体年龄播一次，
         * 出场则等重置后的倒计时走完才开始。
         */
        List<CharClips.Clip> renderClips() {
            List<CharClips.Clip> base = charClips();
            if (exitOffsetMs < 0f) return base;
            if (adjustedClips == null) {
                float natural = exitStartMs();
                List<CharClips.Clip> out = new ArrayList<>(base.size());
                for (CharClips.Clip c : base) {
                    if (c.startMs() >= natural - 0.01f) {
                        out.add(new CharClips.Clip(c.effect(), exitOffsetMs, c.durationMs(), c.speed()));
                    } else {
                        out.add(c);
                    }
                }
                adjustedClips = out;
            }
            return adjustedClips;
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
            // 建议配合追踪实体使用——有实体 id 时按 id 精确识别；没有实体信息时退回按
            // 出生点相近（~0.35 格）判断。击杀与普通数字、治疗与伤害不会互相合并。
            if (DamageEngineConfig.getInstance().indicatorMerge) {
                int vid = victim != null ? victim.getId() : -1;
                for (Indicator ind : indicators) {
                    if (!isAlive(ind, now)) continue;           // 已过期的不参与合并
                    if (ind.isKill != isKill) continue;         // 击杀与普通数字分开
                    if (!isKill && ind.isHeal != isHeal) continue; // 治疗与伤害分开
                    if (!sameTarget(ind, vid, victim, x, y, z)) continue;
                    // 已经进入出场动画的：不再合并（也就不存在"冻结"），让它自然淡出，本次伤害另起一个
                    if (hasStartedExit(ind, now)) continue;
                    ind.damage += damage;
                    ind.isCrit |= isCrit;
                    ind.mergePopStart = now;                 // 合并更新 → 触发跳动动画
                    // 每次攻击把「到出场」的倒计时整个重置：出场会在「本次攻击 + 出场起点」才开始。
                    // 只平移出场剪辑，入场等其它剪辑不动 → 不会重播入场动画（入场由玩家自定义）。
                    ind.exitOffsetMs = (now - ind.spawnTime) + exitStartMs();
                    ind.adjustedClips = null;
                    return;
                }
            }
            indicators.add(new Indicator(victim, x, y, z, damage, isCrit, isKill, isHeal));
        }
    }

    /** 该跳字是否已经进入出场动画（出场已开始，就不该再被合并）。 */
    private static boolean hasStartedExit(Indicator ind, long now) {
        float startAt = ind.exitOffsetMs >= 0f ? ind.exitOffsetMs : exitStartMs();
        return (now - ind.spawnTime) >= startAt;
    }

    /** 是否仍在生命周期内：出场可能被每次攻击往后推，寿命随之顺延。 */
    private static boolean isAlive(Indicator ind, long now) {
        float lifeMs = lifetimeSeconds() * 1000f;
        if (ind.exitOffsetMs >= 0f) {
            lifeMs += ind.exitOffsetMs - exitStartMs(); // 出场被推迟了多少，寿命就延长多少
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

    /** 无实体信息时，合并判定的出生点距离上限（方块）。 */
    private static final double MERGE_DIST = 0.35;
    private static final double MERGE_DIST_SQ = MERGE_DIST * MERGE_DIST;

    public static void tickAndCleanup() {
        long now = System.currentTimeMillis();
        if (now - lastCleanupTime < 500) return;
        lastCleanupTime = now;

        synchronized (indicators) {
            // 暂停时长计入寿命，持续合并的跳字不会被提前移除
            indicators.removeIf(ind -> !isAlive(ind, now));
        }
    }

    /**
     * 世界渲染阶段绘制跳字：交给 Anima 库的真 3D 世界文字（有透视、随距离缩放、会被方块遮挡）。
     * 用 {@code AnimaApi.onWorldRender} 注册。
     */
    public static void renderWorld(PoseStack pose, MultiBufferSource buffers, Camera camera, float partialTick) {
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

            // 入场 / 出场（含淡入淡出）全部由剪辑决定，DE 只提供世界坐标、像素偏移、颜色与文字；
            // 剪辑外的静止时段没有任何整体特效叠加。
            Vec3 camPos = camera.getPosition();
            long now = System.currentTimeMillis();

            boolean isEnhanced = "enhanced".equals(config.indicatorMode);
            float lifetimeSec = lifetimeSeconds();
            float durationMs = lifetimeSec * 1000f;

            // 兜底：库的 onWorldRender 回调在某些版本会固定传 0（未插值），拿不到帧间插值时
            // 自己取渲染插值，避免追踪实体时位置按 tick 步进（一卡一卡）。
            if (partialTick <= 0f) {
                partialTick = client.getTimer().getGameTimeDeltaPartialTick(true);
            }

            int drawnCount = 0;

            for (Indicator ind : indicators) {
                if (!isAlive(ind, now)) continue;

                // 本体年龄：位置 / 漂移 / 文本属性 / 粒子 / 入场动画都用它。
                // 出场被每次攻击重置时只平移出场剪辑（见 Indicator#renderClips），其余不动。
                float trueMs = now - ind.spawnTime;

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
                                // 出生点离实体太远（过期坐标），回退到实体附近的上方
                                ddx = 0; ddy = target.getEyeHeight() * 0.6; ddz = 0;
                            }
                            ind.trackOff = new double[]{ddx, ddy, ddz};
                        }
                        Vec3 pos = target.getPosition(partialTick);
                        ind.x = pos.x + ind.trackOff[0];
                        ind.y = pos.y + ind.trackOff[1];
                        ind.z = pos.z + ind.trackOff[2];
                    }
                }

                // 距离系数：远处目标的漂移更小（字号由 3D 透视自然缩放，不再补偿）
                float distFactor = Mth.clamp(5f / Math.max((float) camPos.distanceTo(new Vec3(ind.x, ind.y, ind.z)), 0.01f),
                    0.3f, 1.0f);

                // 屏幕像素偏移：随机环半径 + 增强模式漂移——DE 只提供随机值，曲线由库求值。
                // Kill 标记固定显示在命中点上方,避免与随机散布的伤害数字重叠。
                float offsetX;
                float offsetY;
                if (ind.isKill) {
                    offsetX = 0f;
                    offsetY = -KILL_TEXT_OFFSET;
                } else {
                    float drift = isEnhanced ? ind.moveSpeed * distFactor : 0f;
                    TextSpread.Offset off = AnimaApi.spreadOffset(ind.moveDirX, ind.moveDirY,
                        ind.ringRadius, drift, trueMs, DRIFT_DURATION * 1000f);
                    offsetX = off.x();
                    offsetY = off.y();
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

                Component label = config.indicatorBold
                    ? Component.literal(text).withStyle(ChatFormatting.BOLD)
                    : Component.literal(text);

                // 字号：伤害越大略大；再乘上编辑器里调的文本属性（缩放 / 透明度 / 位置偏移 / 距离缩放）
                JsonObject textProps = textPropsJson();
                float[] tp = AnimaApi.evalTextProps(textProps, trueMs);
                double tx = ind.x + tp[0];
                double ty = ind.y + tp[1];
                double tz = ind.z + tp[2];
                float scaleMul = (1.0f + Mth.clamp(ind.damage / 100f, 0f, 0.3f)) * tp[3];
                float alphaMul = tp[4];
                // 距离缩放：默认关（无属性时按关处理）；关 = 按距离补偿世界尺寸，屏幕上大小恒定
                boolean distScale = textProps != null && textProps.has("distanceScale")
                    && textProps.get("distanceScale").getAsBoolean();
                if (!distScale) {
                    // 与编辑器一致，基准 6 格
                    scaleMul *= (float) Math.max(0.05, camPos.distanceTo(new Vec3(ind.x, ind.y, ind.z)) / 6.0);
                }

                // 合并跳字时的跳动：汇总数字更新瞬间撑大再回落（缩放由库的 ScaleEffect 求值）。
                // 每次合并都会重置时钟，连续命中时不断跳动。
                if (ind.mergePopStart >= 0) {
                    float popMs = now - ind.mergePopStart;
                    if (popMs < MERGE_POP_MS) {
                        RenderModifier pop = MERGE_POP_SPEC.computeModifier(popMs, MERGE_POP_MS);
                        scaleMul *= pop.sx;
                    } else {
                        ind.mergePopStart = -1; // 动画结束，停用
                    }
                }

                // 逐字动画（默认飘入入场 + 打字机出场）：剪辑由 DE 给出，逐字求值由库完成
                WorldText3D.Glyph[] glyphs = AnimaApi.charGlyphs(text, ind.renderClips(), trueMs);

                // 粒子剪辑：编辑器里拖进来的粒子同样要按剪辑时间发射，否则只会在编辑器预览里出现
                ind.particles.emit(tx, ty, tz, trueMs);

                // 双绘（同原版名字牌）：先 see-through 一遍（不做深度测试 → 不被方块 / 实体遮挡），
                // 再常规画一遍（有遮挡）——这样即使穿透那遍在某些渲染阶段没生效，跳字也一定可见
                int color = 0xFF000000 | (argbColor & 0x00FFFFFF);
                boolean drewA = AnimaApi.drawWorldText3D(buffers, camera, label, tx, ty, tz,
                    offsetX, offsetY, color, null,
                    trueMs, durationMs, WorldText3D.DEFAULT_SCALE, scaleMul, alphaMul, glyphs, true);
                boolean drewB = AnimaApi.drawWorldText3D(buffers, camera, label, tx, ty, tz,
                    offsetX, offsetY, color, null,
                    trueMs, durationMs, WorldText3D.DEFAULT_SCALE, scaleMul, alphaMul, glyphs, false);
                if (drewA || drewB) {
                    drawnCount++;
                }
            }

            // 临时诊断（定位"跳字不显示"）：每秒最多打一行，确认回调有没有跑、有没有真画出去
            if (now - lastDebugLog > 1000) {
                lastDebugLog = now;
                damage.engine.DamageEngine.LOGGER.info("[DE跳字] renderWorld: indicators={} drawn={} buffers={}",
                    indicators.size(), drawnCount, buffers == null ? "null" : buffers.getClass().getSimpleName());
            }
        }
    }

    private static String formatDamage(float damage, int decimalPlaces) {
        return DamageNumberFormat.formatDamage(damage, decimalPlaces);
    }

    public static void clearAll() {
        synchronized (indicators) {
            indicators.clear();
        }
    }
}