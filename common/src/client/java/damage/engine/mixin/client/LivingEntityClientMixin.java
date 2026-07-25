package damage.engine.mixin.client;

import damage.engine.client.ClientHealthTracker;
import damage.engine.DamageEngineClient;
import damage.engine.DamageEngineConfig;
import damage.engine.hud.DamageIndicator;
import net.minecraft.client.Minecraft;
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
    @Unique
    private long tracker$lastDamageTime = 0;

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
        
        // Only process damage when server does NOT have the mod (client-only mode).
        // When server has the mod, the server sends DamagePayload and we must NOT
        // also add indicators from the health tracker to avoid double indicators.
        if ((actualDamage > 0.001f || killed) && !DamageEngineClient.serverHasMod) {
            // Deduplication: skip if damage was detected within the last 50ms for the same entity.
            // This prevents duplicate indicators from rapid health syncs.
            long now = System.currentTimeMillis();
            if (now - tracker$lastDamageTime > 50) {
                float reportedDamage = killed ? Math.max(actualDamage, 0) : actualDamage;
                ClientHealthTracker.onEntityDamaged(self, reportedDamage, killed);
                tracker$lastDamageTime = now;
            }
        }
        
        // Detect healing (health increase) - show globally for all entities
        if (h > tracker$lastHealth + 0.001f) {
            float healAmount = h - tracker$lastHealth;
            DamageEngineConfig config = DamageEngineConfig.getInstance();
            if (config.showDamage && config.showHealIndicator) {
                // Check distance for culling
                Minecraft client = Minecraft.getInstance();
                if (client.player != null && client.level != null) {
                    double dx = self.getX() - client.player.getX();
                    double dy = self.getY() - client.player.getY();
                    double dz = self.getZ() - client.player.getZ();
                    double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    
                    // For heal indicators, use global indicator distance if configured, otherwise show all
                    float maxDistance = config.globalIndicatorMaxDistance;
                    if (distance <= maxDistance) {
                        double posX = self.getX();
                        double posY = self.getY() + self.getEyeHeight() * 0.6;
                        double posZ = self.getZ();
                        DamageIndicator.addIndicator(posX, posY, posZ, healAmount, false, false, true);
                    }
                }
            }
        }
        
        tracker$lastHealth = h;
        tracker$lastAbsorption = a;
    }
}
