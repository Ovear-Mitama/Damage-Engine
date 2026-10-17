package damage.engine.hud.rating;

import damage.engine.DamageEngineConfig;
import damage.engine.api.BonusProvider;
import damage.engine.api.DamageEngineApi;
import damage.engine.api.DamageEngineStats;
import damage.engine.api.Hit;
import damage.engine.api.SessionStats;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class RatingManager {
    private static final RatingManager INSTANCE = new RatingManager();

    private int sessionComboCount = 0;
    private int sessionKillStreak = 0;
    private int sessionHitCount = 0;
    private int sessionCritCount = 0;
    private float sessionTotalDamage = 0f;
    private float sessionScore = 0f;
    private boolean sessionActive = false;
    private long sessionEndTime = 0;
    /** 当前会话的开始时间；渲染层用它判断是否需要重播入场动画。 */
    private long sessionStartTime = 0;
    private long lastHitTime = 0;
    private long lastKillTime = 0;
    private static final long RATING_FADE_MS = 2500;
    /** 单条加分提示的存活时间。 */
    private static final long GAIN_LIFETIME_MS = 2400;
    /** 同一批加分提示依次进入的间隔，避免同时弹出。 */
    private static final long GAIN_STAGGER_MS = 200;
    /** 超出显示上限时，被挤出的条目淡出所需时间。 */
    public static final long GAIN_FADE_MS = 300;

    /** 下一次加分提示可以入场的时间。 */
    private long nextGainTime = 0;

    /** 一条浮动加分提示。 */
    public static final class ScoreGain {
        private final float points;
        private final String source;
        private final long time;
        private final String suffix;
        private long fadeOutAt = Long.MAX_VALUE;

        public ScoreGain(float points, String source, long time) {
            this(points, source, time, "");
        }

        public ScoreGain(float points, String source, long time, String suffix) {
            this.points = points;
            this.source = source;
            this.time = time;
            this.suffix = suffix == null ? "" : suffix;
        }

        public float points() { return points; }
        public String source() { return source; }
        public long time() { return time; }
        public String suffix() { return suffix; }
        public long fadeOutAt() { return fadeOutAt; }

        private void forceFade(long now) {
            if (fadeOutAt == Long.MAX_VALUE) fadeOutAt = now;
        }
    }

    private final List<ScoreGain> recentGains = new CopyOnWriteArrayList<>();
    /** 持续项累计分（例如“伤害”），常驻显示在评分界面。 */
    private final Map<String, Float> continuousTotals = Collections.synchronizedMap(new LinkedHashMap<>());
    private final Map<String, String> continuousNames = Collections.synchronizedMap(new LinkedHashMap<>());

    public static RatingManager getInstance() {
        return INSTANCE;
    }

    public void addHit(float damage, boolean isCrit) {
        addHit(damage, isCrit, false, 0f, false);
    }

    /**
     * 记录一次命中。
     *
     * @param damage   本次伤害
     * @param isCrit   是否暴击
     * @param killed   本次是否击杀
     * @param distance 与目标的距离（米）
     * @param unseen   是否为目标未面向自己的击杀
     */
    public void addHit(float damage, boolean isCrit, boolean killed, float distance, boolean unseen) {
        DamageEngineConfig config = DamageEngineConfig.getInstance();
        if (!sessionActive) {
            resetSession();
            sessionActive = true;
            sessionStartTime = System.currentTimeMillis();
            // 持续项即使在本次命中前也先占位，保证界面里一直能看到它
            if (config.bonusRules != null) {
                for (DamageEngineConfig.BonusRule rule : config.bonusRules) {
                    if (rule != null && rule.enabled && rule.continuous) {
                        continuousTotals.putIfAbsent(rule.id, 0f);
                        continuousNames.put(rule.id, ruleName(rule));
                    }
                }
            }
        }
        long now = System.currentTimeMillis();
        // 重置时间：优先读取表达式里的 {combo,time:...} / {killstreak,time:...}
        float comboTimeoutMs = config.comboResetTime * 1000f;
        float killstreakTimeoutMs = 0f;
        Map<String, Float> timeoutVars = new HashMap<>();
        timeoutVars.put("combo_time", comboTimeoutMs);
        if (config.bonusRules != null) {
            boolean comboDeclared = false;
            for (DamageEngineConfig.BonusRule rule : config.bonusRules) {
                if (rule == null || !rule.enabled) continue;
                if (!comboDeclared) {
                    float declared = ScoreExpression.timeout(rule.expression, "combo", timeoutVars);
                    if (declared > 0f) {
                        comboTimeoutMs = declared;
                        comboDeclared = true;
                    }
                }
                float streakDeclared = ScoreExpression.timeout(rule.expression, "killstreak", timeoutVars);
                if (streakDeclared > 0f) {
                    killstreakTimeoutMs = streakDeclared;
                }
            }
        }
        if (sessionActive && comboTimeoutMs > 0f && now - lastHitTime > (long) comboTimeoutMs) {
            sessionComboCount = 0;
        }
        sessionComboCount++;
        sessionHitCount++;
        if (isCrit) sessionCritCount++;
        if (killed) {
            if (lastKillTime > 0 && killstreakTimeoutMs > 0f
                && now - lastKillTime > (long) killstreakTimeoutMs) {
                sessionKillStreak = 0;
            }
            sessionKillStreak++;
            lastKillTime = now;
        }
        sessionTotalDamage += damage;
        lastHitTime = now;

        Map<String, Float> vars = new HashMap<>();
        vars.put("combo", (float) sessionComboCount);
        vars.put("combo_time", config.comboResetTime * 1000f);
        // 连杀只在击杀的这次命中里参与计分，避免后续普通攻击重复加分
        vars.put("killstreak", killed ? (float) sessionKillStreak : 0f);
        vars.put("hit", 1f);
        vars.put("crit", isCrit ? 1f : 0f);
        vars.put("damage", damage);
        vars.put("kill", killed ? 1f : 0f);
        vars.put("meter", distance);
        vars.put("kill_meter", killed ? distance : 0f);
        vars.put("unseen", unseen ? 1f : 0f);
        vars.put("hit_count", (float) sessionHitCount);
        vars.put("crit_count", (float) sessionCritCount);
        vars.put("total_damage", sessionTotalDamage);

        if (config.bonusRules != null) {
            for (DamageEngineConfig.BonusRule rule : config.bonusRules) {
                if (rule == null || !rule.enabled) continue;
                float points = ScoreExpression.evaluate(rule.expression, vars);
                if (points <= 0f) continue;
                sessionScore += points;
                if (rule.continuous) {
                    continuousTotals.merge(rule.id, points, Float::sum);
                    continuousNames.put(rule.id, ruleName(rule));
                } else {
                    String suffix = "distance".equals(rule.id)
                        ? String.format(java.util.Locale.ROOT, " %.1fm", distance) : "";
                    addGain(points, ruleName(rule), now, suffix);
                }
            }
        }

        // 其他 mod 注册的加分项
        SessionStats stats = new SessionStats(
            sessionComboCount, sessionHitCount, sessionCritCount, sessionTotalDamage);
        Hit hit = new Hit(damage, isCrit);
        for (BonusProvider provider : DamageEngineApi.getBonusProviders()) {
            try {
                float points = provider.bonusOnHit(hit, stats);
                if (points > 0f) {
                    sessionScore += points;
                    addGain(points, provider.displayName(), now, "");
                }
            } catch (Throwable ignored) {
                // 单个加分项抛异常不应影响整体评分
            }
        }

        trimGains();

        DamageEngineStats.update(true, sessionTotalDamage, sessionComboCount, sessionKillStreak,
            sessionCritCount, sessionHitCount, sessionScore, getGrade(), getGradeColor());
    }

    private void addGain(float points, String source, long now) {
        addGain(points, source, now, "");
    }

    private void addGain(float points, String source, long now, String suffix) {
        // 依加入顺序排队入场：同一批加分项不会同时出现
        long time = Math.max(now, nextGainTime);
        nextGainTime = time + GAIN_STAGGER_MS;
        recentGains.add(new ScoreGain(points, source, time, suffix));
    }

    private static String ruleName(DamageEngineConfig.BonusRule rule) {
        if (rule.name != null && !rule.name.isBlank()) return rule.name;
        if (rule.builtin) {
            String key = "rating.bonus." + rule.id;
            String translated = Component.translatable(key).getString();
            if (!translated.equals(key)) return translated;
        }
        return rule.id;
    }

    /** 只保留最近的若干条加分提示；被挤出的条目短暂淡出后再移除。 */
    private void trimGains() {
        int max = Math.max(1, DamageEngineConfig.getInstance().ratingScoreMaxGains);
        int excess = recentGains.size() - max;
        if (excess <= 0) return;
        long now = System.currentTimeMillis();
        for (int i = 0; i < excess && i < recentGains.size(); i++) {
            recentGains.get(i).forceFade(now);
        }
    }

    /** 最近的加分条目（快照）；过期或淡出完成的会在读取时清理，供渲染层使用。 */
    public List<ScoreGain> getRecentGains() {
        long now = System.currentTimeMillis();
        recentGains.removeIf(gain -> {
            if (now - gain.time() > GAIN_LIFETIME_MS) return true;
            long fadeStart = gain.fadeOutAt();
            return fadeStart != Long.MAX_VALUE && now - fadeStart > GAIN_FADE_MS;
        });
        trimGains();
        return new ArrayList<>(recentGains);
    }

    /** 持续项累计分（键为规则 id）的稳定快照。 */
    public Map<String, Float> getContinuousTotals() {
        synchronized (continuousTotals) {
            return new LinkedHashMap<>(continuousTotals);
        }
    }

    /** 持续项显示名称（键为规则 id）的稳定快照。 */
    public Map<String, String> getContinuousNames() {
        synchronized (continuousNames) {
            return new LinkedHashMap<>(continuousNames);
        }
    }

    /** 当前会话开始时间。 */
    public long getSessionStartTime() {
        return sessionStartTime;
    }

    public void endSession() {
        if (sessionActive) {
            sessionActive = false;
            sessionEndTime = System.currentTimeMillis();
            DamageEngineStats.update(false, sessionTotalDamage, sessionComboCount, sessionKillStreak,
                sessionCritCount, sessionHitCount, sessionScore, getGrade(), getGradeColor());
        }
    }

    public boolean isActive() { return sessionActive; }

    public boolean isVisible() {
        if (sessionActive) {
            // Auto-end session if past rating reset time
            float resetTime = DamageEngineConfig.getInstance().ratingResetTime;
            if (resetTime > 0 && System.currentTimeMillis() - lastHitTime > resetTime * 1000) {
                endSession();
            }
            return sessionHitCount > 0;
        }
        return System.currentTimeMillis() - sessionEndTime < RATING_FADE_MS;
    }

    /** 评分模块整体淡出透明度：会话结束后 2.5s 内平滑消失，避免突然闪没。 */
    public float getFadeAlpha() {
        if (!isVisible()) return 0f;
        if (sessionActive) return 1f;
        float t = (System.currentTimeMillis() - sessionEndTime) / (float) RATING_FADE_MS;
        float alpha = 1f - t;
        return Math.max(0f, Math.min(1f, alpha));
    }

    public String getGrade() {
        if (sessionHitCount == 0) return "";
        DamageEngineConfig config = DamageEngineConfig.getInstance();
        if (config.ratingGrades == null || config.ratingGrades.isEmpty()) return "";

        for (DamageEngineConfig.RatingGrade g : config.ratingGrades) {
            if (sessionScore >= g.minScore) return g.text;
        }
        return config.ratingGrades.get(config.ratingGrades.size() - 1).text;
    }

    public float getScore() {
        if (sessionHitCount == 0) return 0;
        return sessionScore;
    }

    public int getComboCount() {
        return sessionComboCount;
    }

    public int getKillStreak() {
        return sessionKillStreak;
    }

    public int getGradeColor() {
        if (sessionHitCount == 0) return 0xFFFFFFFF;
        DamageEngineConfig config = DamageEngineConfig.getInstance();
        if (config.ratingGrades == null || config.ratingGrades.isEmpty()) return 0xFFFFFFFF;

        for (DamageEngineConfig.RatingGrade g : config.ratingGrades) {
            if (sessionScore >= g.minScore) return g.color;
        }
        return config.ratingGrades.get(config.ratingGrades.size() - 1).color;
    }

    public String getGradeImagePath() {
        if (sessionHitCount == 0) return "";
        DamageEngineConfig config = DamageEngineConfig.getInstance();
        if (config.ratingGrades == null || config.ratingGrades.isEmpty()) return "";

        for (DamageEngineConfig.RatingGrade g : config.ratingGrades) {
            if (sessionScore >= g.minScore) return g.imagePath != null ? g.imagePath : "";
        }
        DamageEngineConfig.RatingGrade lastGrade = config.ratingGrades.get(config.ratingGrades.size() - 1);
        return lastGrade.imagePath != null ? lastGrade.imagePath : "";
    }

    public long getGradeShowTime() {
        return sessionActive ? System.currentTimeMillis() - 500 : sessionEndTime;
    }

    public void reset() {
        sessionActive = false;
        resetSession();
        sessionEndTime = 0;
        lastHitTime = 0;
    }

    private void resetSession() {
        sessionComboCount = 0;
        sessionKillStreak = 0;
        sessionHitCount = 0;
        sessionCritCount = 0;
        sessionTotalDamage = 0f;
        sessionScore = 0f;
        sessionStartTime = 0;
        lastKillTime = 0;
        nextGainTime = 0;
        long now = System.currentTimeMillis();
        for (ScoreGain gain : recentGains) gain.forceFade(now);
        continuousTotals.clear();
        continuousNames.clear();
        DamageEngineStats.update(false, 0f, 0, 0, 0, 0, 0f, "", 0xFFFFFFFF);
    }
}
