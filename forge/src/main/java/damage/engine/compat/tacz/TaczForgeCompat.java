package damage.engine.compat.tacz;

import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Forge 侧 TaCZ 爆头钩子。
 * <p>
 * 订阅 Forge 事件总线的 {@code EntityHurtByGunEvent.Pre}(带 {@code isHeadShot()}),在造成
 * 伤害之前把受击者标记给共享的伤害统计,使 TaCZ 爆头算作暴击 —— 必须用 Pre 而不是 Post,
 * 否则在别的目标刷掉待发数据时会漏掉这次标记(下一发普通命中因此被误判为暴击)。</p>
 *
 * <p>仅当运行期加载了 TaCZ 时才会被调用(调用方用 {@code Class.forName} 守卫),
 * 编译期依赖是 {@code compileOnly},玩家没装 TaCZ 时这个类不会被触碰。</p>
 */
public class TaczForgeCompat {

    public static void register(IEventBus bus) {
        bus.addListener((EntityHurtByGunEvent.Pre event) -> {
            if (event.isHeadShot() && event.getHurtEntity() != null) {
                TaczServerHeadshotTracker.markHeadshot(event.getHurtEntity().getId());
            }
        });
    }
}
