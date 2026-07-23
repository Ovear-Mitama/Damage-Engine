package damage.engine.hud;

import damage.engine.DamageEngineConfig;

public class RatingManager {
    private static final RatingManager INSTANCE = new RatingManager();

    private int sessionComboCount = 0;
    private int sessionHitCount = 0;
    private int sessionCritCount = 0;
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
            sessionActive = true;
        }
        sessionComboCount++;
        sessionHitCount++;
        if (isCrit) sessionCritCount++;
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
        sessionEndTime = 0;
        lastHitTime = 0;
    }
}
