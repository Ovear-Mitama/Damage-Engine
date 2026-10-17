package damage.engine.api;

/**
 * Damage Engine 运行时状态（只读）。
 *
 * <p>其他模组可以直接读取当前会话的伤害、连击、分数等数据，用于联动界面或自定义逻辑。
 * 所有值由评分模块在客户端线程更新；在其他线程读取时只保证可见性，不保证同一帧内完全一致。</p>
 */
public final class DamageEngineStats {
    private static volatile boolean active = false;
    private static volatile float totalDamage = 0f;
    private static volatile int comboCount = 0;
    private static volatile int killStreak = 0;
    private static volatile int critCount = 0;
    private static volatile int hitCount = 0;
    private static volatile float score = 0f;
    private static volatile String grade = "";
    private static volatile int gradeColor = 0xFFFFFFFF;

    private DamageEngineStats() {}

    public static boolean isActive() { return active; }

    /** 当前评分会话累计伤害。 */
    public static float getTotalDamage() { return totalDamage; }

    /** 当前连击数。 */
    public static int getComboCount() { return comboCount; }

    /** 当前连杀数。 */
    public static int getKillStreak() { return killStreak; }

    public static int getCritCount() { return critCount; }

    public static int getHitCount() { return hitCount; }

    /** 当前评分会话累计分数。 */
    public static float getScore() { return score; }

    /** 当前评价等级文本（例如 S/A/B）。 */
    public static String getGrade() { return grade; }

    /** 当前评价等级颜色（ARGB）。 */
    public static int getGradeColor() { return gradeColor; }

    /** 内部使用：由评分模块更新快照。 */
    public static void update(boolean sessionActive, float damage, int combo, int streak,
                              int crits, int hits, float currentScore, String currentGrade,
                              int currentGradeColor) {
        active = sessionActive;
        totalDamage = damage;
        comboCount = combo;
        killStreak = streak;
        critCount = crits;
        hitCount = hits;
        score = currentScore;
        grade = currentGrade == null ? "" : currentGrade;
        gradeColor = currentGradeColor;
    }
}
