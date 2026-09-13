package damage.engine.hud;

import damage.engine.DamageEngineConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class DamageSessionManager {
    private static final DamageSessionManager INSTANCE = new DamageSessionManager();

    private float totalDamage = 0;
    private int comboCount = 0;
    private long lastHitTime = 0;
    private int lastTargetEntityId = -1;
    private long infoExpireAt = 0;

    public record DamageEntry(float damage, boolean isCrit, long timestamp, int attackerId) {}
    private final List<DamageEntry> damageHistory = new CopyOnWriteArrayList<>();

    private boolean isActive = false;

    public static DamageSessionManager getInstance() {
        return INSTANCE;
    }

    /**
     * 判断准星命中的实体是否就是本次伤害的受害者。
     * <p>
     * 末影龙的碰撞箱由 {@link EnderDragonPart} 部件实体组成:准星命中的是部件,而伤害最终归属到本体,
     * 两者 id 不同。这里做一次映射,否则打了别的生物后再打龙时无法切换信息面板目标。
     */
    public static boolean isSameVictim(Entity crosshairEntity, int victimId) {
        if (crosshairEntity == null) return false;
        if (crosshairEntity.getId() == victimId) return true;
        return crosshairEntity instanceof EnderDragonPart part
            && part.parentMob != null && part.parentMob.getId() == victimId;
    }

    public void addDamage(float amount, boolean isCrit) {
        addDamage(amount, isCrit, -1, null);
    }

    public void addDamage(float amount, boolean isCrit, int targetEntityId) {
        addDamage(amount, isCrit, targetEntityId, null);
    }

    /**
     * 记录一次伤害。
     *
     * @param crosshairEntity 准星当前命中的实体(可为 null);命中本次受害者时会强制切换信息面板目标
     */
    public void addDamage(float amount, boolean isCrit, int targetEntityId, Entity crosshairEntity) {
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
            if (!isInfoActive() || targetEntityId == lastTargetEntityId || isSameVictim(crosshairEntity, targetEntityId)) {
                lastTargetEntityId = targetEntityId;
                infoExpireAt = now + (long)(DamageEngineConfig.getInstance().infoTrackTime * 1000);
            }
        }

        damageHistory.add(new DamageEntry(amount, isCrit, now, -1));
        int limit = DamageEngineConfig.getInstance().historyLimit;
        if (damageHistory.size() > limit) {
            damageHistory.remove(0);
        }
    }

    public void addOtherPlayerDamage(float amount, boolean isCrit, int attackerId) {
        long now = System.currentTimeMillis();
        damageHistory.add(new DamageEntry(amount, isCrit, now, attackerId));
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
