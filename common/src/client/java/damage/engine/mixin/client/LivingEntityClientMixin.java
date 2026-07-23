package damage.engine.mixin.client;

import damage.engine.client.ClientHealthTracker;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client-side mixin that monitors LivingEntity health changes.
 * Runs on every tick for living entities in the client world.
 * Only feeds data to ClientHealthTracker when in client-only mode.
 */
@Mixin(LivingEntity.class)
public class LivingEntityClientMixin {
    @Unique
    private float tracker$lastHealth = 0;
    @Unique
    private float tracker$lastAbsorption = 0;
    @Unique
    private boolean tracker$initialized = false;

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTickClientSide(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!self.level().isClientSide()) return;
        
        float h = self.getHealth();
        float a = self.getAbsorptionAmount();
        
        if (!tracker$initialized) {
            tracker$lastHealth = h;
            tracker$lastAbsorption = a;
            tracker$initialized = true;
            return;
        }
        
        float healthLost = tracker$lastHealth - h;
        float absorptionLost = tracker$lastAbsorption - a;
        float actualDamage = healthLost + absorptionLost;
        
        boolean killed = !self.isAlive() && (tracker$lastHealth > 0 || tracker$lastAbsorption > 0);
        
        if (actualDamage > 0.001f || killed) {
            float reportedDamage = killed ? Math.max(actualDamage, 0) : actualDamage;
            ClientHealthTracker.onEntityDamaged(self, reportedDamage, killed);
        }
        
        tracker$lastHealth = h;
        tracker$lastAbsorption = a;
    }
}
