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
        // Removed hudEnabled check here to decouple logic from rendering if needed later, 
        // but generally we can keep tracking even if hidden.
        
        long now = System.currentTimeMillis();
        // Check if session expired before adding new damage
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
        
        // Don't reset immediately if we want fade out.
        // Let HUD handle visual fade out, then we reset when fully faded?
        // Or we introduce a "fading" state.
        // Simpler: Extend active time by fade duration (e.g. 1s), but stop updating progress bar?
        
        // Let's just expose "time since last hit" and let HUD decide when to stop rendering.
        // We only reset data when time > resetTime + fadeTime.
        
        if ((now - lastHitTime) > (resetTimeMs + 1000)) { // 1s buffer for fade
            reset();
        }
        
        // Cleanup history (remove entries older than 5s)
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
