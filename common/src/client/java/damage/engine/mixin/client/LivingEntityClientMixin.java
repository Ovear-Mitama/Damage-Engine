package damage.engine.mixin.client;

import damage.engine.client.ClientHealthMonitor;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client-side mixin that monitors LivingEntity health changes.
 * Runs on every tick for living entities in the client world.
 * Only feeds data to ClientHealthTracker when in client-only mode.
 * Per-entity state lives in {@link ClientHealthMonitor} so the same logic can be
 * driven by Forge's LivingTickEvent (Forge 1.20.1 cannot mix into LivingEntity).
 */
@Mixin(LivingEntity.class)
public class LivingEntityClientMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTickClientSide(CallbackInfo ci) {
        ClientHealthMonitor.onTick((LivingEntity) (Object) this);
    }
}
