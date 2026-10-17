package damage.engine.api;

/**
 * 单次命中的信息。
 *
 * @param damage 本次命中造成的伤害
 * @param crit   本次命中是否被判定为暴击
 */
public record Hit(float damage, boolean crit) {
}
