package damage.engine.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Resolves the world position where a floating damage indicator should spawn.
 *
 * <p>For hits caused by the player (e.g. TaCZ gunfire) the indicator should anchor
 * at the exact point the crosshair hit (foot, head, ...), not at the entity center
 * and not at the server-reported projectile position (which is unreliable: TaCZ
 * bullets can report a position at the shooter, and the clamped Y lands around the
 * torso).</p>
 *
 * <p>{@link Minecraft#hitResult} cannot be used because its pick ray is limited to
 * the survival reach (~6 blocks), far shorter than gun range. Instead we cast a long
 * ray from the player's eye along the current view vector and take its entry point
 * into the victim's bounding box.</p>
 */
public final class IndicatorPos {
    /** Ray length used for crosshair hit-point resolution (well beyond gun range). */
    private static final double RAY_RANGE = 512.0;
    /** Slight AABB inflation to mimic the projectile's collision box. */
    private static final double AABB_INFLATE = 0.2;

    private IndicatorPos() {}

    /**
     * @param baseX baseY baseZ server-reported fallback position (victim-side hit point)
     * @param entityId id of the damaged entity (client-level lookup for the crosshair ray)
     * @return the crosshair hit point on the victim, or the fallback position if it
     *         cannot be resolved
     */
    public static Vec3 blend(double baseX, double baseY, double baseZ, int entityId) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            return new Vec3(baseX, baseY, baseZ);
        }
        Entity target = client.level.getEntity(entityId);
        if (target != null) {
            Vec3 hit = crosshairHitPoint(client, target);
            if (hit != null) {
                return hit;
            }
        }
        return new Vec3(baseX, baseY, baseZ);
    }

    private static Vec3 crosshairHitPoint(Minecraft client, Entity target) {
        Vec3 eye = client.player.getEyePosition(1.0F);
        Vec3 look = client.player.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(RAY_RANGE));
        AABB box = target.getBoundingBox().inflate(AABB_INFLATE);
        return box.clip(eye, end).orElse(null);
    }
}
