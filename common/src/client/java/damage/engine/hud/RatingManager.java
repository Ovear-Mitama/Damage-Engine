package damage.engine.hud;

import damage.engine.DamageEngineConfig;

import java.util.List;

public class RatingManager {
    private static final RatingManager INSTANCE = new RatingManager();

    private int sessionComboCount = 0;
    private int sessionHitCount = 0;
    private int sessionCritCount = 0;
    // 每次命中的伤害值(用于单次伤害大小加分)
    private final List<Float> sessionDamageList = new java.util.ArrayList<>();
    private boolean sessionActive = false;
    private long sessionEndTime = 0;
    private long lastHitTime = 0;
    private static final long RATING_FADE_MS = 2500;

    public static RatingManager getInstance() {
        return INSTANCE;
    }

    public void addHit(float damage, boolean isCrit) {
        if (!sessionActive) {
            sessionComboCount = 0;
            sessionHitCount = 0;
            sessionCritCount = 0;
            sessionDamageList.clear();
            sessionActive = true;
        }
        sessionComboCount++;
        sessionHitCount++;
        if (isCrit) sessionCritCount++;
        sessionDamageList.add(damage);
        lastHitTime = System.currentTimeMillis();
    }

    public void endSession() {
        if (sessionActive) {
            sessionActive = false;
            sessionEndTime = System.currentTimeMillis();
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

    public String getGrade() {
        if (sessionHitCount == 0) return "";
        DamageEngineConfig config = DamageEngineConfig.getInstance();
        if (config.ratingGrades == null || config.ratingGrades.isEmpty()) return "";

        float score = calculateScore(config);
        for (DamageEngineConfig.RatingGrade g : config.ratingGrades) {
            if (score >= g.minScore) return g.text;
        }
        return config.ratingGrades.get(config.ratingGrades.size() - 1).text;
    }

    public float getScore() {
        if (sessionHitCount == 0) return 0;
        return calculateScore(DamageEngineConfig.getInstance());
    }

    public int getGradeColor() {
        if (sessionHitCount == 0) return 0xFFFFFFFF;
        DamageEngineConfig config = DamageEngineConfig.getInstance();
        if (config.ratingGrades == null || config.ratingGrades.isEmpty()) return 0xFFFFFFFF;

        float score = calculateScore(config);
        for (DamageEngineConfig.RatingGrade g : config.ratingGrades) {
            if (score >= g.minScore) return g.color;
        }
        return config.ratingGrades.get(config.ratingGrades.size() - 1).color;
    }

    public String getGradeImagePath() {
        if (sessionHitCount == 0) return "";
        DamageEngineConfig config = DamageEngineConfig.getInstance();
        if (config.ratingGrades == null || config.ratingGrades.isEmpty()) return "";

        float score = calculateScore(config);
        for (DamageEngineConfig.RatingGrade g : config.ratingGrades) {
            if (score >= g.minScore) return g.imagePath != null ? g.imagePath : "";
        }
        DamageEngineConfig.RatingGrade lastGrade = config.ratingGrades.get(config.ratingGrades.size() - 1);
        return lastGrade.imagePath != null ? lastGrade.imagePath : "";
    }

    private float calculateScore(DamageEngineConfig config) {
        int normalHits = Math.max(0, sessionHitCount - sessionCritCount);

        // Combo points (triangular: 1+2+...+N) + Normal hit points + Crit hit points
        float score = sessionComboCount * (sessionComboCount + 1) / 2f * config.comboPoints
            + normalHits * config.hitPoints
            + sessionCritCount * config.critPoints;

        // 单次伤害大小加分:每次命中达到伤害大小阈值即增加对应分数
        if (config.damageBonuses != null && !config.damageBonuses.isEmpty()) {
            for (float dmg : sessionDamageList) {
                for (DamageEngineConfig.DamageBonus b : config.damageBonuses) {
                    if (dmg >= b.damageSize) {
                        score += b.points;
                    }
                }
            }
        }

        // 动态伤害加分:每次造成伤害增加(伤害数值 × 倍率)分
        if (config.damageScoreMultiplier != 0f) {
            float totalDamage = 0f;
            for (float dmg : sessionDamageList) {
                totalDamage += dmg;
            }
            score += totalDamage * config.damageScoreMultiplier;
        }
        return score;
    }

    public long getGradeShowTime() {
        return sessionActive ? System.currentTimeMillis() - 500 : sessionEndTime;
    }

    public void reset() {
        sessionActive = false;
        sessionComboCount = 0;
        sessionHitCount = 0;
        sessionCritCount = 0;
        sessionDamageList.clear();
        sessionEndTime = 0;
        lastHitTime = 0;
    }
}
