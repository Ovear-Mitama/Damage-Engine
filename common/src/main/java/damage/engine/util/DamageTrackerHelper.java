package damage.engine.util;

import damage.engine.DamageEngineConfig;
import damage.engine.network.DamagePayload;
import damage.engine.util.AttackerAccessor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.Mth;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import org.slf4j.Logger;

/**
 * Shared damage tracking logic used by both LivingEntityMixin and PlayerEntityMixin.
 */
public class DamageTrackerHelper {

    public record Snapshot(float prevHealth, float prevAbsorption, boolean wasCrit,
                           int attackerId, double srcX, double srcY, double srcZ, boolean isProjectile) {}

    /**
     * Captures the entity's state before damage is applied.
     */
    public static Snapshot capturePreDamage(LivingEntity self, DamageSource source) {
        float prevHealth = self.getHealth();
        float prevAbsorption = self.getAbsorptionAmount();

        boolean wasCrit = false;
        if (source.getEntity() instanceof Player attacker) {
            if (attacker.fallDistance > 0.0F && !attacker.onGround()
                && !attacker.onClimbable() && !attacker.isInWater()) {
                wasCrit = true;
            }
        }

        int attackerId = -1;
        Entity attacker = source.getEntity();
        Entity directSource = source.getDirectEntity();

        // For projectiles, use the owner as the real attacker
        if (attacker instanceof Projectile proj) {
            Entity owner = proj.getOwner();
            if (owner != null) {
                attacker = owner;
            }
        }

        if (attacker == null) {
            if (directSource instanceof OwnableEntity ownable) {
                attacker = ownable.getOwner();
            } else if (directSource instanceof net.minecraft.world.entity.boss.enderdragon.EndCrystal crystal) {
                if (crystal instanceof AttackerAccessor accessor) {
                    attacker = accessor.damageEngine$getAttacker();
                }
            }
        }

        if (attacker == null && (source.is(DamageTypes.ON_FIRE) || source.is(DamageTypes.IN_FIRE))) {
            attacker = self.getLastAttacker();
        }

        if (attacker != null) {
            attackerId = attacker.getId();
        }

        boolean isProjectile = false;
        double srcX, srcY, srcZ;

        if (directSource instanceof Projectile) {
            isProjectile = true;
            srcX = directSource.getX();
            srcY = directSource.getY();
            srcZ = directSource.getZ();
        } else if (attacker != null) {
            AABB box = self.getBoundingBox();
            srcX = Mth.clamp(attacker.getX(), box.minX, box.maxX);
            srcY = Mth.clamp(attacker.getEyeY(), box.minY, box.maxY);
            srcZ = Mth.clamp(attacker.getZ(), box.minZ, box.maxZ);
        } else {
            srcX = self.getX();
            srcY = self.getY() + self.getEyeHeight() * 0.6;
            srcZ = self.getZ();
        }

        return new Snapshot(prevHealth, prevAbsorption, wasCrit, attackerId, srcX, srcY, srcZ, isProjectile);
    }

    /**
     * Calculates actual damage and broadcasts the payload after damage is applied.
     */
    public static void broadcastPostDamage(LivingEntity self, DamageSource source, Snapshot snap, Logger logger) {
        float currentHealth = self.getHealth();
        float currentAbsorption = self.getAbsorptionAmount();

        float healthLost = snap.prevHealth - currentHealth;
        float absorptionLost = snap.prevAbsorption - currentAbsorption;
        float actualDamage = healthLost + absorptionLost;

        String debugInfo = "";
        if (DamageEngineConfig.getInstance().debugMode) {
            String attackerName = source.type().msgId();
            if (snap.attackerId != 0 && self.level() instanceof ServerLevel serverWorld) {
                Entity attacker = serverWorld.getEntity(snap.attackerId);
                if (attacker != null) {
                    attackerName = attacker.getName().getString();
                }
            }
            debugInfo = String.format("%s 造成伤害%.1f 伤害对象 %s",
                attackerName, actualDamage, self.getName().getString());
            logger.info(debugInfo);
        }

        boolean killed = !self.isAlive() || self.getHealth() <= 0f;

        if (actualDamage > 0 || killed) {
            DamagePayload payload = new DamagePayload(self.getId(), actualDamage, snap.wasCrit,
                snap.attackerId, debugInfo,
                snap.srcX, snap.srcY, snap.srcZ, snap.isProjectile, killed);

            ClientboundCustomPayloadPacket packet = new ClientboundCustomPayloadPacket(payload);
            ServerLevel sw = (ServerLevel) self.level();
            for (ServerPlayer player : sw.players()) {
                player.connection.send(packet);
            }
        }
    }
}