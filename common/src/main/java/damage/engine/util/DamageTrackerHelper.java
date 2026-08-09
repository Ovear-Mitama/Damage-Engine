package damage.engine.util;

import damage.engine.DamageEngineConfig;
import damage.engine.network.DamagePayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.Mth;
import org.slf4j.Logger;

/**
 * Shared damage tracking logic used by both server-side mixins and events.
 */
public class DamageTrackerHelper {

    public record Snapshot(float prevHealth, float prevAbsorption, boolean wasCrit,
                           int attackerId, double srcX, double srcY, double srcZ, boolean isProjectile,
                           float sourceAmount) {}

    /**
     * Functional interface for sending damage payloads. Each platform (Forge/Fabric) provides its own implementation.
     */
    @FunctionalInterface
    public interface DamageBroadcaster {
        void broadcast(DamagePayload payload, ServerLevel level);
    }

    private static DamageBroadcaster broadcaster = null;

    public static void setBroadcaster(DamageBroadcaster b) {
        broadcaster = b;
    }

    // ---- Pending-damage merge (TACZ fires hurt() twice per hit: armor + non-armor
    // ---- sources). Same tick + same direct source + same target => merge into one
    // ---- broadcast so players see a single damage float / history entry. ----
    private static int pendingTick = -1;
    private static int pendingTarget = -1;
    private static int pendingDirect = -1;
    private static boolean pending = false;
    private static ServerLevel pendingLevel = null;
    private static float pendingDamage = 0f;
    private static boolean pendingCrit = false;
    private static boolean pendingKilled = false;
    private static int pendingAttackerId = -1;
    private static String pendingDebug = "";
    private static double pendingX, pendingY, pendingZ;
    private static boolean pendingProjectile = false;

    /**
     * Flushes the pending (merged) damage payload if any. Called from platform
     * server-tick hooks so the last hit of a session is never lost.
     */
    public static void flushPendingDamage() {
        if (!pending || broadcaster == null || pendingLevel == null) return;
        // TaCZ headshot: the conditional mixin marks the victim when TaCZ
        // constructs ServerMessageGunHurt (right after hurt(), before the tick-end
        // flush). Consume the mark here so the crit flag rides this exact payload.
        if (damage.engine.compat.tacz.TaczServerHeadshotTracker.consumeHeadshot(pendingTarget)) {
            pendingCrit = true;
        }
        DamagePayload payload = new DamagePayload(pendingTarget, pendingDamage, pendingCrit,
            pendingAttackerId, pendingDebug,
            pendingX, pendingY, pendingZ, pendingProjectile, pendingKilled);
        broadcaster.broadcast(payload, pendingLevel);
        pending = false;
        pendingLevel = null;
    }

    /**
     * Optional hook for mod compat. Called during capturePreDamage to allow
     * external mods (e.g., TACZ) to provide additional attacker resolution.
     */
    @FunctionalInterface
    public interface AttackerResolver {
        /** @return the resolved attacker Entity or null */
        Entity resolve(LivingEntity victim, Entity directSource, DamageSource source);
    }

    private static AttackerResolver attackerResolver = null;

    public static void setAttackerResolver(AttackerResolver resolver) {
        attackerResolver = resolver;
    }

    /**
     * Captures the entity's state before damage is applied.
     */
    public static Snapshot capturePreDamage(LivingEntity self, DamageSource source, float amount) {
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

        // Mod compat hook: allow external mods (e.g. TACZ) to resolve attacker
        if (attacker == null && attackerResolver != null) {
            attacker = attackerResolver.resolve(self, directSource, source);
        }

        if (attacker != null) {
            attackerId = attacker.getId();
        }

        boolean isProjectile = false;
        double srcX, srcY, srcZ;

        // Treat any projectile as a "bow-like" hit: TACZ bullets are standard
        // Projectile subclasses, but we also explicitly detect them via reflection
        // so guns always use the same handling as bows even if a TACZ version
        // deviates from the standard Projectile hierarchy.
        boolean projectileLike = (directSource instanceof Projectile && !(directSource instanceof ThrownPotion))
            || damage.engine.compat.tacz.TaczCompat.isTaczBullet(directSource);

        if (projectileLike) {
            isProjectile = true;
            // Clamp the projectile hit point into the victim's AABB. TACZ bullets can
            // report a server position away from the victim (e.g. right at the
            // shooter), which made floats appear on the player; clamping keeps the
            // float on the victim no matter what.
            AABB box = self.getBoundingBox();
            srcX = Mth.clamp(directSource.getX(), box.minX, box.maxX);
            srcY = Mth.clamp(directSource.getY(), box.minY, box.maxY);
            srcZ = Mth.clamp(directSource.getZ(), box.minZ, box.maxZ);
        } else if (attacker != null) {
            AABB box = self.getBoundingBox();
            srcX = Mth.clamp(attacker.getX(), box.minX, box.maxX);
            srcY = (box.minY + box.maxY) * 0.5;
            srcZ = Mth.clamp(attacker.getZ(), box.minZ, box.maxZ);
        } else {
            AABB box = self.getBoundingBox();
            srcX = self.getX();
            srcY = (box.minY + box.maxY) * 0.5;
            srcZ = self.getZ();
        }

        return new Snapshot(prevHealth, prevAbsorption, wasCrit, attackerId, srcX, srcY, srcZ, isProjectile, amount);
    }

    /**
     * Calculates actual damage and broadcasts the payload after damage is applied.
     * @param hurtAccepted whether the entity's hurt() accepted the damage (returned true)
     */
    public static void broadcastPostDamage(LivingEntity self, DamageSource source, Snapshot snap, Logger logger, boolean hurtAccepted) {
        float currentHealth = self.getHealth();
        float currentAbsorption = self.getAbsorptionAmount();

        float healthLost = snap.prevHealth - currentHealth;
        float absorptionLost = snap.prevAbsorption - currentAbsorption;
        float actualDamage = healthLost + absorptionLost;

        // Some entities (e.g. MmmMmmMmmMmm dummies) intercept setHealth and never
        // actually lose health, even though hurt() returned true. Fall back to the
        // source amount so damage floats still appear for them.
        if (actualDamage <= 0f && hurtAccepted) {
            actualDamage = snap.sourceAmount;
        }

        // Crit is determined server-side (melee crit). TaCZ headshots are consumed
        // in flushPendingDamage() - TaCZ marks them when it constructs
        // ServerMessageGunHurt (after hurt(), before the tick-end flush), so the
        // mark is available exactly when we send our payload.
        boolean crit = snap.wasCrit;

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

        boolean killed = (!self.isAlive() || self.getHealth() <= 0f) && snap.prevHealth > 0f;

        if ((actualDamage > 0 || killed) && broadcaster != null && self.level() instanceof ServerLevel sw) {
            int tick = (int) sw.getGameTime();
            int directId = source.getDirectEntity() != null ? source.getDirectEntity().getId() : -1;
            int targetId = self.getId();

            // Same tick + same direct source + same target: this is the second (or
            // later) hurt() of the same hit (TACZ armor/non-armor double-hit).
            // Merge the damage into the pending payload instead of broadcasting
            // duplicates.
            if (pending && pendingTick == tick && pendingTarget == targetId && pendingDirect == directId) {
                pendingDamage += actualDamage;
                pendingCrit |= crit;
                pendingKilled |= killed;
                return;
            }

            // Different hit: flush whatever was pending, then start a new one.
            flushPendingDamage();
            pending = true;
            pendingTick = tick;
            pendingTarget = targetId;
            pendingDirect = directId;
            pendingLevel = sw;
            pendingDamage = actualDamage;
            pendingCrit = crit;
            pendingKilled = killed;
            pendingAttackerId = snap.attackerId;
            pendingDebug = debugInfo;
            pendingX = snap.srcX;
            pendingY = snap.srcY;
            pendingZ = snap.srcZ;
            pendingProjectile = snap.isProjectile;
        }
    }
}
