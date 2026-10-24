package damage.engine.api;

/**
 * 由其他模组实现的加分项。
 *
 * <p>注册后，每次命中都会回调 {@link #bonusOnHit(Hit, SessionStats)}；
 * 返回值会作为一条额外加分提示加入评分界面。</p>
 */
public interface BonusProvider {
    /** 唯一标识（建议使用 {@code 命名空间:路径} 形式），用于界面展示与去重。 */
    String id();

    /** 在配置界面“其他 mod 加分项”列表中展示的名称。 */
    String displayName();

    /**
     * 每次命中时计算加分。
     *
     * @param hit     本次命中信息
     * @param session 当前会话累计统计（含本次命中）
     * @return 本次为该命中增加的分数；返回 0 或负数表示不加分
     */
    float bonusOnHit(Hit hit, SessionStats session);
}
