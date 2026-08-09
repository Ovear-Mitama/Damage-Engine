package damage.engine.mixin;

import com.tacz.guns.network.message.event.ServerMessageGunHurt;
import damage.engine.compat.tacz.TaczServerHeadshotTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reads TaCZ's official headshot result from the ServerMessageGunHurt packet.
 * The 8-arg constructor is used on the SERVER side when TaCZ broadcasts the
 * packet after a bullet hit; we mark the victim so our own damage payload for
 * the same hit carries the crit flag (consumed once, exactly once). Lives in a
 * conditional mixin config that only applies when TaCZ is loaded.
 */
@Mixin(ServerMessageGunHurt.class)
public class TaczGunHurtMixin {

    @Shadow
    private boolean isHeadShot;

    @Shadow
    private int hurtEntityId;

    @Inject(method = "<init>(IIILnet/minecraft/resources/ResourceLocation;Lnet/minecraft/resources/ResourceLocation;FZF)V", at = @At("RETURN"))
    private void damageEngine$markServerHeadshot(CallbackInfo ci) {
        if (isHeadShot) {
            TaczServerHeadshotTracker.markHeadshot(hurtEntityId);
        }
    }
}
