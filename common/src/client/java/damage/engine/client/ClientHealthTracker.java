package damage.engine.client;

import damage.engine.DamageEngineClient;
import damage.engine.DamageEngineConfig;
import damage.engine.hud.DamageIndicator;
import damage.engine.hud.DamageSessionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Client-side health tracker that detects damage by monitoring entity health changes.
 * Only active when the server does NOT have Damage Engine installed (client-only mode).
 * 
 * Limitations in client-only mode:
 * - Cannot identify attacker (cannot filter for player's own damage)
 * - Cannot detect critical hits
 * - Cannot detect projectile vs. melee attacks
 * - Damage indicator position is approximate (entity position)
 */
public class ClientHealthTracker {

    /**
     * Called by the client mixin when a living entity's health or absorption changes.
     * Only processes damage when in confirmed client-only mode.
     */
    public static void onEntityDamaged(LivingEntity entity, float damageAmount, boolean killed) {
        if (!DamageEngineClient.serverModChecked || DamageEngineClient.serverHasMod) {
            return;
        }

        DamageEngineConfig config = DamageEngineConfig.getInstance();
        if (!config.showDamage) return;

        // Check if player recently attacked this entity
        boolean isSelfDamage = ClientAttackTracker.getInstance().wasRecentlyAttacked(entity.getId());

        // Handle global damage indicators (damage caused by others)
        if (!isSelfDamage && config.showGlobalDamageIndicator) {
            // Calculate distance for culling
            Minecraft client = Minecraft.getInstance();
            if (client.player != null) {
                double dx = entity.getX() - client.player.getX();
                double dy = entity.getY() - client.player.getY();
                double dz = entity.getZ() - client.player.getZ();
                double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

                if (distance <= config.globalIndicatorMaxDistance) {
                    if (config.showDamageIndicator && damageAmount > 0) {
                        DamageIndicator.addIndicator(entity.getId(), entity.getX(),
                            entity.getY() + entity.getEyeHeight() * 0.6, entity.getZ(),
                            damageAmount, false, killed);
                    }

                    if (config.showKillIndicator && killed) {
                        DamageIndicator.addIndicator(entity.getId(), entity.getX(),
                            entity.getY() + entity.getEyeHeight() * 0.6, entity.getZ(),
                            damageAmount, false, true);
                    }
                }
            }
            return; // Don't process further for global damage
        }

        // Only track damage for entities the player recently attacked (melee or projectile)
        if (!isSelfDamage) {
            return;
        }

        // Check if entity is in crosshair for info panel switching
        boolean preferSwitchTarget = false;
        Minecraft client = Minecraft.getInstance();
        if (client.hitResult instanceof EntityHitResult ehr && ehr.getEntity() != null) {
            preferSwitchTarget = ehr.getEntity().getId() == entity.getId();
        }

        DamageSessionManager.getInstance().addDamage(damageAmount, false, entity.getId(), preferSwitchTarget);

        // Blend indicator position toward crosshair hit point
        if (config.showDamageIndicator && damageAmount > 0) {
            DamageIndicator.addIndicator(entity.getId(), entity.getX(),
                entity.getY() + entity.getEyeHeight() * 0.6, entity.getZ(),
                damageAmount, false, killed);
        }

        if (config.showKillIndicator && killed) {
            DamageIndicator.addIndicator(entity.getId(), entity.getX(),
                entity.getY() + entity.getEyeHeight() * 0.6, entity.getZ(),
                damageAmount, false, true);
        }
    }
}
