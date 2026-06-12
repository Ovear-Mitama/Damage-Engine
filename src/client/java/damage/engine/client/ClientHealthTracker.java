package damage.engine.client;

import damage.engine.DamageEngineClient;
import damage.engine.DamageEngineConfig;
import damage.engine.hud.DamageIndicator;
import damage.engine.hud.DamageSessionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

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
        
        // Check if entity is in crosshair for info panel switching
        boolean preferSwitchTarget = false;
        Minecraft client = Minecraft.getInstance();
        if (client.hitResult instanceof EntityHitResult ehr && ehr.getEntity() != null) {
            preferSwitchTarget = ehr.getEntity().getId() == entity.getId();
        }
        
        // In client-only mode, we cannot identify the attacker, so we process all damage
        // as if it were dealt by the local player (best-effort approximation)
        DamageSessionManager.getInstance().addDamage(damageAmount, false, entity.getId(), preferSwitchTarget);
        
        // Blend indicator position toward crosshair hit point
        Vec3 pos = DamageEngineClient.blendIndicatorPos(
            entity.getX(), 
            entity.getY() + entity.getEyeHeight() * 0.6, 
            entity.getZ(),
            entity.getId()
        );
        
        // Add damage indicator using blended position
        if (config.showDamageIndicator && damageAmount > 0) {
            DamageIndicator.addIndicator(pos.x, pos.y, pos.z,
                damageAmount, 
                false,  // Cannot detect crit in client-only mode
                false
            );
        }
        
        if (config.showKillIndicator && killed) {
            DamageIndicator.addIndicator(pos.x, pos.y, pos.z,
                damageAmount,
                false,
                true
            );
        }
    }
}
