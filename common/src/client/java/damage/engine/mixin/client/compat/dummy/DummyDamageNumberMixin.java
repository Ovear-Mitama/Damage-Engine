package damage.engine.mixin.client.compat.dummy;

import damage.engine.compat.dummy.DummyDamageCompat;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 软依赖 MmmMmmMmmMmm:直接读取假人模组发给客户端的伤害数字消息。
 * <p>
 * 假人的生命值在同步到客户端前就已回满,纯客户端(服务端没装本模组)无法通过血量差
 * 检测到伤害。目标类用字符串指定,没装假人模组时该 mixin 不会被应用;
 * 注入非致命且配置 {@code required: false},假人模组改了内部结构也只是失效,不会崩游戏。
 */
@Mixin(targets = "net.mehvahdjukaar.dummmmmmy.network.ClientBoundDamageNumberMessage", remap = false)
public abstract class DummyDamageNumberMixin {

    @Shadow @Final private int entityID;
    @Shadow @Final private float damageAmount;
    @Shadow @Final private boolean isCrit;

    @Inject(method = "handle", at = @At("HEAD"), require = 0)
    private void damageEngine$onDummyDamageNumber(CallbackInfo ci) {
        DummyDamageCompat.onDamageNumber(entityID, damageAmount, isCrit);
    }
}
