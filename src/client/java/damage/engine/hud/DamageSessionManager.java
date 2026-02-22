package damage.engine.hud;

import damage.engine.DamageEngineConfig;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class DamageSessionManager {
    private static final DamageSessionManager INSTANCE = new DamageSessionManager();
    
    private float totalDamage = 0;
    private int comboCount = 0;
    private long lastHitTime = 0;
    
    public record DamageEntry(float damage, boolean isCrit, long timestamp) {}
    private final List<DamageEntry> damageHistory = new CopyOnWriteArrayList<>();
    
    private boolean isActive = false;

    public static DamageSessionManager getInstance() {
        return INSTANCE;
    }

    public void addDamage(float amount, boolean isCrit) {
        // 此处移除了 hudEnabled 检查，以便在需要时将逻辑与渲染解耦，
        // 但通常即使隐藏，我们也可以保持跟踪。
        
        long now = System.currentTimeMillis();
        // 在添加新伤害之前检查会话是否过期
        if (isActive && (now - lastHitTime) > DamageEngineConfig.getInstance().resetTime * 1000) {
            reset();
        }

        isActive = true;
        totalDamage += amount;
        comboCount++;
        lastHitTime = now;
        
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
        

        
        if ((now - lastHitTime) > (resetTimeMs + 1000)) { // 1秒淡出缓冲
            reset();
        }
        
        // 清理历史记录（根据配置的时间）
        long historyLifeTimeMs = (long)(DamageEngineConfig.getInstance().historyDisappearanceTime * 1000);
        damageHistory.removeIf(entry -> (now - entry.timestamp) > historyLifeTimeMs);
    }
    
    public long getLastHitTime() { return lastHitTime; }
    
    public void reset() {
        totalDamage = 0;
        comboCount = 0;
        damageHistory.clear();
        isActive = false;
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
        long now = System.currentTimeMillis();
        long resetTimeMs = (long)(DamageEngineConfig.getInstance().resetTime * 1000);
        long elapsed = now - lastHitTime;
        float progress = 1.0f - (float)elapsed / resetTimeMs;
        return Math.max(0, Math.min(1, progress));
    }
    
    public boolean isActive() {
        return isActive;
    }
}
