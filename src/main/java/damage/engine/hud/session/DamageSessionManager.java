package damage.engine.hud.session;

import damage.engine.DamageEngineConfig;
import damage.engine.hud.rating.RatingManager;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class DamageSessionManager {
    private static final DamageSessionManager INSTANCE = new DamageSessionManager();

    private float totalDamage = 0;
    private int comboCount = 0;
    private long lastHitTime = 0;
    private int lastTargetEntityId = -1;
    private long infoExpireAt = 0;

    /** 一条伤害记录。 */
    public static final class DamageEntry {
        private final float damage;
        private final boolean isCrit;
        private final long timestamp;
        private final int attackerId;

        public DamageEntry(float damage, boolean isCrit, long timestamp, int attackerId) {
            this.damage = damage;
            this.isCrit = isCrit;
            this.timestamp = timestamp;
            this.attackerId = attackerId;
        }

        public float damage() { return damage; }
        public boolean isCrit() { return isCrit; }
        public long timestamp() { return timestamp; }
        public int attackerId() { return attackerId; }
    }
    private final List<DamageEntry> damageHistory = new CopyOnWriteArrayList<>();

    private boolean isActive = false;

    public static DamageSessionManager getInstance() {
        return INSTANCE;
    }

    public void addDamage(float amount, boolean isCrit) {
        addDamage(amount, isCrit, -1, false);
    }

    public void addDamage(float amount, boolean isCrit, int targetEntityId) {
        addDamage(amount, isCrit, targetEntityId, false);
    }

    public void addDamage(float amount, boolean isCrit, int targetEntityId, boolean preferSwitchTarget) {
        addDamage(amount, isCrit, targetEntityId, preferSwitchTarget, false, 0f, false);
    }

    public void addDamage(float amount, boolean isCrit, int targetEntityId, boolean preferSwitchTarget,
                          boolean killed, float distance, boolean unseen) {
        long now = System.currentTimeMillis();
        if (DamageEngineConfig.getInstance().resetEnabled && isActive && (now - lastHitTime) > DamageEngineConfig.getInstance().resetTime * 1000) {
            RatingManager.getInstance().endSession();
            resetDamageOnly();
        }

        isActive = true;
        totalDamage += amount;
        comboCount++;
        lastHitTime = now;

        RatingManager.getInstance().addHit(amount, isCrit, killed, distance, unseen);

        if (targetEntityId != -1) {
            if (!isInfoActive() || targetEntityId == lastTargetEntityId || preferSwitchTarget) {
                lastTargetEntityId = targetEntityId;
                infoExpireAt = now + (long)(DamageEngineConfig.getInstance().infoTrackTime * 1000);
            }
        }

        damageHistory.add(new DamageEntry(amount, isCrit, now, -1));
        // 多保留几条，让被挤出列表的那条有机会渐隐，而不是瞬间消失
        int limit = DamageEngineConfig.getInstance().historyLimit + 4;
        if (damageHistory.size() > limit) {
            damageHistory.remove(0);
        }
    }

    public void addOtherPlayerDamage(float amount, boolean isCrit, int attackerId) {
        // Respect the "record other players" setting: when disabled, don't record/show other players' damage
        if (!DamageEngineConfig.getInstance().recordOtherPlayers) return;
        long now = System.currentTimeMillis();
        isActive = true;
        lastHitTime = now;
        damageHistory.add(new DamageEntry(amount, isCrit, now, attackerId));
        int limit = DamageEngineConfig.getInstance().historyLimit + 4;
        if (damageHistory.size() > limit) {
            damageHistory.remove(0);
        }
    }

    public void tick() {
        long now = System.currentTimeMillis();

        // 历史记录清理与会话状态无关，避免会话结束后旧记录一直堆着
        long historyLifeTimeMs = (long) (DamageEngineConfig.getInstance().historyDisappearanceTime * 1000);
        damageHistory.removeIf(entry -> (now - entry.timestamp) > historyLifeTimeMs);

        if (!isActive) return;

        long resetTimeMs = (long) (DamageEngineConfig.getInstance().resetTime * 1000);
        if (DamageEngineConfig.getInstance().resetEnabled && (now - lastHitTime) > (resetTimeMs + 1000)) {
            RatingManager.getInstance().endSession();
            resetDamageOnly();
        }
    }

    public long getLastHitTime() { return lastHitTime; }

    public int getLastTargetEntityId() { return lastTargetEntityId; }

    public long getInfoExpireAt() { return infoExpireAt; }

    public boolean isInfoActive() { return System.currentTimeMillis() <= infoExpireAt; }

    public void clearInfo() {
        lastTargetEntityId = -1;
        infoExpireAt = 0;
    }

    /** 会话自动结束时保留伤害记录，让旧记录按自身寿命淡出，而不是瞬间消失。 */
    public void resetDamageOnly() {
        totalDamage = 0;
        comboCount = 0;
        isActive = false;
    }

    public void reset() {
        RatingManager.getInstance().endSession();
        resetDamageOnly();
        damageHistory.clear();
        clearInfo();
    }

    public float getTotalDamage() {
        return totalDamage;
    }

    public int getComboCount() {
        return comboCount;
    }

    public List<DamageEntry> getDamageHistory() {
        return damageHistory;
    }

    public float getRemainingTimeProgress() {
        if (!isActive) return 0f;
        if (!DamageEngineConfig.getInstance().resetEnabled) return 1.0f;
        long now = System.currentTimeMillis();
        long resetTimeMs = (long)(DamageEngineConfig.getInstance().resetTime * 1000);
        if (resetTimeMs <= 0) return 0f;
        long elapsed = now - lastHitTime;
        float progress = 1.0f - (float)elapsed / resetTimeMs;
        return Math.max(0, Math.min(1, progress));
    }

    public boolean isActive() {
        return isActive;
    }
}
