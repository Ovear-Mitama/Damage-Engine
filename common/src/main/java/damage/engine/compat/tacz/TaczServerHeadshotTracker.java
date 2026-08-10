package damage.engine.compat.tacz;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side tracker for TaCZ's official headshot result. The Fabric event
 * hook (TaczFabricCompat, subscribing to EntityHurtByGunEvent.PRE) marks the
 * victim here right before hurt(), and flushPendingDamage consumes the mark
 * once so the crit flag travels inside our own payload. Per-entity storage
 * keeps concurrent hits on different targets from overwriting each other. A
 * short time window guards against a stale mark being consumed by a later
 * body shot.
 */
public class TaczServerHeadshotTracker {

    private static final long WINDOW_MS = 500L;

    private static final Map<Integer, Long> HEADSHOTS = new ConcurrentHashMap<>();

    public static void markHeadshot(int entityId) {
        // Opportunistic cleanup: drop any entry whose window already expired so
        // stale marks cannot be consumed by a later unrelated hit, and the map
        // cannot grow unbounded if hurt() is cancelled before a flush consumes it.
        long now = System.currentTimeMillis();
        HEADSHOTS.entrySet().removeIf(e -> now - e.getValue() > WINDOW_MS);
        HEADSHOTS.put(entityId, now);
    }

    public static boolean consumeHeadshot(int entityId) {
        Long markTime = HEADSHOTS.remove(entityId);
        if (markTime == null) return false;
        return System.currentTimeMillis() - markTime <= WINDOW_MS;
    }
}
