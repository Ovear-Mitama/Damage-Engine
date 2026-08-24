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

        // Check if player recently attacked this entity
        boolean isSelfDamage = ClientAttackTracker.getInstance().wasRecentlyAttacked(entity.getId());

        // Handle global damage indicators (damage caused by others)
        if (!isSelfDamage && config.showGlobalDamageIndicator) {
            // Calculate distance for culling
            Minecraft client = Minecraft.getInstance();
            if (client.player != null) {
                // 分项设置:玩家使用玩家显示距离,非玩家实体按实体规则(注册名/All)
                Float maxDist = config.resolveGlobalIndicatorDistance(entity);
                if (maxDist == null) {
                    return; // 该受害实体被屏蔽或未配置显示
                }
                double dx = entity.getX() - client.player.getX();
                double dy = entity.getY() - client.player.getY();
                double dz = entity.getZ() - client.player.getZ();
                double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

                if (maxDist <= 0 || distance <= maxDist) {
                    Vec3 pos = DamageEngineClient.blendIndicatorPos(
                        entity.getX(),
                        entity.getY() + entity.getEyeHeight() * 0.6,
                        entity.getZ(),
                        entity.getId()
                    );

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
        Vec3 pos = DamageEngineClient.blendIndicatorPos(
            entity.getX(),
            entity.getY() + entity.getEyeHeight() * 0.6,
            entity.getZ(),
            entity.getId()
        );

        // Add damage indicator using blended position, capped by the configured max distance
        if (client.player != null) {
            double maxDist = config.globalIndicatorMaxDistance;
            if (maxDist <= 0 || pos.distanceToSqr(client.player.position()) <= maxDist * maxDist) {
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
    }
}
