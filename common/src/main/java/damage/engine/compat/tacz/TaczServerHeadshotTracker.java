package damage.engine.compat.tacz;

/**
 * Server-side tracker for TaCZ's official headshot result. A conditional mixin
 * (Fabric) or the forge event hook (Forge) marks the victim here, and
 * flushPendingDamage consumes the mark once so the crit flag travels inside our
 * own payload - exactly one headshot mark, consumed exactly once. A short time
 * window guards against the mark being set after a pending payload was already
 * flushed (multi-target ticks), so it can never leak into a later body shot.
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
