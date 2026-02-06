package damage.engine.hud;

import damage.engine.DamageEngineConfig;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

import java.util.List;

public class DamageHud implements HudRenderCallback {
    private float smoothProgress = 0f;
    private boolean isRefilling = false;
    private int lastComboCount = 0;

    @Override
    public void onHudRender(DrawContext context, float tickDelta) {
        if (!DamageEngineConfig.getInstance().showDamage) return;
        
        DamageSessionManager session = DamageSessionManager.getInstance();
        if (!session.isActive()) {
            lastComboCount = 0;
            return;
        }
        
        // Prevent flashing of empty session
        if (session.getTotalDamage() <= 0 && !session.getDamageHistory().isEmpty()) {
             // This might happen if session reset but history lingers? 
             // Just a safety check.
        }

        long now = System.currentTimeMillis();
        long timeSinceLast = now - session.getLastHitTime();
        long resetTimeMs = (long)(DamageEngineConfig.getInstance().resetTime * 1000);
        
        float globalAlpha = 1.0f;
        if (timeSinceLast > resetTimeMs) {
            // Fading out phase
            float fadeProgress = (timeSinceLast - resetTimeMs) / 1000.0f; // 1s fade
            globalAlpha = 1.0f - fadeProgress;
            if (globalAlpha < 0) globalAlpha = 0;
        }
        
        // Clamp alpha strictly
        globalAlpha = MathHelper.clamp(globalAlpha, 0.0f, 1.0f);
        
        // Force hide if alpha is zero to prevent flash artifacts
        // Increase threshold slightly
        if (globalAlpha <= 0.01f) {
            return;
        }
        
        if (globalAlpha > 0.01f) {
            renderContent(context, session.getTotalDamage(), session.getComboCount(), 
                session.getRemainingTimeProgress(), session.getDamageHistory(), false, globalAlpha, tickDelta);
        }
    }
    
    public void renderPreview(DrawContext context, int centerX, int centerY) {
        // Mock Data
        float total = 12.5f;
        int combo = 5;
        float progress = 0.7f;
        List<DamageSessionManager.DamageEntry> history = List.of(
            new DamageSessionManager.DamageEntry(2.5f, false, 0),
            new DamageSessionManager.DamageEntry(2.5f, false, 0),
            new DamageSessionManager.DamageEntry(2.5f, false, 0),
            new DamageSessionManager.DamageEntry(2.5f, false, 0),
            new DamageSessionManager.DamageEntry(2.5f, false, 0)
        );
        
        renderContent(context, total, combo, progress, history, true, 1.0f, 1.0f);
    }

    private void renderContent(DrawContext context, float total, int combo, float targetProgress, List<DamageSessionManager.DamageEntry> history, boolean isPreview, float globalAlpha, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer tr = client.textRenderer;
        
        // Position
        float cfgX = DamageEngineConfig.getInstance().hudX;
        float cfgY = DamageEngineConfig.getInstance().hudY;
        int x = cfgX == -1.0f ? client.getWindow().getScaledWidth() / 2 : (int)(cfgX * client.getWindow().getScaledWidth());
        int y = cfgY == -1.0f ? client.getWindow().getScaledHeight() / 2 + 20 : (int)(cfgY * client.getWindow().getScaledHeight());
        
        float scale = DamageEngineConfig.getInstance().hudScale;
        
        context.getMatrices().push();
        context.getMatrices().translate(x, y, 0);
        context.getMatrices().scale(scale, scale, 1.0f);
        
        // Helper to apply alpha
        int baseAlpha = (int)(255 * globalAlpha);
        
        // 1. Render Total Damage (Big)
        String totalText = String.format("%.1f", total);
        
        // Color Logic
        int color = 0xFFFFFF; // White < 20
        if (total >= 100) color = 0xFFD700; 
        else if (total >= 50) color = 0xFC54FC; // Requested #FC54FC
        else if (total >= 25) color = 0x3FA9F0; // Light Blue
        
        int colorWithAlpha = (color & 0x00FFFFFF) | (baseAlpha << 24);
        
        float damageScale = 1.5f;
        context.getMatrices().push();
        context.getMatrices().scale(damageScale, damageScale, 1.0f);
        int textWidth = tr.getWidth(totalText);
        context.drawTextWithShadow(tr, totalText, -textWidth / 2, 0, colorWithAlpha);
        context.getMatrices().pop();
        
        // 2. Render Combo
        String comboText = "x" + combo;
        int comboColor = (0xFFAA00 & 0x00FFFFFF) | (baseAlpha << 24);
        context.drawTextWithShadow(tr, comboText, (int)(textWidth * damageScale / 2) + 5, 2, comboColor);
        
        // 3. Progress Bar
        if (DamageEngineConfig.getInstance().showProgressBar) {
            if (isPreview) {
                smoothProgress = targetProgress;
            } else {
                float delta = tickDelta;
                
                // Logic:
                // If target > current, we are refilling (Combo extended)
                // If target <= current, we are draining (Time passing)
                
                // Detect Combo Change
                if (combo > lastComboCount) {
                    if (combo == 1) {
                        // First hit: No animation, instant fill
                        smoothProgress = 1.0f;
                        isRefilling = false;
                    } else {
                        // Combo hit: Refill animation
                        isRefilling = true;
                    }
                    lastComboCount = combo;
                }
                
                if (isRefilling) {
                     // Fast refill animation
                     float refillSpeed = 0.1f * delta; // Adjust speed as needed
                     smoothProgress += refillSpeed;
                     if (smoothProgress >= targetProgress) {
                         smoothProgress = targetProgress;
                         isRefilling = false; // Done filling, switch to drain mode
                     }
                } else {
                    // Draining: Directly map to target (Linear time based)
                    // The targetProgress from session is already linear based on time.
                    // But to ensure it's smooth, we can interpolate slightly or trust the high-res timer
                    // Use a very fast lerp to smooth out any tick-alignment jitters, but keep it mostly linear
                    float drainSpeed = 0.5f * delta;
                    if (Math.abs(smoothProgress - targetProgress) > 0.01f) {
                        smoothProgress = MathHelper.lerp(drainSpeed, smoothProgress, targetProgress);
                    } else {
                        smoothProgress = targetProgress;
                    }
                }
                
                // Clamp
                smoothProgress = MathHelper.clamp(smoothProgress, 0.0f, 1.0f);
            }
            
            int barWidth = 40;
            int barHeight = 2;
            int barX = -barWidth / 2;
            int barY = 15;
            
            int bgAlpha = (int)(128 * globalAlpha); // 0x80 base
            context.fill(barX, barY, barX + barWidth, barY + barHeight, (bgAlpha << 24)); // Background Black
            
            int barColor = DamageEngineConfig.getInstance().progressBarColor;
            int barAlpha = (int)(255 * globalAlpha);
            int barColorWithAlpha = (barColor & 0x00FFFFFF) | (barAlpha << 24);
            
            context.fill(barX, barY, barX + (int)(barWidth * smoothProgress), barY + barHeight, barColorWithAlpha);
        }
        
        // 4. Damage List
        // Smoothly interpolate list position?
        // Actually, let's use the 'index' as the target Y, but if an item was removed, we can't easily track it without state.
        // Simplified approach: Just ensure the rendering is clean.
        // User said: "List moving up seems not smooth".
        // We will try to interpolate the Y position based on fractional time?
        // No, that's for scrolling.
        // Let's stick to the fade logic for now.
        
        int listStartX = (int)(textWidth * damageScale / 2) + 40;
        int listStartY = -20;
        
        // Reverse iteration order to draw newest first (if newest is at end of list)
        // Or render based on list order.
        // User request: "New data should be first, old data backwards".
        // Currently damageHistory adds new entries to the END.
        // So index 0 is oldest, index size-1 is newest.
        // If we want Newest at top, we should iterate from size-1 down to 0.
        // And position them downwards: y + 0, y + 10, etc.
        
        List<DamageSessionManager.DamageEntry> renderList = history;
        // Iterate backwards
        int renderIndex = 0;
        for (int i = renderList.size() - 1; i >= 0; i--) {
            DamageSessionManager.DamageEntry entry = renderList.get(i);
            long timeAlive = isPreview ? 0 : (System.currentTimeMillis() - entry.timestamp());
            
            // Global Fade Logic Override:
            // If we are in global fade out, force item fade out
            float finalItemAlpha = 1.0f;
            
            if (!isPreview) {
                // Item natural fade (after 4s)
                if (timeAlive > 4000) {
                     finalItemAlpha = (5000 - timeAlive) / 1000f;
                }
            }
            
            // Multiply by global alpha
            finalItemAlpha *= globalAlpha;
            
            if (finalItemAlpha <= 0) continue;
            
            // Ensure alpha is clamped
            finalItemAlpha = MathHelper.clamp(finalItemAlpha, 0.0f, 1.0f);
            
            int itemAlpha = (int)(255 * finalItemAlpha);
            
            int entryColor = entry.isCrit() ? 0xFFD700 : 0xFFFFFF;
            int itemColorWithAlpha = (entryColor & 0x00FFFFFF) | (itemAlpha << 24);
            
            String valText = String.format("%.1f", entry.damage());
            context.drawTextWithShadow(tr, valText, listStartX, listStartY + renderIndex * 10, itemColorWithAlpha);
            renderIndex++;
        }
        
        context.getMatrices().pop();
    }
}
