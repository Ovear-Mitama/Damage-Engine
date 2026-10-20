package damage.engine.hud.rating;

import com.mojang.blaze3d.platform.NativeImage;
import damage.engine.DamageEngineClient;
import damage.engine.DamageEngineConfig;
import damage.engine.hud.DamageHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 评分模块的渲染：等级显示与分数显示。
 *
 * <p>等级(S/A/B/C/D)与分数(COD 风格累计分 + 漂浮加分提示)相互独立，
 * 配置里可以分别开关。</p>
 */
public final class RatingRenderer {
    private static final long GAIN_LIFETIME_MS = 2400;
    private static final int GAIN_COLOR = 0xFFE8E8E8;
    private static final long SCORE_ANIM_MS = 350;
    private static final long SCORE_PULSE_MS = 220;
    /** 新条目出现时，旧条目向下让位的动画时长。 */
    private static final long ROW_SHIFT_MS = 180;
    /** 低于该透明度时直接不画，避免极低 alpha 下残影造成的闪烁。 */
    private static final int MIN_ALPHA = 5;
    /** 新会话开始时评分界面的淡入时长。 */
    private static final long SESSION_FADE_MS = 180;
    /** 持续项首次出现时的淡入时长。 */
    private static final long CONTINUOUS_FADE_MS = 180;

    // 分数显示动画状态：数字平滑滚动 + 每次加分轻微弹一下
    private static float displayedScore = 0f;
    private static float scoreFrom = 0f;
    private static float lastScoreTarget = -1f;
    private static long scoreAnimStart = 0;
    private static long lastScoreGainTime = 0;
    /** 已同步过的会话开始时间：变化说明是新会话，需要重播入场动画。 */
    private static long seenSessionStart = 0;

    /** 评分图片纹理缓存，避免每帧重复注册。 */
    private static final Map<String, RatingTexture> TEXTURES = new HashMap<>();
    /** 持续项当前显示值（用于平滑增长）。 */
    private static final Map<String, Float> CONTINUOUS_SHOWN = new HashMap<>();
    /** 持续项首次出现的时间（用于淡入）。 */
    private static final Map<String, Long> CONTINUOUS_APPEAR = new HashMap<>();

    // 最近一次渲染的本地包围盒，供 HUD 编辑器绘制准确的选中框
    private static float gradeMinX, gradeMinY, gradeWidth, gradeHeight;
    private static float scoreMinX, scoreMinY, scoreWidth, scoreHeight;

    /** 等级控件的本地包围盒 {minX, minY, width, height}；未渲染过时为 null。 */
    public static float[] getLastGradeBounds() {
        return gradeWidth > 0 ? new float[]{gradeMinX, gradeMinY, gradeWidth, gradeHeight} : null;
    }

    /** 分数控件的本地包围盒 {minX, minY, width, height}；未渲染过时为 null。 */
    public static float[] getLastScoreBounds() {
        return scoreWidth > 0 ? new float[]{scoreMinX, scoreMinY, scoreWidth, scoreHeight} : null;
    }

    private record RatingTexture(ResourceLocation id, int width, int height) {}

    private RatingRenderer() {}

    /** 评价等级(独立 HUD 控件)。 */
    public static void renderGrade(GuiGraphics guiGraphics, boolean isPreview, float globalAlpha,
                                   Minecraft client, String previewGrade, int previewGradeColor) {
        DamageEngineConfig config = DamageEngineConfig.getInstance();
        RatingManager rm = RatingManager.getInstance();
        int baseAlpha = (int) (255 * globalAlpha);
        if (!isPreview && baseAlpha < MIN_ALPHA) return;

        String gradeText = "";
        int gradeColor = 0xFFFFFFFF;
        String imagePath = "";
        float gradeAlpha = 0f;
        float sessionFade = 1f;

        if (isPreview && previewGrade != null && !previewGrade.isEmpty() && !rm.isVisible()) {
            long t = System.currentTimeMillis() % 1500;
            float progress = Mth.clamp(t / 1500f, 0f, 1f);
            gradeAlpha = progress < 0.15f ? progress / 0.15f
                : (progress > 0.7f ? (1f - (progress - 0.7f) / 0.3f) : 1f);
            gradeText = previewGrade;
            gradeColor = previewGradeColor;
            List<DamageEngineConfig.RatingGrade> grades = config.ratingGrades;
            if (grades != null) {
                for (DamageEngineConfig.RatingGrade g : grades) {
                    if (g.text.equals(previewGrade)) {
                        imagePath = g.imagePath;
                        break;
                    }
                }
            }
        } else if (rm.isVisible()) {
            // 预览时如果存在实时评分会话，直接用实时数据，保证预览与实际内容一致
            long nowMs = DamageHud.INSTANCE.hudTimeMs();
            sessionFade = syncSession(rm, nowMs);
            if (config.reduceAnimations) {
                // 减少动画：等级直接完整显示
                gradeAlpha = 1f;
            } else {
                long elapsed = nowMs - rm.getGradeShowTime();
                float progress = Mth.clamp(elapsed / 2000f, 0f, 1f);
                gradeAlpha = progress > 0.6f ? (1f - (progress - 0.6f) / 0.4f) : 1f;
            }
            gradeText = rm.getGrade();
            gradeColor = rm.getGradeColor();
            imagePath = rm.getGradeImagePath();
        }

        if (gradeAlpha > 0.01f) {
            int alpha = (int) (baseAlpha * gradeAlpha * sessionFade);
            if (alpha < MIN_ALPHA) return;
            drawGrade(guiGraphics, client, config, gradeText, gradeColor, imagePath, alpha);

            RatingTexture texture = config.ratingUseImages && imagePath != null && !imagePath.isEmpty()
                ? getTexture(client, imagePath) : null;
            if (texture != null) {
                gradeWidth = texture.width() * 0.15f;
                gradeHeight = texture.height() * 0.15f;
            } else {
                gradeWidth = client.font.width(gradeText) * 1.2f;
                gradeHeight = client.font.lineHeight * 1.2f;
            }
            gradeMinX = -gradeWidth / 2f;
            gradeMinY = -gradeHeight / 2f;
        }
    }

    /** 评分分数(独立 HUD 控件)。 */
    public static void renderScore(GuiGraphics guiGraphics, boolean isPreview, float globalAlpha,
                                   Minecraft client) {
        DamageEngineConfig config = DamageEngineConfig.getInstance();
        RatingManager rm = RatingManager.getInstance();
        int baseAlpha = (int) (255 * globalAlpha);
        // 预览时若有实时评分会话，则走实时数据分支（与实际 HUD 完全一致）
        boolean livePreview = isPreview && rm.isVisible();
        if (!isPreview || livePreview) {
            if (baseAlpha < MIN_ALPHA) return;
            baseAlpha = (int) (baseAlpha * syncSession(rm, DamageHud.INSTANCE.hudTimeMs()));
            if (baseAlpha < MIN_ALPHA) return;
        }
        drawScore(guiGraphics, client, config, rm, isPreview && !livePreview, baseAlpha);
    }

    /**
     * 会话切换时重置入场动画状态，返回本次会话的淡入系数。
     * 这样第一次攻击时分数会从 0 滚上来，持续项也会淡入，而不是直接出现在最终位置。
     */
    private static float syncSession(RatingManager rm, long now) {
        long start = rm.getSessionStartTime();
        if (start != seenSessionStart) {
            seenSessionStart = start;
            displayedScore = 0f;
            scoreFrom = 0f;
            lastScoreTarget = -1f;
            scoreAnimStart = 0;
            lastScoreGainTime = 0;
            CONTINUOUS_SHOWN.clear();
            CONTINUOUS_APPEAR.clear();
        }
        if (start <= 0) return 1f;
        // 减少动画：跳过整块评分界面的淡入
        if (DamageEngineConfig.getInstance().reduceAnimations) return 1f;
        return Mth.clamp((now - start) / (float) SESSION_FADE_MS, 0f, 1f);
    }

    private static void drawGrade(GuiGraphics guiGraphics, Minecraft client, DamageEngineConfig config,
                                  String text, int color, String imagePath, int alpha) {
        Font font = client.font;
        int rColor = (color & 0x00FFFFFF) | (alpha << 24);

        if (config.ratingUseImages && imagePath != null && !imagePath.isEmpty()) {
            RatingTexture texture = getTexture(client, imagePath);
            if (texture != null) {
                guiGraphics.pose().pushPose();
                guiGraphics.pose().scale(0.15f, 0.15f, 1.0f);
                guiGraphics.setColor(1.0f, 1.0f, 1.0f, alpha / 255.0f);
                guiGraphics.blit(texture.id(), -texture.width() / 2, -texture.height() / 2, 0, 0,
                    texture.width(), texture.height(), texture.width(), texture.height());
                guiGraphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
                guiGraphics.pose().popPose();
                return;
            }
        }

        float scale = 1.2f;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(scale, scale, 1.0f);
        int width = font.width(text);
        guiGraphics.drawString(font, text, -width / 2, -font.lineHeight / 2, rColor);
        guiGraphics.pose().popPose();
    }

    /**
     * 一条浮动加分提示。
     * suffix（如距离 "12.3m"）单独绘制在固定位置，避免分值增长时被推着左右滑动。
     */
    private record GainLine(String text, String suffix, float suffixX, int alpha, float progress, float y) {}

    private static void drawScore(GuiGraphics guiGraphics, Minecraft client, DamageEngineConfig config,
                                  RatingManager rm, boolean isPreview, int baseAlpha) {
        Font font = client.font;
        long now = DamageHud.INSTANCE.hudTimeMs();
        boolean reduceAnimations = config.reduceAnimations;
        float target = isPreview ? 1250f : rm.getScore();
        // getRecentGains() 已经返回快照，无需再复制一次
        List<RatingManager.ScoreGain> gains = isPreview ? previewGains() : rm.getRecentGains();
        // 严格按加入时间升序：先出现的排在前面（上方）
        gains.sort(Comparator.comparingLong(RatingManager.ScoreGain::time));
        if (!isPreview && target <= 0 && gains.isEmpty()) return;

        updateScoreAnimation(target, isPreview, now, reduceAnimations);

        // 加分时数字轻轻弹一下
        float pulseAge = (now - lastScoreGainTime) / (float) SCORE_PULSE_MS;
        float pulse = (!isPreview && !reduceAnimations && pulseAge >= 0f && pulseAge < 1f)
            ? (float) Math.sin(pulseAge * Math.PI) * 0.10f
            : 0f;
        float scoreScale = 1f + pulse;

        // 持续项（如“伤害”）固定在分数下方；浮动提示排在持续项下面
        Map<String, Float> totals = isPreview ? previewTotals() : rm.getContinuousTotals();
        Map<String, String> names = isPreview ? previewNames() : rm.getContinuousNames();
        // 只统计真正会显示的持续项（四舍五入后至少 1 分），避免首击的 0.2 连击占一行
        int continuousCount = 0;
        for (float v : totals.values()) {
            if (isPreview || Math.round(v) >= 1) continuousCount++;
        }
        float feedBaseY = 13f + continuousCount * 10f;

        // 最新条目在最上方，旧条目依次向下排开；新条目出现时旧条目平滑下移让位
        int maxGains = Mth.clamp(config.ratingScoreMaxGains, 1, 10);
        int first = Math.max(0, gains.size() - maxGains);
        int count = gains.size() - first;

        float[] alphas = new float[count];
        float[] progresses = new float[count];
        for (int k = 0; k < count; k++) {
            RatingManager.ScoreGain gain = gains.get(first + k);
            long age = now - gain.time();
            if (!isPreview && age < 0) continue;
            float progress = isPreview
                ? 0.25f
                : Mth.clamp(age / (float) GAIN_LIFETIME_MS, 0f, 1f);

            float fadeIn = Mth.clamp(progress / 0.08f, 0f, 1f);
            float fadeOut = 1f - Mth.clamp((progress - 0.7f) / 0.3f, 0f, 1f);
            // 被新条目挤出显示上限时快速淡出，避免直接消失
            long fadeStart = gain.fadeOutAt();
            float forced = fadeStart == Long.MAX_VALUE ? 1f
                : 1f - Mth.clamp((now - fadeStart) / (float) RatingManager.GAIN_FADE_MS, 0f, 1f);
            int alpha = (int) (baseAlpha * fadeIn * fadeOut * forced);
            if (alpha > 4) {
                alphas[k] = alpha;
                progresses[k] = progress;
            }
        }

        List<GainLine> lines = new ArrayList<>();
        float shift = 0f;
        for (int k = count - 1; k >= 0; k--) {
            RatingManager.ScoreGain gain = gains.get(first + k);
            long age = now - gain.time();
            if (alphas[k] > 0f) {
                // 每项加分独立从 0 增长到自身分值，与总分相同的缓出节奏；
                // 以各自的加入时间计算进度，新条目不会打断已有条目的动画
                float grow = (isPreview || reduceAnimations) ? 1f
                    : easeOutCubic(Mth.clamp(age / (float) SCORE_ANIM_MS, 0f, 1f));
                String sourcePart = (config.showRatingScoreSource && gain.source() != null && !gain.source().isEmpty())
                    ? " " + gain.source() : "";
                String text = "+" + formatPoints(gain.points() * grow) + sourcePart;
                // 距离击杀的米数与分值用同一条增长曲线：0 → 实际距离
                String suffix = Float.isNaN(gain.distance())
                    ? gain.suffix()
                    : String.format(Locale.ROOT, " %.1fm", gain.distance() * grow);
                // 后缀按“最终分值”的宽度定位：分值滚动增长时距离文本不会跟着平移
                String finalText = "+" + formatPoints(gain.points()) + sourcePart;
                lines.add(new GainLine(text, suffix, font.width(finalText),
                    (int) alphas[k], progresses[k], feedBaseY + shift));
            }
            // 该条目出现后，所有更旧的条目在 ROW_SHIFT_MS 内向下让出一行
            if (isPreview || reduceAnimations) {
                shift += 10f;
            } else if (age >= 0) {
                shift += 10f * easeOutCubic(Mth.clamp(age / (float) ROW_SHIFT_MS, 0f, 1f));
            }
        }

        String scoreText = String.format(Locale.ROOT, "%,d", Math.round(displayedScore));
        // 分数与加分提示共用同一个固定左边缘：位数变化时不左右移动，
        // 同时保证下方的加分/持续项始终和分数左对齐。
        float anchorX = -font.width("00,000") / 2f;
        float scoreX = anchorX;
        float feedX = anchorX;

        // 记录本帧的包围盒，供 HUD 编辑器使用
        float boundMinX = Math.min(scoreX, feedX);
        float boundMaxX = scoreX + font.width(scoreText) * scoreScale;
        float boundMinY = 0f;
        float boundMaxY = font.lineHeight * scoreScale;

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(scoreX, 0f, 0f);
        guiGraphics.pose().scale(scoreScale, scoreScale, 1f);
        guiGraphics.drawString(font, scoreText, 0, 0, withAlpha(config.ratingScoreColor, baseAlpha));
        guiGraphics.pose().popPose();

        // 固定行距，纵向位置由逐条下移动画给出；旧条目先画，最新条目覆盖在最上层
        for (int li = lines.size() - 1; li >= 0; li--) {
            GainLine line = lines.get(li);
            float pop = reduceAnimations ? 1f : 1f + 0.10f
                * (1f - easeOutCubic(Mth.clamp(line.progress() / 0.18f, 0f, 1f)));
            float textScale = pop * 0.8f;

            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(feedX, line.y(), 0f);
            guiGraphics.pose().scale(textScale, textScale, 1f);
            guiGraphics.drawString(font, line.text(), 0, 0, withAlpha(GAIN_COLOR, line.alpha()));
            float lineWidth = font.width(line.text());
            if (line.suffix() != null && !line.suffix().isEmpty()) {
                guiGraphics.drawString(font, line.suffix(), Math.round(line.suffixX()), 0, withAlpha(GAIN_COLOR, line.alpha()));
                lineWidth = Math.max(lineWidth, line.suffixX() + font.width(line.suffix()));
            }
            guiGraphics.pose().popPose();

            boundMaxX = Math.max(boundMaxX, feedX + lineWidth * textScale);
            boundMaxY = Math.max(boundMaxY, line.y() + font.lineHeight * textScale);
        }

        // 持续项（如“伤害”）常驻显示在分数正下方，数值平滑变化
        if (totals.isEmpty()) {
            CONTINUOUS_SHOWN.clear();
            CONTINUOUS_APPEAR.clear();
            scoreMinX = boundMinX;
            scoreMinY = boundMinY;
            scoreWidth = boundMaxX - boundMinX;
            scoreHeight = boundMaxY - boundMinY;
            return;
        }
        CONTINUOUS_SHOWN.keySet().removeIf(id -> !totals.containsKey(id));
        CONTINUOUS_APPEAR.keySet().removeIf(id -> !totals.containsKey(id));

        int row = 0;
        int comboCount = isPreview ? 12 : rm.getComboCount();
        int killStreak = isPreview ? 3 : rm.getKillStreak();
        for (Map.Entry<String, Float> entry : totals.entrySet()) {
            float targetValue = entry.getValue();
            String id = entry.getKey();

            // 四舍五入后不足 1 分的持续项不显示（例如首击的 0.2 连击），
            // 同时清空入场状态，避免它在真正出现前就把淡入/增长动画消耗掉。
            if (!isPreview && Math.round(targetValue) < 1) {
                CONTINUOUS_SHOWN.put(id, 0f);
                CONTINUOUS_APPEAR.remove(id);
                continue;
            }

            // 真正出现时才记录入场时间：从 0 开始增长 + 淡入
            long appearAt = (isPreview || reduceAnimations) ? 0L
                : CONTINUOUS_APPEAR.computeIfAbsent(id, k -> now);
            float appear = (isPreview || reduceAnimations) ? 1f
                : Mth.clamp((now - appearAt) / (float) CONTINUOUS_FADE_MS, 0f, 1f);

            float shown;
            if (reduceAnimations) {
                // 减少动画：持续项直接显示最终值
                shown = targetValue;
            } else {
                shown = CONTINUOUS_SHOWN.getOrDefault(id, 0f);
                shown += (targetValue - shown) * 0.25f;
                if (Math.abs(targetValue - shown) < 0.05f) shown = targetValue;
            }
            CONTINUOUS_SHOWN.put(id, shown);

            int rounded = isPreview ? Math.round(shown) : Math.max(1, Math.round(shown));
            String text = "+" + (isPreview ? formatPoints(shown) : String.valueOf(rounded));
            String name = names.get(id);
            if (config.showRatingScoreSource && name != null && !name.isBlank()) {
                text += " " + name;
            }
            int alpha = (int) (baseAlpha * 0.9f * appear);
            if (alpha >= MIN_ALPHA) {
                guiGraphics.pose().pushPose();
                guiGraphics.pose().translate(feedX, 13f + row * 10f, 0f);
                guiGraphics.pose().scale(0.8f, 0.8f, 1f);
                guiGraphics.drawString(font, text, 0, 0, withAlpha(GAIN_COLOR, alpha));
                // 连击/连杀次数显示在名称后面；关闭“加分项目名称”时一起隐藏
                String countText = null;
                if (config.showRatingScoreSource) {
                    if ("combo".equals(entry.getKey()) && comboCount > 0) {
                        countText = " ×" + comboCount;
                    } else if ("killstreak".equals(entry.getKey()) && killStreak > 0) {
                        countText = " ×" + killStreak;
                    }
                }
                float textWidth = font.width(text);
                if (countText != null) {
                    int comboColor = (config.comboColor & 0x00FFFFFF) | (alpha << 24);
                    // 数字/CJK 视觉高度不同，向下 1px 让基线与名称看起来对齐
                    guiGraphics.drawString(font, countText, font.width(text), 1, comboColor);
                    textWidth += font.width(countText);
                }
                guiGraphics.pose().popPose();

                boundMaxX = Math.max(boundMaxX, feedX + textWidth * 0.8f);
                boundMaxY = Math.max(boundMaxY, 13f + row * 10f + font.lineHeight * 0.8f);
            }
            row++;
        }

        scoreMinX = boundMinX;
        scoreMinY = boundMinY;
        scoreWidth = boundMaxX - boundMinX;
        scoreHeight = boundMaxY - boundMinY;
    }

    /** 分数数字的平滑滚动：新分数在 350ms 内缓出追上，会话重置时直接归零。 */
    private static void updateScoreAnimation(float target, boolean isPreview, long now, boolean reduceAnimations) {
        if (isPreview || reduceAnimations) {
            displayedScore = target;
            scoreFrom = target;
            lastScoreTarget = target;
            scoreAnimStart = 0;
            lastScoreGainTime = 0;
            return;
        }
        if (lastScoreTarget < 0f) {
            displayedScore = 0f;
            scoreFrom = 0f;
            lastScoreTarget = target;
            scoreAnimStart = now;
            lastScoreGainTime = now;
        } else if (target < lastScoreTarget - 0.01f) {
            displayedScore = target;
            scoreFrom = target;
            lastScoreTarget = target;
            scoreAnimStart = 0;
        } else if (target > lastScoreTarget + 0.01f) {
            scoreFrom = displayedScore;
            lastScoreTarget = target;
            scoreAnimStart = now;
            lastScoreGainTime = now;
        }

        if (scoreAnimStart > 0) {
            float t = Mth.clamp((now - scoreAnimStart) / (float) SCORE_ANIM_MS, 0f, 1f);
            displayedScore = scoreFrom + (lastScoreTarget - scoreFrom) * easeOutCubic(t);
            if (t >= 1f) {
                displayedScore = lastScoreTarget;
                scoreFrom = lastScoreTarget;
                scoreAnimStart = 0;
            }
        }
    }

    private static float easeOutCubic(float t) {
        float u = 1f - t;
        return 1f - u * u * u;
    }

    private static List<RatingManager.ScoreGain> previewGains() {
        long now = System.currentTimeMillis();
        List<RatingManager.ScoreGain> gains = new ArrayList<>();
        gains.add(new RatingManager.ScoreGain(0.6f, translate("rating.source.combo"), now - 900));
        gains.add(new RatingManager.ScoreGain(6f, translate("rating.source.hit"), now - 600));
        gains.add(new RatingManager.ScoreGain(10f, translate("rating.source.crit"), now - 300));
        return gains;
    }

    /** 编辑器预览：展示所有持续项，方便调整位置。 */
    private static Map<String, Float> previewTotals() {
        Map<String, Float> totals = new LinkedHashMap<>();
        totals.put("combo", 12f);
        totals.put("killstreak", 30f);
        totals.put("damage", 42f);
        return totals;
    }

    private static Map<String, String> previewNames() {
        Map<String, String> names = new LinkedHashMap<>();
        names.put("combo", translate("rating.bonus.combo"));
        names.put("killstreak", translate("rating.bonus.killstreak"));
        names.put("damage", translate("rating.bonus.damage"));
        return names;
    }

    private static String translate(String key) {
        return Component.translatable(key).getString();
    }

    private static String formatPoints(float points) {
        // 不显示小数；不足 1 分的条目也至少显示 1，避免出现 "0 连击"
        return String.valueOf(Math.max(1, Math.round(points)));
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Mth.clamp(alpha, 0, 255) << 24);
    }

    private static RatingTexture getTexture(Minecraft client, String imagePath) {
        RatingTexture cached = TEXTURES.get(imagePath);
        if (cached != null) return cached;

        try {
            File imageFile = new File(client.gameDirectory,
                "config/damage-engine/images/" + imagePath + ".png");
            if (!imageFile.exists()) {
                throw new Exception("Image file not found: " + imageFile.getAbsolutePath());
            }
            NativeImage image;
            try (FileInputStream input = new FileInputStream(imageFile)) {
                image = NativeImage.read(input);
            }
            if (image == null) {
                throw new Exception("Failed to read native image");
            }

            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                "damage-engine", "rating_" + imagePath);
            client.getTextureManager().register(id, new DynamicTexture(image));
            RatingTexture texture = new RatingTexture(id, image.getWidth(), image.getHeight());
            TEXTURES.put(imagePath, texture);
            return texture;
        } catch (Exception e) {
            DamageEngineClient.LOGGER.error("Failed to load rating image: " + e.getMessage());
            return null;
        }
    }
}
