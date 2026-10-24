package damage.engine.api;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Damage Engine 对外 API 入口。
 *
 * <h2>扩展方式</h2>
 * <p>其他模组不需要修改 Damage Engine 本身：在自己的初始化阶段调用
 * {@link #registerBonusProvider(BonusProvider)} 注册加分项即可。注册是全局的，
 * 与加载器无关（Fabric / NeoForge 通用）。</p>
 *
 * <p>只读的会话数据（累计伤害、连击、连杀、分数、等级）通过
 * {@link DamageEngineStats} 获取。</p>
 *
 * <h2>示例</h2>
 * <pre>{@code
 * DamageEngineApi.registerBonusProvider(new BonusProvider() {
 *     public String id() { return "example_mod:execution"; }
 *     public String displayName() { return "处决额外加分"; }
 *     public float bonusOnHit(Hit hit, SessionStats session) {
 *         return hit.damage() >= 100f ? 50f : 0f;
 *     }
 * });
 * }</pre>
 */
public final class DamageEngineApi {
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

    /** 已注册的全部加分项（只读）。 */
    public static List<BonusProvider> getBonusProviders() {
        return Collections.unmodifiableList(PROVIDERS);
    }
}
