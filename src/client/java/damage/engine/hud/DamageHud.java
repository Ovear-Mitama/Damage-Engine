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
            if (!DamageEngineConfig.getInstance().showDamage) return;
            
            DamageSessionManager session = DamageSessionManager.getInstance();
            if (!session.isActive()) {
                lastComboCount = 0;
                return;
            }
            
            // 防止空会话闪烁
            if (session.getTotalDamage() <= 0 && !session.getDamageHistory().isEmpty()) {

            }
    
            long now = System.currentTimeMillis();
            long timeSinceLast = now - session.getLastHitTime();
            long resetTimeMs = (long)(DamageEngineConfig.getInstance().resetTime * 1000);
            
            float globalAlpha = 1.0f;
            if (timeSinceLast > resetTimeMs) {
                // 淡出阶段
                float fadeProgress = (timeSinceLast - resetTimeMs) / 1000.0f; // 1秒淡出
                globalAlpha = 1.0f - fadeProgress;
                if (globalAlpha < 0) globalAlpha = 0;
            }
            
            // 严格限制 alpha
            globalAlpha = MathHelper.clamp(globalAlpha, 0.0f, 1.0f);
            
            // 如果 alpha 为零则强制隐藏以防止闪烁伪影
            // 稍微增加阈值
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
        // 模拟数据
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
        
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            TextRenderer tr = client.textRenderer;
            
            long now = System.currentTimeMillis();
            long timeSinceLast = 0;
            long resetTimeMs = (long)(DamageEngineConfig.getInstance().resetTime * 1000);
            
            if (!isPreview) {
                DamageSessionManager session = DamageSessionManager.getInstance();
                timeSinceLast = now - session.getLastHitTime();
            }
            
            // 位置
            float cfgX = DamageEngineConfig.getInstance().hudX;
            float cfgY = DamageEngineConfig.getInstance().hudY;
            int x = cfgX == -1.0f ? client.getWindow().getScaledWidth() / 2 : (int)(cfgX * client.getWindow().getScaledWidth());
            int y = cfgY == -1.0f ? client.getWindow().getScaledHeight() / 2 + 20 : (int)(cfgY * client.getWindow().getScaledHeight());
            
            float scale = DamageEngineConfig.getInstance().hudScale;
            
            context.getMatrices().push();
            context.getMatrices().translate(x, y, 0);
            context.getMatrices().scale(scale, scale, 1.0f);
            
            // 应用 alpha 的辅助工具
            int baseAlpha = (int)(255 * globalAlpha);

            // 类似修复列表闪烁：如果 baseAlpha 太低，直接不渲染文字
            if (baseAlpha < 5) return;
            
            // 1. 渲染总伤害（大）
            String totalText = String.format("%.1f", total);
            
            // 颜色逻辑
            int color = 0xFFFFFFFF; // 默认白色
            float bestThreshold = -1f;
            
            // 复制列表以避免迭代期间的 CME
            List<DamageEngineConfig.DamageThreshold> thresholds = null;
            if (DamageEngineConfig.getInstance().damageThresholds != null) {
                 thresholds = new java.util.ArrayList<>(DamageEngineConfig.getInstance().damageThresholds);
            } else {
                 thresholds = new java.util.ArrayList<>();
            }
            
            for (DamageEngineConfig.DamageThreshold dt : thresholds) {
                 if (dt == null) continue;
                 if (total >= dt.threshold) {
                     if (dt.threshold > bestThreshold) {
                         bestThreshold = dt.threshold;
                         color = dt.color;
                     }
                 }
            }
            
            int colorWithAlpha = (color & 0x00FFFFFF) | (baseAlpha << 24);
            
            float damageScale = 1.5f;
            context.getMatrices().push();
            context.getMatrices().scale(damageScale, damageScale, 1.0f);
            int textWidth = tr.getWidth(totalText);
            context.drawTextWithShadow(tr, totalText, -textWidth / 2, 0, colorWithAlpha);
            context.getMatrices().pop();
            
            // 2. 渲染连击
            String comboText = "x" + combo;
            int comboColor = (0xFFAA00 & 0x00FFFFFF) | (baseAlpha << 24);
            context.drawTextWithShadow(tr, comboText, (int)(textWidth * damageScale / 2) + 5, 2, comboColor);
            
            // 3. 进度条
            if (DamageEngineConfig.getInstance().showProgressBar) {
                if (isPreview) {
                    smoothProgress = targetProgress;
                } else {
                    float delta = MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(true);
                    
                    
                    // 检测连击变化
                    if (combo > lastComboCount) {
                        if (combo == 1) {
                            // 第一次攻击：无动画，瞬间填充
                            smoothProgress = 1.0f;
                            isRefilling = false;
                        } else {
                            // 连击攻击：重新填充动画
                            isRefilling = true;
                        }
                        lastComboCount = combo;
                    }
                    
                    if (isRefilling) {
                         // 快速重新填充动画
                         float refillSpeed = 0.1f * delta; // 根据需要调整速度
                         smoothProgress += refillSpeed;
                         if (smoothProgress >= targetProgress) {
                             smoothProgress = targetProgress;
                             isRefilling = false; // 填充完成，切换到消耗模式
                         }
                    } else {
                        
                        if (resetTimeMs > 0) {
                            float realProgress = 1.0f - (float)timeSinceLast / (float)resetTimeMs;
                            smoothProgress = MathHelper.clamp(realProgress, 0.0f, 1.0f);
                        } else {
                            smoothProgress = 0.0f;
                        }
                    }
                    
                    // 限制
                    smoothProgress = MathHelper.clamp(smoothProgress, 0.0f, 1.0f);
                }
                
                int barWidth = 40;
                int barHeight = 2;
                int barX = -barWidth / 2;
                int barY = 15;
                
                int bgAlpha = (int)(128 * globalAlpha); // 0x80 基数
                context.fill(barX, barY, barX + barWidth, barY + barHeight, (bgAlpha << 24)); // 背景黑色
                
                int barColor = DamageEngineConfig.getInstance().progressBarColor;
                int barAlpha = (int)(255 * globalAlpha);
                int barColorWithAlpha = (barColor & 0x00FFFFFF) | (barAlpha << 24);
                
                context.fill(barX, barY, barX + (int)(barWidth * smoothProgress), barY + barHeight, barColorWithAlpha);
            }
            
            // 4. 伤害列表
            if (DamageEngineConfig.getInstance().showDamageHistory) {
            int listStartX = (int)(textWidth * damageScale / 2) + 40;
            int listStartY = -20;
            
            List<DamageSessionManager.DamageEntry> renderList;
            if (history != null) {
                renderList = new java.util.ArrayList<>(history);
            } else {
                renderList = java.util.Collections.emptyList();
            }
            
            // 向后迭代
            int renderIndex = 0;
            int limit = DamageEngineConfig.getInstance().historyLimit;
    
            for (int i = renderList.size() - 1; i >= 0; i--) {
                if (renderIndex >= limit) break;
                
                DamageSessionManager.DamageEntry entry = renderList.get(i);
                if (entry == null) continue;
                
                long timeAlive = isPreview ? 0 : (System.currentTimeMillis() - entry.timestamp());
                
                // 全局淡出逻辑覆盖：
                // 如果我们处于全局淡出，强制项目淡出
                float finalItemAlpha = 1.0f;
                
                if (!isPreview) {
                    // 项目自然淡出
                    float disappearTime = DamageEngineConfig.getInstance().historyDisappearanceTime;
                    long disappearTimeMs = (long)(disappearTime * 1000);
                    long fadeStartMs = disappearTimeMs - 1000;
                    if (fadeStartMs < 0) fadeStartMs = 0;
                    
                    if (timeAlive > fadeStartMs) {
                     finalItemAlpha = (disappearTimeMs - timeAlive) / 1000f;
                }
            }
            
            // 乘以全局 alpha
                finalItemAlpha *= globalAlpha;
                
                if (finalItemAlpha <= 0) continue;
                
                // 确保 alpha 被限制
                finalItemAlpha = MathHelper.clamp(finalItemAlpha, 0.0f, 1.0f);
                
                int itemAlpha = (int)(255 * finalItemAlpha);
                
                // 如果 alpha 很低但仍 >0，在最后一帧可能会闪烁？
                // 强制如果 alpha < 5/255 则隐藏
                if (itemAlpha < 5) continue;
                
                int entryColor = entry.isCrit() ? 0xFFD700 : 0xFFFFFF;
                int itemColorWithAlpha = (entryColor & 0x00FFFFFF) | (itemAlpha << 24);
                
                String valText = String.format("%.1f", entry.damage());
                context.drawTextWithShadow(tr, valText, listStartX, listStartY + renderIndex * 10, itemColorWithAlpha);
                renderIndex++;
            }
            }
            
            context.getMatrices().pop();
        } catch (Exception e) {
            e.printStackTrace();
            context.getMatrices().pop(); // 如果有异常确保 pop
        }
    }
}
