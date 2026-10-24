package damage.engine.api;

/**
 * 当前评分会话的累计统计。
 */
public final class SessionStats {
    private final int comboCount;
    private final int hitCount;
    private final int critCount;
    private final float totalDamage;

    public SessionStats(int comboCount, int hitCount, int critCount, float totalDamage) {
        this.comboCount = comboCount;
        this.hitCount = hitCount;
        this.critCount = critCount;
        this.totalDamage = totalDamage;
    }

    /** 连击数。 */
    public int comboCount() {
        return comboCount;
    }

    /** 命中次数（含暴击）。 */
    public int hitCount() {
        return hitCount;
    }

    /** 暴击次数。 */
    public int critCount() {
        return critCount;
    }

    /** 会话累计伤害。 */
    public float totalDamage() {
        return totalDamage;
    }
}
