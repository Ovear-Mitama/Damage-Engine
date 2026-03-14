package damage.engine.hud;

import damage.engine.DamageEngineConfig;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.text.Text;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import java.util.List;


public class DamageHud implements HudRenderCallback {
    private float smoothProgress = 0f;
    private boolean isRefilling = false;
    private int lastComboCount = 0;
    private int infoLastTargetId = -1;
    private float infoSmoothRatio = -1f;
    private float infoLagRatio = -1f;
    private float infoHealRatio = -1f;
    private boolean infoLagHold = false;
    private boolean infoHealHold = false;
    private float infoAvatarFactor = 0f;
    private float infoAvatarAlpha = 0f;
    private boolean infoSwitching = false;
    private long infoSwitchStartMs = 0;
    private float infoPrevSmoothRatio = -1f;
    private float infoPrevLagRatio = -1f;
    private float infoPrevHealRatio = -1f;
    private boolean infoPrevDamageTailActive = false;
    private long infoLayoutLastUpdateMs = 0;
    private long infoLastUpdateMs = 0;
    private long infoHealthLastUpdateMs = 0;
    private float infoAlpha = 0f;
    private boolean infoFading = false;
    private long infoFadeStartMs = 0;
    private static final long INFO_FADE_MS = 1000;
    private static final long INFO_SWITCH_MS = 180;
    private boolean infoDeathDrain = false;
    private boolean infoDamageTailActive = false;
    private boolean infoDamageTailPending = false;
    private boolean infoHasSnapshot = false;
    private String infoName = "";
    private boolean infoIsPlayer = false;
    private SkinTextures infoPlayerSkin = null;
    private SkinTextures infoLastPlayerSkin = null;
    private float infoHealth = 0f;
    private float infoMaxHealth = 1f;
    private float infoAbsorption = 0f;

    @Override
    public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            DamageEngineConfig config = DamageEngineConfig.getInstance();
            
            if (config.hideOnF1 && client.options.hudHidden) return;
            if (!config.showDamage) return;
            
            DamageSessionManager session = DamageSessionManager.getInstance();
            updateInfoAnimation(session, client);
            if (config.showInfo && infoAlpha > 0.01f) {
                float a = infoAlpha;
                renderModule(context, config.infoConfig, client, a, () -> {
                    renderInfo(context, session, false, a, client);
                });
            }
            
            if (!session.isActive()) {
                lastComboCount = 0;
                return;
            }
            
            if (!config.resetEnabled) {
                renderDamageContent(context, session.getTotalDamage(), session.getComboCount(), 1.0f, session.getDamageHistory(), false, 1.0f);
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
                renderDamageContent(context, session.getTotalDamage(), session.getComboCount(),
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
        
        renderDamageContent(context, total, combo, progress, history, true, 1.0f);
        
        MinecraftClient client = MinecraftClient.getInstance();
        DamageEngineConfig config = DamageEngineConfig.getInstance();
        renderModule(context, config.infoConfig, client, 1.0f, () -> {
            renderInfo(context, null, true, 1.0f, client);
        });
    }

    private void renderDamageContent(DrawContext context, float total, int combo, float targetProgress, List<DamageSessionManager.DamageEntry> history, boolean isPreview, float globalAlpha) {
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
        
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(x, y);
        context.getMatrices().scale(moduleConfig.scale, moduleConfig.scale);
        
        renderAction.run();
        
        context.getMatrices().popMatrix();
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
        context.getMatrices().pushMatrix();
        context.getMatrices().scale(damageScale, damageScale);
        int textWidth = tr.getWidth(totalText);
        context.drawTextWithShadow(tr, totalText, -textWidth / 2, 0, colorWithAlpha);
        
        if (combo > 0) {
            String comboText = "x" + combo;
            int comboColorCfg = DamageEngineConfig.getInstance().comboColor;
            int comboColor = (comboColorCfg & 0x00FFFFFF) | (baseAlpha << 24);
            
            context.getMatrices().pushMatrix();
            
            float comboScale = 0.8f;
            
            context.getMatrices().translate(textWidth / 2.0f + 3, 0);
            
            context.getMatrices().scale(comboScale, comboScale);
            
            context.drawTextWithShadow(tr, comboText, 0, 3, comboColor);
            
            context.getMatrices().popMatrix();
        }
        
        context.getMatrices().popMatrix();
        
        if (DamageEngineConfig.getInstance().showProgressBar && DamageEngineConfig.getInstance().resetEnabled) {
            if (isPreview) {
                smoothProgress = targetProgress;
            } else {
                float delta = 1.0f; // client.getRenderTickCounter().getTickDelta(true); // TODO: Fix RenderTickCounter
                
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
            context.getMatrices().pushMatrix();
            context.getMatrices().translate(barX, barY);
            context.getMatrices().scale(barWidth * smoothProgress, (float)barHeight);
            
            context.fill(0, 0, 1, 1, barColor);
            
            context.getMatrices().popMatrix();
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
    
    private void updateInfoAnimation(DamageSessionManager session, MinecraftClient client) {
        long now = System.currentTimeMillis();
        float dt = infoLastUpdateMs == 0 ? 0.016f : (now - infoLastUpdateMs) / 1000.0f;
        dt = MathHelper.clamp(dt, 0.0f, 0.1f);
        infoLastUpdateMs = now;
        
        int candidateTargetId = session.isInfoActive() ? session.getLastTargetEntityId() : infoLastTargetId;
        
        LivingEntity target = null;
        if (candidateTargetId != -1 && client.world != null) {
            Entity e = client.world.getEntityById(candidateTargetId);
            if (e instanceof LivingEntity le) {
                target = le;
            }
        }
        
        boolean shouldBeActive = session.isInfoActive() && target != null && target.isAlive();
        
        if (shouldBeActive) {
            boolean wasInactive = !infoHasSnapshot || infoAlpha <= 0.01f;
            boolean targetAvatar = target instanceof AbstractClientPlayerEntity;
            captureInfoSnapshot(target);
            infoFading = false;
            infoAlpha = 1.0f;
            infoDeathDrain = false;
            if (wasInactive) {
                infoAvatarFactor = targetAvatar ? 1.0f : 0.0f;
                infoAvatarAlpha = infoAvatarFactor;
            }
            
            int targetId = session.getLastTargetEntityId();
            if (targetId != infoLastTargetId) {
                if (!wasInactive && infoSmoothRatio >= 0) {
                    float nextRatio = MathHelper.clamp(target.getHealth() / Math.max(1.0f, target.getMaxHealth()), 0.0f, 1.0f);
                    if (Math.abs(nextRatio - infoSmoothRatio) > 0.01f) {
                        infoPrevSmoothRatio = infoSmoothRatio;
                        infoPrevLagRatio = infoLagRatio;
                        infoPrevHealRatio = infoHealRatio;
                        infoPrevDamageTailActive = infoDamageTailActive;
                        infoSwitching = true;
                        infoSwitchStartMs = now;
                    } else {
                        infoSwitching = false;
                    }
                } else {
                    infoSwitching = false;
                }
                infoLastTargetId = targetId;
                infoSmoothRatio = -1f;
                infoLagRatio = -1f;
                infoHealRatio = -1f;
                infoLagHold = false;
                infoHealHold = false;
                infoDamageTailActive = false;
                infoDamageTailPending = false;
                infoHealthLastUpdateMs = 0;
            }
            return;
        }
        
        if (!infoDeathDrain && !infoFading && candidateTargetId != -1 && (target == null || !target.isAlive()) && (session.isInfoActive() || infoHasSnapshot)) {
            if (target != null) {
                captureInfoSnapshot(target);
            } else if (!infoHasSnapshot) {
                infoHasSnapshot = true;
                infoName = "";
                infoIsPlayer = false;
                infoPlayerSkin = null;
                infoMaxHealth = 1f;
            }
            infoHealth = 0f;
            infoAbsorption = 0f;
            infoDeathDrain = true;
            infoFading = false;
            infoAlpha = 1.0f;
            session.clearInfo();
            return;
        }
        
        if (infoDeathDrain) {
            float eps = 0.0025f;
            if (infoSmoothRatio >= 0 && infoLagRatio >= 0 && infoHealRatio >= 0
                && infoSmoothRatio <= eps && infoLagRatio <= eps && infoHealRatio <= eps) {
                infoDeathDrain = false;
                infoFading = true;
                infoFadeStartMs = now;
                infoLastTargetId = -1;
            } else {
                infoFading = false;
                infoAlpha = 1.0f;
            }
        } else if (infoHasSnapshot && !infoFading && infoAlpha > 0.01f) {
            infoFading = true;
            infoFadeStartMs = now;
        }
        
        if (infoFading) {
            float t = (now - infoFadeStartMs) / (float)INFO_FADE_MS;
            infoAlpha = 1.0f - MathHelper.clamp(t, 0.0f, 1.0f);
            if (infoAlpha <= 0.01f) {
                infoAlpha = 0.0f;
                infoFading = false;
                infoHasSnapshot = false;
                infoLastTargetId = -1;
                infoLayoutLastUpdateMs = 0;
                infoSmoothRatio = -1f;
                infoLagRatio = -1f;
                infoHealRatio = -1f;
                infoLagHold = false;
                infoHealHold = false;
                infoDeathDrain = false;
                infoDamageTailActive = false;
                infoDamageTailPending = false;
                infoAvatarAlpha = 0f;
                infoSwitching = false;
                infoPrevSmoothRatio = -1f;
                infoPrevLagRatio = -1f;
                infoPrevHealRatio = -1f;
                infoPrevDamageTailActive = false;
                infoHealthLastUpdateMs = 0;
            }
        } else if (infoDeathDrain) {
            infoAlpha = 1.0f;
        } else {
            infoAlpha = 0.0f;
        }
    }
    
    private void captureInfoSnapshot(LivingEntity target) {
        infoHasSnapshot = true;
        infoName = target.getName().getString();
        infoIsPlayer = target instanceof AbstractClientPlayerEntity;
        if (infoIsPlayer) {
            infoPlayerSkin = ((AbstractClientPlayerEntity) target).getSkin();
            infoLastPlayerSkin = infoPlayerSkin;
        } else {
            infoPlayerSkin = null;
        }
        infoHealth = target.getHealth();
        infoMaxHealth = target.getMaxHealth();
        if (infoMaxHealth <= 0) infoMaxHealth = 1f;
        infoAbsorption = target.getAbsorptionAmount();
    }
    
    public void renderInfo(DrawContext context, DamageSessionManager session, boolean isPreview, float globalAlpha, MinecraftClient client) {
        float health;
        float maxHealth;
        float absorption;
        String fullName;
        boolean isPlayer;
        SkinTextures playerSkin;
        
        if (isPreview) {
            health = 10.0f;
            maxHealth = 20.0f;
            absorption = 0.0f;
            isPlayer = true;
            com.mojang.authlib.GameProfile profile = client.getGameProfile();
            fullName = client.player != null ? client.player.getName().getString() : "Player";
            if (client.player != null) {
                playerSkin = client.player.getSkin();
            } else if (profile != null) {
                playerSkin = client.getSkinProvider().supplySkinTextures(profile, true).get();
            } else {
                playerSkin = null;
            }
            infoAvatarFactor = 1.0f;
            infoAvatarAlpha = 1.0f;
            infoLastPlayerSkin = playerSkin;
        } else {
            if (!infoHasSnapshot) return;
            health = infoHealth;
            maxHealth = infoMaxHealth;
            absorption = infoAbsorption;
            fullName = infoName;
            isPlayer = infoIsPlayer;
            playerSkin = infoPlayerSkin;
        }
        
        int hp = Math.max(0, Math.round(health + absorption));
        int maxHp = Math.max(1, Math.round(maxHealth));
        float ratio = MathHelper.clamp(health / maxHealth, 0.0f, 1.0f);
        
        int baseAlpha = (int)(255 * globalAlpha);
        if (baseAlpha < 5) return;
        
        long now = System.currentTimeMillis();
        float layoutDt = infoLayoutLastUpdateMs == 0 ? 0.016f : (now - infoLayoutLastUpdateMs) / 1000.0f;
        layoutDt = MathHelper.clamp(layoutDt, 0.0f, 0.1f);
        infoLayoutLastUpdateMs = now;
        
        float targetAvatar = isPlayer ? 1.0f : 0.0f;
        float avatarSmoothing = 1.0f - (float)Math.pow(0.0005, layoutDt);
        infoAvatarFactor += (targetAvatar - infoAvatarFactor) * avatarSmoothing;
        infoAvatarFactor = MathHelper.clamp(infoAvatarFactor, 0.0f, 1.0f);
        
        float avatarDelay = MathHelper.clamp((infoAvatarFactor - 0.60f) / 0.30f, 0.0f, 1.0f);
        float targetAvatarAlpha = targetAvatar * avatarDelay;
        infoAvatarAlpha += (targetAvatarAlpha - infoAvatarAlpha) * avatarSmoothing;
        infoAvatarAlpha = MathHelper.clamp(infoAvatarAlpha, 0.0f, 1.0f);
        
        int panelH = 29;
        
        int bgOpacity = MathHelper.clamp(DamageEngineConfig.getInstance().infoBackgroundOpacity, 0, 100);
        int bgBaseAlpha = (int)Math.round(255 * (bgOpacity / 100.0f));
        int bgAlpha = (int)Math.round(bgBaseAlpha * globalAlpha);
        int bgColor = DamageEngineConfig.getInstance().infoBackgroundColor;
        int bg = (bgColor & 0x00FFFFFF) | (bgAlpha << 24);
        
        int barW = 80;
        int barX = -barW / 2;
        
        int avatarSize = 20;
        int avatarSlotFull = avatarSize + 3;
        float layoutAvatarFactor = Math.max(infoAvatarFactor, infoAvatarAlpha);
        int avatarSlot = (int)Math.round(avatarSlotFull * layoutAvatarFactor);
        int gap = 1;
        int leftPad = 1;
        int rightPad = 1;
        
        int contentLeft = barX - gap - avatarSlot;
        int baseX = contentLeft - leftPad;
        int baseRight = barX + barW + rightPad;
        int panelW = baseRight - baseX;
        int baseY = -panelH / 2;
        context.fill(baseX - 2, baseY - 2, baseX + panelW + 2, baseY + panelH + 2, bg);
        
        TextRenderer tr = client.textRenderer;
        
        int avatarDrawX = contentLeft + avatarSlot - avatarSlotFull;
        int avatarY = baseY + (panelH - avatarSize) / 2;
        
        SkinTextures skinToDraw = playerSkin != null ? playerSkin : infoLastPlayerSkin;
        if (skinToDraw != null && infoAvatarAlpha > 0.01f) {
            float avatarFade = infoAvatarAlpha;
            Identifier skinId = skinToDraw.body().id();
            AbstractTexture tex = client.getTextureManager().getTexture(skinId);
            AbstractTexture missing = client.getTextureManager().getTexture(TextureManager.MISSING_IDENTIFIER);
            
            if (tex == missing) {
                SkinTextures fallback = DefaultSkinHelper.getSkinTextures(client.getGameProfile());
                Identifier fallbackId = fallback.body().id();
                AbstractTexture fbTex = client.getTextureManager().getTexture(fallbackId);
                if (fbTex == missing) return;
                
                int a = (int)(baseAlpha * avatarFade);
                int tint = (a << 24) | 0xFFFFFF;
                PlayerSkinDrawer.draw(context, fallbackId, avatarDrawX + 1, avatarY + 1, avatarSize - 2, true, false, tint);
            } else {
                int a = (int)(baseAlpha * avatarFade);
                int tint = (a << 24) | 0xFFFFFF;
                PlayerSkinDrawer.draw(context, skinId, avatarDrawX + 1, avatarY + 1, avatarSize - 2, true, false, tint);
            }
        }
        
        int barH = 5;
        int barY = baseY + (panelH - barH) / 2;
        
        int textX = barX;
        int textGap = 3;
        int textYName = barY - textGap - tr.fontHeight;
        int textYHp = barY + barH + textGap;
        int maxTextWidth = barW;
        
        String name = fullName;
        if (tr.getWidth(name) > maxTextWidth) {
            String raw = name;
            while (!raw.isEmpty() && tr.getWidth(raw + "…") > maxTextWidth) {
                raw = raw.substring(0, raw.length() - 1);
            }
            name = raw.isEmpty() ? "" : raw + "…";
        }
        
        int textColor = (0x00FFFFFF) | (baseAlpha << 24);
        context.drawTextWithShadow(tr, Text.literal(name), textX, textYName, textColor);
        
        int barBg = ((int)(128 * globalAlpha) << 24);
        context.fill(barX, barY, barX + barW, barY + barH, barBg);
        
        float dt = infoHealthLastUpdateMs == 0 ? 0.016f : (now - infoHealthLastUpdateMs) / 1000.0f;
        infoHealthLastUpdateMs = now;
        dt = MathHelper.clamp(dt, 0.0f, 0.1f);
        
        if (infoSmoothRatio < 0) {
            infoSmoothRatio = ratio;
            infoLagRatio = ratio;
            infoHealRatio = ratio;
            infoLagHold = false;
            infoHealHold = false;
            infoDamageTailActive = false;
            infoDamageTailPending = false;
        } else {
            float prevSmooth = infoSmoothRatio;
            
            float downSmoothing = 1.0f - (float)Math.pow(0.001, dt);
            float upSmoothing = 1.0f - (float)Math.pow(0.05, dt);
            float smoothing = ratio < prevSmooth ? downSmoothing : upSmoothing;
            infoSmoothRatio += (ratio - infoSmoothRatio) * smoothing;
            
            if (ratio < prevSmooth - 0.0001f) {
                infoLagHold = true;
                infoDamageTailActive = true;
                infoDamageTailPending = true;
                infoLagRatio = Math.max(infoLagRatio, prevSmooth);
            }
            if (ratio > prevSmooth + 0.0001f) {
                infoHealHold = true;
            }
            
            if (infoLagHold) {
                if (Math.abs(infoSmoothRatio - ratio) < 0.0025f) {
                    infoLagHold = false;
                }
            }
            
            float tailSmoothing = 1.0f - (float)Math.pow(0.02, dt);
            if (infoDamageTailActive) {
                if (infoDamageTailPending) {
                    if (!infoLagHold || infoHealHold) {
                        infoDamageTailPending = false;
                    } else {
                        infoLagRatio = Math.max(infoLagRatio, prevSmooth);
                    }
                }
                
                if (!infoDamageTailPending) {
                    infoLagRatio += (infoSmoothRatio - infoLagRatio) * tailSmoothing;
                }
            } else if (!infoLagHold) {
                infoLagRatio += (ratio - infoLagRatio) * tailSmoothing;
            }
            
            if (infoHealHold) {
                if (infoHealRatio < 0) infoHealRatio = prevSmooth;
                infoHealRatio += (ratio - infoHealRatio) * downSmoothing;
                if (Math.abs(infoSmoothRatio - ratio) < 0.0025f) {
                    infoHealHold = false;
                    infoHealRatio = infoSmoothRatio;
                }
            } else {
                infoHealRatio = infoSmoothRatio;
            }
        }
        
        infoSmoothRatio = MathHelper.clamp(infoSmoothRatio, 0.0f, 1.0f);
        infoLagRatio = MathHelper.clamp(infoLagRatio, 0.0f, 1.0f);
        infoHealRatio = MathHelper.clamp(infoHealRatio, 0.0f, 1.0f);
        int barColor = DamageEngineConfig.getInstance().infoBarColor;
        
        float switchT = 1.0f;
        if (infoSwitching) {
            switchT = (System.currentTimeMillis() - infoSwitchStartMs) / (float)INFO_SWITCH_MS;
            if (switchT >= 1.0f) {
                switchT = 1.0f;
                infoSwitching = false;
            } else if (switchT < 0.0f) {
                switchT = 0.0f;
            }
        }
        
        float oldAlphaMul = 1.0f - switchT;
        float newAlphaMul = switchT;
        
        if (oldAlphaMul > 0.01f && infoPrevSmoothRatio >= 0) {
            float s = MathHelper.clamp(infoPrevSmoothRatio, 0.0f, 1.0f);
            float lag = MathHelper.clamp(infoPrevLagRatio, 0.0f, 1.0f);
            float heal = MathHelper.clamp(infoPrevHealRatio, 0.0f, 1.0f);
            int a = (int)(baseAlpha * oldAlphaMul);
            
            int fillW = (int)Math.floor(barW * s);
            int barColorWithAlpha = (barColor & 0x00FFFFFF) | (a << 24);
            context.fill(barX, barY, barX + fillW, barY + barH, barColorWithAlpha);
            
            if (heal > s) {
                int healStart = barX + (int)Math.floor(barW * s);
                int healEnd = barX + (int)Math.floor(barW * heal);
                int healAlpha = (int)(a * 0.45f);
                int healColor = (barColor & 0x00FFFFFF) | (healAlpha << 24);
                context.fill(healStart, barY, healEnd, barY + barH, healColor);
            }
            
            if (infoPrevDamageTailActive && lag > s) {
                int lagStart = barX + (int)Math.floor(barW * s);
                int lagEnd = barX + (int)Math.floor(barW * lag);
                int lagColor = (0xF9867D & 0x00FFFFFF) | (a << 24);
                context.fill(lagStart, barY, lagEnd, barY + barH, lagColor);
            }
        }
        
        if (newAlphaMul > 0.01f) {
            int a = (int)(baseAlpha * newAlphaMul);
            int fillW = (int)Math.floor(barW * infoSmoothRatio);
            int barColorWithAlpha = (barColor & 0x00FFFFFF) | (a << 24);
            context.fill(barX, barY, barX + fillW, barY + barH, barColorWithAlpha);
            
            if (infoHealRatio > infoSmoothRatio) {
                int healStart = barX + (int)Math.floor(barW * infoSmoothRatio);
                int healEnd = barX + (int)Math.floor(barW * infoHealRatio);
                int healAlpha = (int)(a * 0.45f);
                int healColor = (barColor & 0x00FFFFFF) | (healAlpha << 24);
                context.fill(healStart, barY, healEnd, barY + barH, healColor);
            }
            
            if (infoDamageTailActive && infoLagRatio > infoSmoothRatio) {
                int lagStart = barX + (int)Math.floor(barW * infoSmoothRatio);
                int lagEnd = barX + (int)Math.floor(barW * infoLagRatio);
                int lagColor = (0xF9867D & 0x00FFFFFF) | (a << 24);
                context.fill(lagStart, barY, lagEnd, barY + barH, lagColor);
                if (Math.abs(infoLagRatio - infoSmoothRatio) < 0.0025f) {
                    infoDamageTailActive = false;
                }
            }
        }
        
        boolean hasAbsorption = absorption > 0.01f;
        Identifier heartTexture = Identifier.ofVanilla(hasAbsorption ? "hud/heart/absorbing_full" : "hud/heart/full");
        
        Text hpText = Text.literal(hp + "/" + maxHp);
        context.drawTextWithShadow(tr, hpText, textX + 10, textYHp, textColor);
        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, Identifier.ofVanilla("hud/heart/container"), textX, textYHp, 9, 9, globalAlpha);
        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, heartTexture, textX, textYHp, 9, 9, globalAlpha);
    }
}
