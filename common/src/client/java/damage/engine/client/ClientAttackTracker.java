package damage.engine.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks entities the local player recently attacked (melee or projectile).
 * Used by ClientHealthTracker to filter out damage not caused by the player
 * in client-only mode (server without the mod).
 */
public class ClientAttackTracker {
    private static final ClientAttackTracker INSTANCE = new ClientAttackTracker();

    private final Map<Integer, Long> recentAttacks = new ConcurrentHashMap<>();

    private static final long ATTACK_MEMORY_MS = 2000;

    public static ClientAttackTracker getInstance() {
        return INSTANCE;
    }

    public void recordAttack(int entityId) {
        recentAttacks.put(entityId, System.currentTimeMillis());
    }

    public boolean wasRecentlyAttacked(int entityId) {
        Long time = recentAttacks.get(entityId);
        if (time == null) return false;
        if (System.currentTimeMillis() - time > ATTACK_MEMORY_MS) {
            recentAttacks.remove(entityId);
            return false;
        }
        return true;
    }

    public void cleanup() {
        long now = System.currentTimeMillis();
        recentAttacks.entrySet().removeIf(e -> now - e.getValue() > ATTACK_MEMORY_MS);
    }
}
