package damage.engine.hud;

import damage.engine.DamageEngineConfig;
import java.util.ArrayList;
import java.util.List;

public class DamageSessionManager {
    private static final DamageSessionManager INSTANCE = new DamageSessionManager();
    
    private float totalDamage = 0;
    private int comboCount = 0;
    private long lastHitTime = 0;
    
    public record DamageEntry(float damage, boolean isCrit, long timestamp) {}
    private final List<DamageEntry> damageHistory = new ArrayList<>();
    
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
        if (damageHistory.size() > 8) {
            damageHistory.remove(0);
        }
    }

    public void tick() {
        if (!isActive) return;

        long now = System.currentTimeMillis();
        long resetTimeMs = (long)(DamageEngineConfig.getInstance().resetTime * 1000);
        
        // 如果我们想要淡出，不要立即重置。
        // 让 HUD 处理视觉淡出，然后在完全淡出时重置？
        // 或者我们引入一个'正在淡出'的状态。
        // 更简单：将会话时间延长淡出持续时间（例如 1 秒），但停止更新进度条？
        
        // 让我们只暴露'自上次攻击以来的时间'，让 HUD 决定何时停止渲染。
        // 我们仅在时间 > 重置时间 + 淡出时间时重置数据。
        
        if ((now - lastHitTime) > (resetTimeMs + 1000)) { // 1秒淡出缓冲
            reset();
        }
        
        // 清理历史记录（删除超过 5 秒的条目）
        damageHistory.removeIf(entry -> (now - entry.timestamp) > 5000);
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
