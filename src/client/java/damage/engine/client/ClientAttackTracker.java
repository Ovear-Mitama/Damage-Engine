package damage.engine.client;

import java.util.HashMap;
import java.util.Map;

/**
 * Tracks entities that the local player has recently attacked.
 * Used by ClientHealthTracker to filter damage events so that only
 * entities the player actually attacked are counted.
 */
public class ClientAttackTracker {
    private static final ClientAttackTracker INSTANCE = new ClientAttackTracker();
    private static final long ATTACK_TIMEOUT_MS = 2000;

    private final Map<Integer, Long> recentlyAttacked = new HashMap<>();

    public static ClientAttackTracker getInstance() {
        return INSTANCE;
    }

    public void recordAttack(int entityId) {
        recentlyAttacked.put(entityId, System.currentTimeMillis());
    }

    public boolean wasRecentlyAttacked(int entityId) {
        Long time = recentlyAttacked.get(entityId);
        if (time == null) return false;
        if (System.currentTimeMillis() - time > ATTACK_TIMEOUT_MS) {
            recentlyAttacked.remove(entityId);
            return false;
        }
        return true;
    }

    public void cleanup() {
        long now = System.currentTimeMillis();
        recentlyAttacked.entrySet().removeIf(e -> now - e.getValue() > ATTACK_TIMEOUT_MS);
    }
}
