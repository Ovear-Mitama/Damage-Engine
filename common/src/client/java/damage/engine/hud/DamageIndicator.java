package damage.engine.hud;

import anima.api.AnimaApi;
import anima.client.world.ClipParticles;
import anima.client.world.TextSpread;
import anima.client.world.WorldText3D;
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

    public static class Indicator {
        final double x, y, z;
        final float damage;
        final boolean isCrit;
        final boolean isKill;
        final boolean isHeal;
        final long spawnTime;
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

        Indicator(double x, double y, double z, float damage, boolean isCrit, boolean isKill, boolean isHeal) {
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
        addIndicator(x, y, z, damage, isCrit, isKill, false);
    }

    public static void addIndicator(double x, double y, double z, float damage, boolean isCrit, boolean isKill, boolean isHeal) {
        synchronized (indicators) {
            // No performance cap on indicator count
            indicators.add(new Indicator(x, y, z, damage, isCrit, isKill, isHeal));
        }
    }

    public static void tickAndCleanup() {
        long now = System.currentTimeMillis();
        if (now - lastCleanupTime < 500) return;
        lastCleanupTime = now;

        long lifeMs = (long) (lifetimeSeconds() * 1000f);
        synchronized (indicators) {
            indicators.removeIf(ind -> now - ind.spawnTime > lifeMs);
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
            Vec3 camPos = camera.position();
            long now = System.currentTimeMillis();

            boolean isEnhanced = "enhanced".equals(config.indicatorMode);
            float lifetimeSec = lifetimeSeconds();
            float durationMs = lifetimeSec * 1000f;

            for (Indicator ind : indicators) {
                float age = (now - ind.spawnTime) / 1000f;
                if (age < 0 || age > lifetimeSec) continue;
                float localMs = age * 1000f;

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
                        ind.ringRadius, drift, localMs, DRIFT_DURATION * 1000f);
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
                float[] tp = AnimaApi.evalTextProps(textProps, localMs);
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

                // 逐字动画（默认飘入入场 + 打字机出场）：剪辑由 DE 给出，逐字求值由库完成
                WorldText3D.Glyph[] glyphs = AnimaApi.charGlyphs(text, charClips(), localMs);

                // 粒子剪辑：编辑器里拖进来的粒子同样要按剪辑时间发射，否则只会在编辑器预览里出现
                ind.particles.emit(tx, ty, tz, localMs);

                // 双绘（同原版名字牌）：先 see-through 一遍（不做深度测试 → 不被方块 / 实体遮挡），
                // 再常规画一遍（有遮挡）——这样即使穿透那遍在某些渲染阶段没生效，跳字也一定可见
                int color = 0xFF000000 | (argbColor & 0x00FFFFFF);
                AnimaApi.drawWorldText3D(buffers, camera, label, tx, ty, tz,
                    offsetX, offsetY, color, null,
                    localMs, durationMs, WorldText3D.DEFAULT_SCALE, scaleMul, alphaMul, glyphs, true);
                AnimaApi.drawWorldText3D(buffers, camera, label, tx, ty, tz,
                    offsetX, offsetY, color, null,
                    localMs, durationMs, WorldText3D.DEFAULT_SCALE, scaleMul, alphaMul, glyphs, false);
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
