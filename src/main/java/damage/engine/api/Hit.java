package damage.engine.api;

/**
 * 单次命中的信息。
 */
public final class Hit {
    private final float damage;
    private final boolean crit;

    public Hit(float damage, boolean crit) {
        this.damage = damage;
        this.crit = crit;
    }

    /** 本次命中造成的伤害。 */
    public float damage() {
        return damage;
    }

    /** 本次命中是否被判定为暴击。 */
    public boolean crit() {
        return crit;
    }
}
