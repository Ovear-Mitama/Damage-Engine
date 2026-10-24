package damage.engine.hud.rating;

import damage.engine.DamageEngineConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 1.12.2 版评分渲染：等级淡入淡出、分数滚动、加分提示逐条入场与持续项增长。
 * <p>布局与 1.20.1/1.21.1 保持一致，绘制在本模块原点附近。</p>
 */
public final class RatingRenderer {
    private static final long GAIN_LIFETIME_MS = 2400;
    private static final int GAIN_COLOR = 0xFFE8E8E8;
    private static final long SCORE_ANIM_MS = 350;
    private static final long SCORE_PULSE_MS = 220;
    private static final long ROW_SHIFT_MS = 180;
    private static final int MIN_ALPHA = 5;
    private static final long SESSION_FADE_MS = 180;
    private static final long CONTINUOUS_FADE_MS = 180;

    private static float displayedScore = 0f;
    private static float scoreFrom = 0f;
    private static float lastScoreTarget = -1f;
    private static long scoreAnimStart = 0;
    private static long lastScoreGainTime = 0;
    private static long seenSessionStart = 0;

    private static final Map<String, Float> CONTINUOUS_SHOWN = new LinkedHashMap<>();
    private static final Map<String, Long> CONTINUOUS_APPEAR = new LinkedHashMap<>();

    private static float gradeMinX, gradeMinY, gradeWidth, gradeHeight;
    private static float scoreMinX, scoreMinY, scoreWidth, scoreHeight;

    private RatingRenderer() {
    }

    public static float[] getLastGradeBounds() {
        return gradeWidth > 0 ? new float[]{gradeMinX, gradeMinY, gradeWidth, gradeHeight} : null;
    }

    public static float[] getLastScoreBounds() {
        return scoreWidth > 0 ? new float[]{scoreMinX, scoreMinY, scoreWidth, scoreHeight} : null;
    }

    private static float clamp(float value, float min, float max) {
        return value < min ? min : (value > max ? max : value);
    }

    private static float easeOutCubic(float t) {
        float u = 1.0f - t;
        return 1.0f - u * u * u;
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }

    private static String formatPoints(float points) {
        return String.valueOf(Math.max(1, Math.round(points)));
    }

    /** 评价等级。 */
    public static void renderGrade(boolean isPreview, float globalAlpha,
                                   String previewGrade, int previewGradeColor) {
        DamageEngineConfig config = DamageEngineConfig.getInstance();
        RatingManager rm = RatingManager.getInstance();
        Minecraft mc = Minecraft.getMinecraft();
        LogHolder.font = mc.fontRenderer;

        int baseAlpha = (int) (255 * globalAlpha);
        if (!isPreview && baseAlpha < MIN_ALPHA) return;

        String gradeText = "";
        int gradeColor = 0xFFFFFFFF;
        float gradeAlpha = 0f;
        float sessionFade = 1f;
        long now = DamageEngineConfig.getInstance().hudNow();

        if (isPreview && previewGrade != null && !previewGrade.isEmpty()) {
            long t = now % 1500;
            float progress = clamp(t / 1500.0f, 0f, 1f);
            gradeAlpha = progress < 0.15f ? progress / 0.15f
                : (progress > 0.7f ? (1f - (progress - 0.7f) / 0.3f) : 1f);
            gradeText = previewGrade;
            gradeColor = previewGradeColor;
        } else if (!isPreview && rm.isVisible()) {
            sessionFade = syncSession(rm, now);
            long elapsed = now - rm.getGradeShowTime();
            float progress = clamp(elapsed / 2500.0f, 0f, 1f);
            gradeAlpha = progress > 0.6f ? (1f - (progress - 0.6f) / 0.4f) : 1f;
            gradeText = rm.getGrade();
            gradeColor = rm.getGradeColor();
        }

        if (gradeAlpha <= 0.01f) return;
        int alpha = (int) (baseAlpha * gradeAlpha * sessionFade);
        if (alpha < MIN_ALPHA) return;

        FontRenderer font = LogHolder.font;
        int rColor = (gradeColor & 0x00FFFFFF) | (alpha << 24);
        float scale = 1.2f;
        GlStateManager.pushMatrix();
        GlStateManager.scale(scale, scale, 1.0f);
        int width = font.getStringWidth(gradeText);
        font.drawStringWithShadow(gradeText, -width / 2.0f, -font.FONT_HEIGHT / 2.0f, rColor);
        GlStateManager.popMatrix();

        gradeWidth = width * scale;
        gradeHeight = font.FONT_HEIGHT * scale;
        gradeMinX = -gradeWidth / 2f;
        gradeMinY = -gradeHeight / 2f;
    }

    /** 评分分数与加分提示。 */
    public static void renderScore(boolean isPreview, float globalAlpha) {
        DamageEngineConfig config = DamageEngineConfig.getInstance();
        RatingManager rm = RatingManager.getInstance();
        Minecraft mc = Minecraft.getMinecraft();
        LogHolder.font = mc.fontRenderer;

        int baseAlpha = (int) (255 * globalAlpha);
        if (!isPreview) {
            if (baseAlpha < MIN_ALPHA) return;
            baseAlpha = (int) (baseAlpha * syncSession(rm, System.currentTimeMillis()));
            if (baseAlpha < MIN_ALPHA) return;
        }
        drawScore(config, rm, isPreview, baseAlpha);
    }

    /** 仅用于持有字体引用，避免每帧在多个方法间传递。 */
    private static final class LogHolder {
        static FontRenderer font;
    }

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
        return clamp((now - start) / (float) SESSION_FADE_MS, 0f, 1f);
    }

    private static final class GainLine {
        final String text;
        final String suffix;
        final float suffixX;
        final int alpha;
        final float progress;
        final float y;

        GainLine(String text, String suffix, float suffixX, int alpha, float progress, float y) {
            this.text = text;
            this.suffix = suffix;
            this.suffixX = suffixX;
            this.alpha = alpha;
            this.progress = progress;
            this.y = y;
        }
    }

    private static void drawScore(DamageEngineConfig config, RatingManager rm,
                                  boolean isPreview, int baseAlpha) {
        FontRenderer font = LogHolder.font;
        long now = config.hudNow();
        float target = isPreview ? 1250f : rm.getScore();
        List<RatingManager.ScoreGain> gains =
            new ArrayList<>(isPreview ? previewGains() : rm.getRecentGains());
        gains.sort(Comparator.comparingLong(RatingManager.ScoreGain::time));
        if (!isPreview && target <= 0 && gains.isEmpty()) return;

        updateScoreAnimation(target, isPreview || config.reduceAnimations, now);

        float pulseAge = (now - lastScoreGainTime) / (float) SCORE_PULSE_MS;
        float pulse = (!config.reduceAnimations && !isPreview && pulseAge >= 0f && pulseAge < 1f)
            ? (float) Math.sin(pulseAge * Math.PI) * 0.10f : 0f;
        float scoreScale = 1.0f + pulse;

        Map<String, Float> totals = isPreview ? previewTotals() : rm.getContinuousTotals();
        Map<String, String> names = isPreview ? previewNames() : rm.getContinuousNames();
        int continuousCount = 0;
        for (float v : totals.values()) {
            if (isPreview || Math.round(v) >= 1) continuousCount++;
        }
        float feedBaseY = 13f + continuousCount * 10f;

        int maxGains = Math.max(1, Math.min(10, config.ratingScoreMaxGains));
        int first = Math.max(0, gains.size() - maxGains);
        int count = gains.size() - first;

        float[] alphas = new float[count];
        float[] progresses = new float[count];
        for (int k = 0; k < count; k++) {
            RatingManager.ScoreGain gain = gains.get(first + k);
            long age = now - gain.time();
            if (!isPreview && age < 0) continue;
            if (config.reduceAnimations) {
                alphas[k] = baseAlpha;
                progresses[k] = 1f;
                continue;
            }
            float progress = isPreview ? 0.25f : clamp(age / (float) GAIN_LIFETIME_MS, 0f, 1f);
            float fadeIn = clamp(progress / 0.08f, 0f, 1f);
            float fadeOut = 1f - clamp((progress - 0.7f) / 0.3f, 0f, 1f);
            long fadeStart = gain.fadeOutAt();
            float forced = fadeStart == Long.MAX_VALUE ? 1f
                : 1f - clamp((now - fadeStart) / (float) RatingManager.GAIN_FADE_MS, 0f, 1f);
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
                float grow = (isPreview || config.reduceAnimations) ? 1f
                    : easeOutCubic(clamp(age / (float) SCORE_ANIM_MS, 0f, 1f));
                String sourcePart = (config.showRatingScoreSource && gain.source() != null
                    && !gain.source().isEmpty()) ? " " + gain.source() : "";
                String text = "+" + formatPoints(gain.points() * grow) + sourcePart;
                String suffix = Float.isNaN(gain.distance())
                    ? gain.suffix()
                    : String.format(Locale.ROOT, " %.1fm", gain.distance() * grow);
                String finalText = "+" + formatPoints(gain.points()) + sourcePart;
                lines.add(new GainLine(text, suffix, font.getStringWidth(finalText),
                    (int) alphas[k], progresses[k], feedBaseY + shift));
            }
            if (isPreview || config.reduceAnimations) {
                shift += 10f;
            } else if (age >= 0) {
                shift += 10f * easeOutCubic(clamp(age / (float) ROW_SHIFT_MS, 0f, 1f));
            }
        }

        String scoreText = String.format(Locale.ROOT, "%,d", Math.round(displayedScore));
        String anchorText = String.format(Locale.ROOT, "%,d",
            Math.round(lastScoreTarget >= 0f ? lastScoreTarget : displayedScore));
        float scoreX = -font.getStringWidth(scoreText) / 2f;
        float feedX = -font.getStringWidth(anchorText) / 2f;

        float boundMinX = Math.min(scoreX, feedX);
        float boundMaxX = scoreX + font.getStringWidth(scoreText) * scoreScale;
        float boundMinY = 0f;
        float boundMaxY = font.FONT_HEIGHT * scoreScale;

        GlStateManager.pushMatrix();
        GlStateManager.translate(scoreX, 0f, 0f);
        GlStateManager.scale(scoreScale, scoreScale, 1f);
        font.drawString(scoreText, 0, 0, withAlpha(config.ratingScoreColor, baseAlpha));
        GlStateManager.popMatrix();

        for (int li = lines.size() - 1; li >= 0; li--) {
            GainLine line = lines.get(li);
            float pop = 1f + 0.10f
                * (1f - easeOutCubic(clamp(line.progress / 0.18f, 0f, 1f)));
            float textScale = pop * 0.8f;

            GlStateManager.pushMatrix();
            GlStateManager.translate(feedX, line.y, 0f);
            GlStateManager.scale(textScale, textScale, 1f);
            font.drawString(line.text, 0, 0, withAlpha(GAIN_COLOR, line.alpha));
            float lineWidth = font.getStringWidth(line.text);
            if (line.suffix != null && !line.suffix.isEmpty()) {
                font.drawString(line.suffix, Math.round(line.suffixX), 0,
                    withAlpha(GAIN_COLOR, line.alpha));
                lineWidth = Math.max(lineWidth, line.suffixX + font.getStringWidth(line.suffix));
            }
            GlStateManager.popMatrix();

            boundMaxX = Math.max(boundMaxX, feedX + lineWidth * textScale);
            boundMaxY = Math.max(boundMaxY, line.y + font.FONT_HEIGHT * textScale);
        }

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

            if (!isPreview && Math.round(targetValue) < 1) {
                CONTINUOUS_SHOWN.put(id, 0f);
                CONTINUOUS_APPEAR.remove(id);
                continue;
            }

            long appearAt = isPreview ? 0L : CONTINUOUS_APPEAR.computeIfAbsent(id, k -> now);
            float appear = (isPreview || config.reduceAnimations) ? 1f
                : clamp((now - appearAt) / (float) CONTINUOUS_FADE_MS, 0f, 1f);

            float shown;
            if (config.reduceAnimations) {
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
            if (config.showRatingScoreSource && name != null && !name.trim().isEmpty()) {
                text += " " + name;
            }
            int alpha = (int) (baseAlpha * 0.9f * appear);
            if (alpha >= MIN_ALPHA) {
                GlStateManager.pushMatrix();
                GlStateManager.translate(feedX, 13f + row * 10f, 0f);
                GlStateManager.scale(0.8f, 0.8f, 1f);
                font.drawString(text, 0, 0, withAlpha(GAIN_COLOR, alpha));
                String countText = null;
                if (config.showRatingScoreSource) {
                    if ("combo".equals(id) && comboCount > 0) {
                        countText = " \u00d7" + comboCount;
                    } else if ("killstreak".equals(id) && killStreak > 0) {
                        countText = " \u00d7" + killStreak;
                    }
                }
                float textWidth = font.getStringWidth(text);
                if (countText != null) {
                    int comboColor = (config.comboColor & 0x00FFFFFF) | (alpha << 24);
                    font.drawString(countText, font.getStringWidth(text), 1, comboColor);
                    textWidth += font.getStringWidth(countText);
                }
                GlStateManager.popMatrix();

                boundMaxX = Math.max(boundMaxX, feedX + textWidth * 0.8f);
                boundMaxY = Math.max(boundMaxY, 13f + row * 10f + font.FONT_HEIGHT * 0.8f);
            }
            row++;
        }

        scoreMinX = boundMinX;
        scoreMinY = boundMinY;
        scoreWidth = boundMaxX - boundMinX;
        scoreHeight = boundMaxY - boundMinY;
    }

    private static void updateScoreAnimation(float target, boolean isPreview, long now) {
        if (isPreview) {
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
            float t = clamp((now - scoreAnimStart) / (float) SCORE_ANIM_MS, 0f, 1f);
            displayedScore = scoreFrom + (lastScoreTarget - scoreFrom) * easeOutCubic(t);
            if (t >= 1f) {
                displayedScore = lastScoreTarget;
                scoreFrom = lastScoreTarget;
                scoreAnimStart = 0;
            }
        }
    }

    private static List<RatingManager.ScoreGain> previewGains() {
        long now = System.currentTimeMillis();
        List<RatingManager.ScoreGain> gains = new ArrayList<>();
        gains.add(new RatingManager.ScoreGain(0.6f, I18n.format("rating.source.combo"), now - 900));
        gains.add(new RatingManager.ScoreGain(6f, I18n.format("rating.source.hit"), now - 600));
        gains.add(new RatingManager.ScoreGain(10f, I18n.format("rating.source.crit"), now - 300));
        return gains;
    }

    private static Map<String, Float> previewTotals() {
        Map<String, Float> totals = new LinkedHashMap<>();
        totals.put("combo", 12f);
        totals.put("killstreak", 30f);
        totals.put("damage", 42f);
        return totals;
    }

    private static Map<String, String> previewNames() {
        Map<String, String> names = new LinkedHashMap<>();
        names.put("combo", I18n.format("rating.bonus.combo"));
        names.put("killstreak", I18n.format("rating.bonus.killstreak"));
        names.put("damage", I18n.format("rating.bonus.damage"));
        return names;
    }
}
