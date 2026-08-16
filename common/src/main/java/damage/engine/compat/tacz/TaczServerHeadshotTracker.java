package damage.engine.compat.tacz;

/**
 * Server-side tracker for TaCZ's official headshot result. The Fabric event
 * hook (TaczFabricCompat, subscribing to EntityHurtByGunEvent.PRE) and the
 * Forge event hook (TaczForgeCompat) mark the victim here right before hurt(),
 * and flushPendingDamage consumes the mark once so the crit flag travels inside
 * our own payload. Per-entity storage keeps concurrent hits on different
 * targets from overwriting each other. A short time window guards against a
 * stale mark being consumed by a later body shot.
 */
public class TaczServerHeadshotTracker {

    private static final long WINDOW_MS = 500L;

    private static int pendingEntityId = -1;
    private static boolean pending = false;
    private static long markTime = 0L;

    public static void markHeadshot(int entityId) {
        pendingEntityId = entityId;
        pending = true;
        markTime = System.currentTimeMillis();
    }

    public static boolean consumeHeadshot(int entityId) {
        if (pending && System.currentTimeMillis() - markTime > WINDOW_MS) {
            pending = false;
            pendingEntityId = -1;
        }
        if (pending && entityId == pendingEntityId) {
            pending = false;
            pendingEntityId = -1;
            return true;
        }
        return false;
    }
}
