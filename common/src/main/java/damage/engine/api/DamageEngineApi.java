package damage.engine.api;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Damage Engine 对外 API。
 *
 * <p>其他模组可通过 {@link #registerBonusProvider(BonusProvider)} 注册自定义“加分项”，
 * 参与评分(Rating)计算。加分条件完全由注册方自行定义：每次命中时会回调
 * {@link BonusProvider#bonusOnHit(Hit, SessionStats)}，返回值即本次为该命中增加的分数。</p>
 *
 * <p>注册是全局的、与加载器无关(Fabric / NeoForge 通用)，通常在模组初始化阶段调用一次即可。</p>
 *
 * <h2>示例</h2>
 * <pre>{@code
 * DamageEngineApi.registerBonusProvider(new DamageEngineApi.BonusProvider() {
 *     public String id() { return "example_mod:execution"; }
 *     public String displayName() { return "处决额外加分"; }
 *     public float bonusOnHit(Hit hit, SessionStats session) {
 *         return hit.damage() >= 100f ? 50f : 0f;
 *     }
 * });
 * }</pre>
 */
public final class DamageEngineApi {

    /**
     * 单次命中的信息。
     *
     * @param damage 本次命中造成的伤害
     * @param crit   本次命中是否被判定为暴击
     */
    public record Hit(float damage, boolean crit) {}

    /**
     * 当前评分会话的累计统计。
     *
     * @param comboCount 连击数
     * @param hitCount   命中次数(含暴击)
     * @param critCount  暴击次数
     * @param totalDamage 会话累计伤害
     */
    public record SessionStats(int comboCount, int hitCount, int critCount, float totalDamage) {}

    /**
     * 由其他模组实现的加分项。
     */
    public interface BonusProvider {
        /** 唯一标识(建议使用 {@code 命名空间:路径} 形式)，用于界面展示与去重。 */
        String id();

        /** 在配置界面“其他mod加分项”列表中展示的名称。 */
        String displayName();

        /**
         * 每次命中时计算加分。
         *
         * @param hit     本次命中信息
         * @param session 当前会话累计统计(含本次命中)
         * @return 本次为该命中增加的分数；返回 0 或负数表示不加分
         */
        float bonusOnHit(Hit hit, SessionStats session);
    }

    private static final List<BonusProvider> PROVIDERS = new CopyOnWriteArrayList<>();

    private DamageEngineApi() {}

    /**
     * 注册一个加分项。重复注册同一 {@link BonusProvider#id()} 会被忽略。
     *
     * @return 是否注册成功
     */
    public static boolean registerBonusProvider(BonusProvider provider) {
        if (provider == null || provider.id() == null || provider.id().isEmpty()) {
            return false;
        }
        for (BonusProvider existing : PROVIDERS) {
            if (provider.id().equals(existing.id())) {
                return false;
            }
        }
        PROVIDERS.add(provider);
        return true;
    }

    /** 已注册的全部加分项(只读)。 */
    public static List<BonusProvider> getBonusProviders() {
        return Collections.unmodifiableList(PROVIDERS);
    }
}
