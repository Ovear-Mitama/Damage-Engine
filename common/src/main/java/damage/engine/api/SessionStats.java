package damage.engine.api;

/**
 * 当前评分会话的累计统计。
 *
 * @param comboCount  连击数
 * @param hitCount    命中次数（含暴击）
 * @param critCount   暴击次数
 * @param totalDamage 会话累计伤害
 */
public record SessionStats(int comboCount, int hitCount, int critCount, float totalDamage) {
}
