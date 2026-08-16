package damage.engine.compat.tacz;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * TACZ (Timeless and Classics Zero) compatibility helper.
 * Uses reflection since TACZ is a compileOnly dependency and must work on both
 * Forge and Fabric without hard class references.
 */
public class TaczCompat {

    private static final String BULLET_CLASS_NAME = "com.tacz.guns.entity.EntityKineticBullet";

    // Cache the Class lookup once TACZ is present; Class.forName on every hit is wasteful.
    private static volatile Class<?> cachedBulletClass = null;
    private static volatile boolean bulletClassResolved = false;

    private static Class<?> bulletClass() {
        if (bulletClassResolved) {
            return cachedBulletClass;
        }
        try {
            cachedBulletClass = Class.forName(BULLET_CLASS_NAME);
        } catch (ClassNotFoundException e) {
            cachedBulletClass = null;
        }
        bulletClassResolved = true;
        return cachedBulletClass;
    }

    /**
     * @return true if the given entity is a TACZ bullet.
     * TACZ bullets are regular Projectile subclasses in most versions, but this
     * reflection check keeps working even if a version deviates from that.
     * Does not depend on any optional TACZ API class (the Refabricated port does
     * not ship every API class), only on the bullet entity itself.
     */
    public static boolean isTaczBullet(Entity entity) {
        if (entity == null) return false;
        Class<?> bulletClass = bulletClass();
        if (bulletClass == null) return false;
        return bulletClass.isInstance(entity);
    }

    /**
     * Attempt to extract the gun owner from TACZ damage.
     *
     * @param directSource the direct damage source entity (e.g., bullet)
     * @return the shooter entity, or null if not TACZ damage or shooter not found
     */
    public static Entity tryGetTaczShooter(Entity directSource) {
        if (directSource == null) return null;
        Class<?> bulletClass = bulletClass();
        if (bulletClass == null) return null;
        try {
            if (bulletClass.isInstance(directSource)) {
                // EntityKineticBullet extends Projectile, so getOwner() works via the
                // standard API, but try the TACZ-specific method first for reliability.
                try {
                    var method = bulletClass.getMethod("getShooter");
                    Object shooter = method.invoke(directSource);
                    if (shooter instanceof LivingEntity) {
                        return (LivingEntity) shooter;
                    }
                } catch (NoSuchMethodException e) {
                    // Fall back to standard Projectile.getOwner() (handled by callers)
                }
            }
        } catch (Exception e) {
            // Reflection error, ignore
        }
        return null;
    }
}
