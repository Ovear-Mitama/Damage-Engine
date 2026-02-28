package damage.engine.hud;

import damage.engine.DamageEngineConfig;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.math.MathHelper;

import java.util.List;

public class DamageHud implements HudRenderCallback {
    private float smoothProgress = 0f;
    private boolean isRefilling = false;
    private int lastComboCount = 0;

    @Override
    public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            DamageEngineConfig config = DamageEngineConfig.getInstance();
            
            if (config.hideOnF1 && client.options.hudHidden) return;
            if (!config.showDamage) return;
            
            DamageSessionManager session = DamageSessionManager.getInstance();
            if (!session.isActive()) {
                lastComboCount = 0;
                return;
            }
            
            long now = System.currentTimeMillis();
            long timeSinceLast = now - session.getLastHitTime();
            long resetTimeMs = (long)(DamageEngineConfig.getInstance().resetTime * 1000);
            
            float globalAlpha = 1.0f;
            if (timeSinceLast > resetTimeMs) {
                float fadeProgress = (timeSinceLast - resetTimeMs) / 1000.0f;
                globalAlpha = 1.0f - fadeProgress;
                if (globalAlpha < 0) globalAlpha = 0;
            }
            
            globalAlpha = MathHelper.clamp(globalAlpha, 0.0f, 1.0f);
            
            if (globalAlpha <= 0.01f) {
                return;
            }
            
            if (globalAlpha > 0.01f) {
                renderContent(context, session.getTotalDamage(), session.getComboCount(), 
                    session.getRemainingTimeProgress(), session.getDamageHistory(), false, globalAlpha);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public void renderPreview(DrawContext context, int centerX, int centerY) {
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
        
        renderContent(context, total, combo, progress, history, true, 1.0f);
    }

    private void renderContent(DrawContext context, float total, int combo, float targetProgress, List<DamageSessionManager.DamageEntry> history, boolean isPreview, float globalAlpha) {
        DamageEngineConfig config = DamageEngineConfig.getInstance();
        MinecraftClient client = MinecraftClient.getInstance();
        
        renderModule(context, config.totalDamageConfig, client, globalAlpha, () -> {
            renderTotalDamage(context, total, targetProgress, isPreview, globalAlpha, combo, client);
        });

        if (config.showDamageHistory) {
            renderModule(context, config.historyConfig, client, globalAlpha, () -> {
                renderHistory(context, history, isPreview, globalAlpha, client);
            });
        }
    }

    public void renderModule(DrawContext context, DamageEngineConfig.ModuleConfig moduleConfig, MinecraftClient client, float globalAlpha, Runnable renderAction) {
        if (!moduleConfig.enabled) return;
        
        int x = moduleConfig.x == -1.0f ? client.getWindow().getScaledWidth() / 2 : (int)(moduleConfig.x * client.getWindow().getScaledWidth());
        int y = moduleConfig.y == -1.0f ? client.getWindow().getScaledHeight() / 2 : (int)(moduleConfig.y * client.getWindow().getScaledHeight());
        
        context.getMatrices().push();
        context.getMatrices().translate(x, y, 0);
        context.getMatrices().scale(moduleConfig.scale, moduleConfig.scale, 1.0f);
        
        renderAction.run();
        
        context.getMatrices().pop();
    }
    
    public void renderTotalDamage(DrawContext context, float total, float targetProgress, boolean isPreview, float globalAlpha, int combo, MinecraftClient client) {
        TextRenderer tr = client.textRenderer;
        int baseAlpha = (int)(255 * globalAlpha);
        if (baseAlpha < 5) return;

        String totalText = String.format("%.1f", total);
        
        int color = 0xFFFFFFFF;
        float bestThreshold = -1f;
        
        List<DamageEngineConfig.DamageThreshold> thresholds = DamageEngineConfig.getInstance().damageThresholds;
        if (thresholds != null) {
             for (DamageEngineConfig.DamageThreshold dt : thresholds) {
                 if (total >= dt.threshold) {
                     if (dt.threshold > bestThreshold) {
                         bestThreshold = dt.threshold;
                         color = dt.color;
                     }
                 }
             }
        }
        
        int colorWithAlpha = (color & 0x00FFFFFF) | (baseAlpha << 24);
        
        float damageScale = 1.5f;
        context.getMatrices().push();
        context.getMatrices().scale(damageScale, damageScale, 1.0f);
        int textWidth = tr.getWidth(totalText);
        context.drawTextWithShadow(tr, totalText, -textWidth / 2, 0, colorWithAlpha);
        
        if (combo > 0) {
            String comboText = "x" + combo;
            int comboColorCfg = DamageEngineConfig.getInstance().comboColor;
            int comboColor = (comboColorCfg & 0x00FFFFFF) | (baseAlpha << 24);
            
            context.getMatrices().push();
            
            float comboScale = 0.8f;
            
            context.getMatrices().translate(textWidth / 2.0f + 3, 0, 0); 
            
            context.getMatrices().scale(comboScale, comboScale, 1.0f);
            
            context.drawTextWithShadow(tr, comboText, 0, 3, comboColor);
            
            context.getMatrices().pop();
        }
        
        context.getMatrices().pop();
        
        if (DamageEngineConfig.getInstance().showProgressBar) {
            if (isPreview) {
                smoothProgress = targetProgress;
            } else {
                float delta = client.getRenderTickCounter().getTickDelta(true);
                
                if (combo > lastComboCount) {
                    if (combo == 1) {
                        smoothProgress = 1.0f;
                        isRefilling = false;
                    } else {
                        isRefilling = true;
                    }
                    lastComboCount = combo;
                }
                
                if (isRefilling) {
                     float refillSpeed = 0.1f * delta;
                     smoothProgress += refillSpeed;
                     if (smoothProgress >= targetProgress) {
                         smoothProgress = targetProgress;
                         isRefilling = false;
                     }
                } else {
                    long resetTimeMs = (long)(DamageEngineConfig.getInstance().resetTime * 1000);
                     if (resetTimeMs > 0) {
                        long timeSinceLast = 0;
                         if (!isPreview) {
                            DamageSessionManager session = DamageSessionManager.getInstance();
                            timeSinceLast = System.currentTimeMillis() - session.getLastHitTime();
                        }
                        float realProgress = 1.0f - (float)timeSinceLast / (float)resetTimeMs;
                        smoothProgress = MathHelper.clamp(realProgress, 0.0f, 1.0f);
                    } else {
                        smoothProgress = 0.0f;
                    }
                }
                smoothProgress = MathHelper.clamp(smoothProgress, 0.0f, 1.0f);
            }
            
            int barWidth = 40;
            int barHeight = 2;
            int barX = -barWidth / 2;
            int barY = 15;
            
            int bgAlpha = (int)(128 * globalAlpha);
            context.fill(barX, barY, barX + barWidth, barY + barHeight, (bgAlpha << 24));
            
            int barColor = DamageEngineConfig.getInstance().progressBarColor;
            int barAlpha = (int)(255 * globalAlpha);
            int barColorWithAlpha = (barColor & 0x00FFFFFF) | (barAlpha << 24);
            
            // Use matrix scaling for smooth sub-pixel rendering of the progress bar
            context.getMatrices().push();
            context.getMatrices().translate(barX, barY, 0);
            context.getMatrices().scale(barWidth * smoothProgress, (float)barHeight, 1.0f);
            context.fill(0, 0, 1, 1, barColorWithAlpha);
            context.getMatrices().pop();
        }
    }
    
    public void renderCombo(DrawContext context, int combo, float globalAlpha, MinecraftClient client) {
        TextRenderer tr = client.textRenderer;
        int baseAlpha = (int)(255 * globalAlpha);
        if (baseAlpha < 5) return;
        
        String comboText = "x" + combo;
        int comboColorCfg = DamageEngineConfig.getInstance().comboColor;
        int comboColor = (comboColorCfg & 0x00FFFFFF) | (baseAlpha << 24);
        
        int textWidth = tr.getWidth(comboText);
        context.drawTextWithShadow(tr, comboText, -textWidth / 2, 0, comboColor);
    }
    
    public void renderHistory(DrawContext context, List<DamageSessionManager.DamageEntry> history, boolean isPreview, float globalAlpha, MinecraftClient client) {
        TextRenderer tr = client.textRenderer;
        int limit = DamageEngineConfig.getInstance().historyLimit;
        int historyColorCfg = DamageEngineConfig.getInstance().historyColor;

        int renderIndex = 0;
        List<DamageSessionManager.DamageEntry> renderList = (history != null) ? new java.util.ArrayList<>(history) : java.util.Collections.emptyList();
        
        for (int i = renderList.size() - 1; i >= 0; i--) {
            if (renderIndex >= limit) break;
            DamageSessionManager.DamageEntry entry = renderList.get(i);
            if (entry == null) continue;
            
            long timeAlive = isPreview ? 0 : (System.currentTimeMillis() - entry.timestamp());
            float finalItemAlpha = 1.0f;
            
            if (!isPreview) {
                float disappearTime = DamageEngineConfig.getInstance().historyDisappearanceTime;
                long disappearTimeMs = (long)(disappearTime * 1000);
                long fadeStartMs = disappearTimeMs - 1000;
                if (fadeStartMs < 0) fadeStartMs = 0;
                
                if (timeAlive > fadeStartMs) {
                    finalItemAlpha = (disappearTimeMs - timeAlive) / 1000f;
                }
            }
            
            finalItemAlpha *= globalAlpha;
            if (finalItemAlpha <= 0) continue;
            finalItemAlpha = MathHelper.clamp(finalItemAlpha, 0.0f, 1.0f);
            int itemAlpha = (int)(255 * finalItemAlpha);
            if (itemAlpha < 5) continue;
            
            int critColorCfg = DamageEngineConfig.getInstance().critColor;
            int entryColor = entry.isCrit() ? critColorCfg : historyColorCfg;
            int itemColorWithAlpha = (entryColor & 0x00FFFFFF) | (itemAlpha << 24);
            
            String valText = String.format("%.1f", entry.damage());
            int textWidth = tr.getWidth(valText);
            
            context.drawTextWithShadow(tr, valText, -textWidth / 2, renderIndex * 10, itemColorWithAlpha);
            renderIndex++;
        }
    }
}
