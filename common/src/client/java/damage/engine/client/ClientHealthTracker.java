package damage.engine.client;

import damage.engine.DamageEngineClient;
import damage.engine.DamageEngineConfig;
import damage.engine.hud.indicator.DamageIndicator;
import damage.engine.hud.session.DamageSessionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Client-side health tracker that detects damage by monitoring entity health changes.
 * Only active when the server does NOT have Damage Engine installed (client-only mode).
 * 
 * Limitations in client-only mode:
 * - Cannot identify the real attacker; only entities the player recently attacked
 *   (tracked by {@link ClientAttackTracker}) are counted, other damage is ignored
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

        // 客户端降级模式无法判断真实攻击者：玩家最近攻击过的实体计入自己的统计，
        // 其余伤害只在开启「全图跳字」时显示跳字，不进入伤害会话/评分/信息面板。
        if (!ClientAttackTracker.getInstance().wasRecentlyAttacked(entity.getId())) {
            showGlobalIndicator(config, entity, damageAmount, killed);
            return;
        }

        // Check if entity is in crosshair for info panel switching
        boolean preferSwitchTarget;
        Minecraft client = Minecraft.getInstance();
        if (client.hitResult instanceof EntityHitResult ehr && ehr.getEntity() != null) {
            preferSwitchTarget = ehr.getEntity().getId() == entity.getId();
        } else {
            // 没有准心实体（远程/爆炸等）：直接切换到本次受伤的目标
            preferSwitchTarget = true;
        }

        float distance = client.player != null
            ? damage.engine.hud.rating.CombatContext.distance(client.player, entity) : 0f;
        boolean unseen = killed && client.player != null
            && damage.engine.hud.rating.CombatContext.isHiddenFromPlayer(entity, client.player);
        DamageSessionManager.getInstance().addDamage(damageAmount, false, entity.getId(),
            preferSwitchTarget, killed, distance, unseen, true);

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
                    DamageIndicator.addIndicator(entity, pos.x, pos.y, pos.z,
                        damageAmount,
                        false,  // Cannot detect crit in client-only mode
                        false
                    );
                }

                if (config.showKillIndicator && killed) {
                    DamageIndicator.addIndicator(entity, pos.x, pos.y, pos.z,
                        damageAmount,
                        false,
                        true
                    );
                }
            }
        }
    }

    /**
     * 全图跳字：非玩家造成的伤害只显示跳字，不进入伤害会话/评分/信息面板。
     * 距离、实体规则与感知过滤与服务端模式保持一致（见 {@link GlobalDamageFilter}）。
     */
    private static void showGlobalIndicator(DamageEngineConfig config, LivingEntity entity,
                                            float damageAmount, boolean killed) {
        if (!config.showGlobalDamageIndicator) return;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;

        Vec3 pos = DamageEngineClient.blendIndicatorPos(
            entity.getX(), entity.getY() + entity.getEyeHeight() * 0.6, entity.getZ(), entity.getId());
        if (!GlobalDamageFilter.shouldShow(config, client, entity, pos.x, pos.y, pos.z)) return;

        if (config.showDamageIndicator && damageAmount > 0) {
            DamageIndicator.addIndicator(entity, pos.x, pos.y, pos.z,
                damageAmount,
                false,  // Cannot detect crit in client-only mode
                false
            );
        }
        if (config.showKillIndicator && killed) {
            DamageIndicator.addIndicator(entity, pos.x, pos.y, pos.z,
                damageAmount,
                false,
                true
            );
        }
    }
}
