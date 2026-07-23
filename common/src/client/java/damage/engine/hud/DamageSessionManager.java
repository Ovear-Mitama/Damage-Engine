package damage.engine.hud;

import damage.engine.DamageEngineConfig;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class DamageSessionManager {
    private static final DamageSessionManager INSTANCE = new DamageSessionManager();

    private float totalDamage = 0;
    private int comboCount = 0;
    private long lastHitTime = 0;
    private int lastTargetEntityId = -1;
    private long infoExpireAt = 0;

    public record DamageEntry(float damage, boolean isCrit, long timestamp) {}
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
        long now = System.currentTimeMillis();
        if (DamageEngineConfig.getInstance().resetEnabled && isActive && (now - lastHitTime) > DamageEngineConfig.getInstance().resetTime * 1000) {
            RatingManager.getInstance().endSession();
            resetDamageOnly();
        }

        isActive = true;
        totalDamage += amount;
        comboCount++;
        lastHitTime = now;

        RatingManager.getInstance().addHit(amount, isCrit);

        if (targetEntityId != -1) {
            if (!isInfoActive() || targetEntityId == lastTargetEntityId || preferSwitchTarget) {
                lastTargetEntityId = targetEntityId;
                infoExpireAt = now + (long)(DamageEngineConfig.getInstance().infoTrackTime * 1000);
            }
        }

        damageHistory.add(new DamageEntry(amount, isCrit, now));
        int limit = DamageEngineConfig.getInstance().historyLimit;
        if (damageHistory.size() > limit) {
            damageHistory.remove(0);
        }
    }

    public void tick() {
        if (!isActive) return;

        long now = System.currentTimeMillis();
        long resetTimeMs = (long)(DamageEngineConfig.getInstance().resetTime * 1000);



        if (DamageEngineConfig.getInstance().resetEnabled && (now - lastHitTime) > (resetTimeMs + 1000)) {
            RatingManager.getInstance().endSession();
            resetDamageOnly();
        }

        // 清理历史记录（根据配置的时间）
        long historyLifeTimeMs = (long)(DamageEngineConfig.getInstance().historyDisappearanceTime * 1000);
        damageHistory.removeIf(entry -> (now - entry.timestamp) > historyLifeTimeMs);
    }

    public long getLastHitTime() { return lastHitTime; }

    public int getLastTargetEntityId() { return lastTargetEntityId; }

    public long getInfoExpireAt() { return infoExpireAt; }

    public boolean isInfoActive() { return System.currentTimeMillis() <= infoExpireAt; }

    public void clearInfo() {
        lastTargetEntityId = -1;
        infoExpireAt = 0;
    }

    public void resetDamageOnly() {
        totalDamage = 0;
        comboCount = 0;
        damageHistory.clear();
        isActive = false;
    }

    public void reset() {
        RatingManager.getInstance().endSession();
        resetDamageOnly();
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
