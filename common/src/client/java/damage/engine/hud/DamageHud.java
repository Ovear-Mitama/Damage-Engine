package damage.engine.hud;

import damage.engine.DamageEngineClient;
import damage.engine.DamageEngineConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.Mth;
import java.util.ArrayList;
import java.util.List;


public class DamageHud {
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
    private static final long INFO_FADE_MS = 500;
    private static final long INFO_SWITCH_MS = 500;
    private boolean infoDeathDrain = false;
    private boolean infoDamageTailActive = false;
    private boolean infoDamageTailPending = false;
    private boolean infoHasSnapshot = false;
    private String infoName = "";
    private boolean infoIsPlayer = false;
    private Identifier infoPlayerSkin = null;
    private Identifier infoPrevPlayerSkin = null;
    private float infoHealth = 0f;
    private float infoMaxHealth = 1f;
    private float infoAbsorption = 0f;

    private static final long HISTORY_ANIM_MS = 150;

    private float contentRightX = 20f;
    private String previewGrade = "";
    private int previewGradeColor = 0xFFFFFFFF;

    public void cyclePreviewGrades() {
        List<DamageEngineConfig.RatingGrade> grades = DamageEngineConfig.getInstance().ratingGrades;
        if (grades != null && !grades.isEmpty()) {
            int idx = (int)((System.currentTimeMillis() / 1500) % grades.size());
            previewGrade = grades.get(idx).text;
            previewGradeColor = grades.get(idx).color;
        }
    }

    public void onHudRender(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker) {
        try {
            Minecraft client = Minecraft.getInstance();
            if (client.screen instanceof damage.engine.client.gui.DamageConfigScreen) return;
            if (client.screen instanceof damage.engine.client.gui.HudEditorScreen) return;

            DamageEngineConfig config = DamageEngineConfig.getInstance();

            if (config.hideOnF1 && client.options.hideGui) return;
            if (!config.showDamage) return;

            DamageSessionManager session = DamageSessionManager.getInstance();
            
            if (!config.showDamageDisplay) {
                return;
            }

            updateInfoAnimation(session, client);
            if (config.showInfo && infoAlpha > 0.01f) {
                float a = infoAlpha;
                renderModule(guiGraphics, config.infoConfig, client, a, () -> {
                    renderInfo(guiGraphics, session, false, a, client);
                });
            }

            float globalAlpha = 1.0f;
            if (session.isActive()) {
                if (config.resetEnabled) {
                    long now = System.currentTimeMillis();
                    long timeSinceLast = now - session.getLastHitTime();
                    long resetTimeMs = (long)(DamageEngineConfig.getInstance().resetTime * 1000);
                    if (timeSinceLast > resetTimeMs) {
                        float fadeProgress = (timeSinceLast - resetTimeMs) / 1000.0f;
                        globalAlpha = 1.0f - fadeProgress;
                        if (globalAlpha < 0) globalAlpha = 0;
                    }
                    globalAlpha = Mth.clamp(globalAlpha, 0.0f, 1.0f);
                }
                
                if (globalAlpha > 0.01f) {
                    renderDamageContent(guiGraphics, session.getTotalDamage(), session.getComboCount(),
                        session.getRemainingTimeProgress(), session.getDamageHistory(), false, globalAlpha);
                }
            } else {
                lastComboCount = 0;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void renderPreview(GuiGraphicsExtractor guiGraphics, int centerX, int centerY) {
        DamageEngineConfig config = DamageEngineConfig.getInstance();
        Minecraft client = Minecraft.getInstance();

        float total = 0;
        int previewLimit = config.historyLimit;
        List<DamageSessionManager.DamageEntry> history = new ArrayList<>();
        for (int i = 0; i < previewLimit; i++) {
            boolean isCrit = i >= previewLimit - 3;
            float dmg = 1.5f + i * 0.3f;
            total += dmg;
            history.add(new DamageSessionManager.DamageEntry(dmg, isCrit, 0, -1));
        }
        int combo = previewLimit;
        float progress = 0.7f;

        List<DamageEngineConfig.RatingGrade> grades = config.ratingGrades;
        if (grades != null && !grades.isEmpty()) {
            int idx = (int)((System.currentTimeMillis() / 1500) % grades.size());
            previewGrade = grades.get(idx).text;
            previewGradeColor = grades.get(idx).color;
        }

        renderDamageContent(guiGraphics, total, combo, progress, history, true, 1.0f);

        if (config.showInfo) {
            renderModule(guiGraphics, config.infoConfig, client, 1.0f, () -> {
                renderInfo(guiGraphics, null, true, 1.0f, client);
            });
        }
    }

    private void renderDamageContent(GuiGraphicsExtractor guiGraphics, float total, int combo, float targetProgress, List<DamageSessionManager.DamageEntry> history, boolean isPreview, float globalAlpha) {
        DamageEngineConfig config = DamageEngineConfig.getInstance();
        Minecraft client = Minecraft.getInstance();

        RatingManager rm = RatingManager.getInstance();
        if ((isPreview && config.showRating) || (!isPreview && config.showRating && rm.isVisible())) {
            renderModule(guiGraphics, config.ratingConfig, client, globalAlpha, () -> {
                renderRating(guiGraphics, isPreview, globalAlpha, client);
            });
        }

        if (!isPreview || config.showDamageDisplay) {
            renderModule(guiGraphics, config.totalDamageConfig, client, globalAlpha, () -> {
                renderTotalDamage(guiGraphics, total, targetProgress, isPreview, globalAlpha, combo, client);

                if (config.showDamageHistory) {
                    renderHistory(guiGraphics, history, isPreview, globalAlpha, client);
                }
            });
        }
    }

    // 26.1+ 渲染状态提取阶段不允许做文件 I/O 与纹理上传（Vulkan 后端要求纹理预上传到 GPU），
    // 评级图片按 imagePath 缓存：仅首次加载并注册纹理，之后每帧直接复用。
    private static final java.util.Map<String, RatingTexture> RATING_TEXTURES = new java.util.HashMap<>();

    private record RatingTexture(Identifier texId, int width, int height) {}

    private static RatingTexture loadRatingTexture(String imagePath, Minecraft client) {
        try {
            java.io.File imageFile = new java.io.File(client.gameDirectory, "config/damage-engine/images/" + imagePath + ".png");
            if (!imageFile.exists()) {
                throw new Exception("Image file not found: " + imageFile.getAbsolutePath());
            }
            java.io.FileInputStream fis = new java.io.FileInputStream(imageFile);
            com.mojang.blaze3d.platform.NativeImage nativeImage = com.mojang.blaze3d.platform.NativeImage.read(fis);
            fis.close();
            if (nativeImage == null) {
                throw new Exception("Failed to read native image");
            }
            Identifier texId = Identifier.fromNamespaceAndPath("damage-engine", "rating_" + imagePath);
            net.minecraft.client.renderer.texture.DynamicTexture dynamicTexture = new net.minecraft.client.renderer.texture.DynamicTexture(() -> "damage-engine/rating_" + imagePath, nativeImage);
            client.getTextureManager().register(texId, dynamicTexture);
            return new RatingTexture(texId, nativeImage.getWidth(), nativeImage.getHeight());
        } catch (Exception e) {
            DamageEngineClient.LOGGER.error("Failed to load rating image: " + e.getMessage());
            return null;
        }
    }

    public void renderRating(GuiGraphicsExtractor guiGraphics, boolean isPreview, float globalAlpha, Minecraft client) {
        Font font = client.font;
        RatingManager rm = RatingManager.getInstance();
        int baseAlpha = (int)(255 * globalAlpha);

        float ratingAlpha = 0f;
        String ratingText = "";
        int ratingColor = 0;
        String imagePath = "";

        if (isPreview && !previewGrade.isEmpty()) {
            long t = System.currentTimeMillis() % 1500;
            float progress = Mth.clamp(t / 1500f, 0f, 1f);
            ratingAlpha = progress < 0.15f ? progress / 0.15f : (progress > 0.7f ? (1f - (progress - 0.7f) / 0.3f) : 1f);
            ratingText = previewGrade;
            ratingColor = previewGradeColor;
            List<DamageEngineConfig.RatingGrade> grades = DamageEngineConfig.getInstance().ratingGrades;
            if (grades != null) {
                for (DamageEngineConfig.RatingGrade g : grades) {
                    if (g.text.equals(previewGrade)) {
                        imagePath = g.imagePath;
                        break;
                    }
                }
            }
        } else if (!isPreview && rm.isVisible()) {
            long elapsed = System.currentTimeMillis() - rm.getGradeShowTime();
            float progress = Mth.clamp(elapsed / 2000f, 0f, 1f);
            ratingAlpha = progress > 0.6f ? (1f - (progress - 0.6f) / 0.4f) : 1f;
            ratingText = rm.getGrade();
            ratingColor = rm.getGradeColor();
            imagePath = rm.getGradeImagePath();
        }

        if (ratingAlpha > 0.01f) {
            int rAlpha = (int)(baseAlpha * ratingAlpha);
            int rColor = (ratingColor & 0x00FFFFFF) | (rAlpha << 24);

            if (DamageEngineConfig.getInstance().ratingUseImages && imagePath != null && !imagePath.isEmpty()) {
                RatingTexture rt = RATING_TEXTURES.computeIfAbsent(imagePath, p -> loadRatingTexture(p, client));
                if (rt != null) {
                    guiGraphics.pose().pushMatrix();
                    guiGraphics.pose().scale(0.15f, 0.15f);

                    int imgColor = (rAlpha << 24) | 0xFFFFFF;
                    guiGraphics.blit(RenderPipelines.GUI_TEXTURED, rt.texId(), -rt.width() / 2, -rt.height() / 2, 0.0f, 0.0f, rt.width(), rt.height(), rt.width(), rt.height(), rt.width(), rt.height(), imgColor);

                    guiGraphics.pose().popMatrix();
                } else {
                    // Image load failed once — fall back to text rendering (do not retry every frame)
                    float rs = 1.2f;
                    guiGraphics.pose().pushMatrix();
                    guiGraphics.pose().scale(rs, rs);
                    int gw = font.width(ratingText);
                    guiGraphics.text(font, ratingText, -gw / 2, -font.lineHeight / 2, rColor);
                    guiGraphics.pose().popMatrix();
                }
            } else {
                float rs = 1.2f;
                guiGraphics.pose().pushMatrix();
                guiGraphics.pose().scale(rs, rs);
                int gw = font.width(ratingText);
                guiGraphics.text(font, ratingText, -gw / 2, -font.lineHeight / 2, rColor);
                guiGraphics.pose().popMatrix();
            }
        }
    }

    public void renderModule(GuiGraphicsExtractor guiGraphics, DamageEngineConfig.ModuleConfig moduleConfig, Minecraft client, float globalAlpha, Runnable renderAction) {
        if (!moduleConfig.enabled) return;

        int x = moduleConfig.x == -1.0f ? client.getWindow().getGuiScaledWidth() / 2 : (int)(moduleConfig.x * client.getWindow().getGuiScaledWidth());
        int y = moduleConfig.y == -1.0f ? client.getWindow().getGuiScaledHeight() / 2 : (int)(moduleConfig.y * client.getWindow().getGuiScaledHeight());

        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate(x, y);
        guiGraphics.pose().scale(moduleConfig.scale, moduleConfig.scale);

        renderAction.run();

        guiGraphics.pose().popMatrix();
    }

    public void renderTotalDamage(GuiGraphicsExtractor guiGraphics, float total, float targetProgress, boolean isPreview, float globalAlpha, int combo, Minecraft client) {
        Font font = client.font;
        int baseAlpha = (int)(255 * globalAlpha);

        int decimalPlaces = DamageEngineConfig.getInstance().decimalPlaces;
        String totalText = formatDamage(total, decimalPlaces);

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

        boolean showCombo = DamageEngineConfig.getInstance().showCombo;
        float labelScale = 0.9f;
        int labelAlpha = baseAlpha;
        int labelColor = (0xFFFFFFFF) | (labelAlpha << 24);

        String damageLabel = "damage";
        String fullLabel;
        if (showCombo && combo > 0) {
            fullLabel = damageLabel + "  x" + combo;
        } else {
            fullLabel = damageLabel;
        }

        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().scale(labelScale, labelScale);
        int fullLabelWidth = font.width(fullLabel);
        guiGraphics.pose().popMatrix();

        float contentHalfWidth = (fullLabelWidth * labelScale) / 2.0f;
        contentRightX = contentHalfWidth;

        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().scale(labelScale, labelScale);
        int lblW = font.width(damageLabel);
        // Label shifts down when progress bar is hidden
        boolean hasProgressBar = DamageEngineConfig.getInstance().showProgressBar && DamageEngineConfig.getInstance().resetEnabled;
        int labelY = hasProgressBar ? -15 : -12;
        if (showCombo && combo > 0) {
            guiGraphics.text(font, damageLabel, -fullLabelWidth / 2, labelY, labelColor);
            String comboText = "x" + combo;
            int comboColorCfg = DamageEngineConfig.getInstance().comboColor;
            int comboColor = (comboColorCfg & 0x00FFFFFF) | (labelAlpha << 24);
            guiGraphics.text(font, comboText, fullLabelWidth / 2 - font.width(comboText), labelY, comboColor);
        } else {
            guiGraphics.text(font, damageLabel, -fullLabelWidth / 2 + (fullLabelWidth - lblW), labelY, labelColor);
        }
        guiGraphics.pose().popMatrix();

        if (DamageEngineConfig.getInstance().showProgressBar && DamageEngineConfig.getInstance().resetEnabled) {
            if (isPreview) {
                smoothProgress = targetProgress;
            } else {
                float delta = 1.0f;

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
                        smoothProgress = Mth.clamp(realProgress, 0.0f, 1.0f);
                    } else {
                        smoothProgress = 0.0f;
                    }
                }
                smoothProgress = Mth.clamp(smoothProgress, 0.0f, 1.0f);
            }

            float barWidth = contentHalfWidth * 2.0f;
            int barHeight = 1;
            float barX = -contentHalfWidth;
            int barY = -4;

            int bgAlpha = (int)(100 * globalAlpha);
            guiGraphics.fill((int)barX, barY, (int)(barX + barWidth), barY + barHeight, (bgAlpha << 24));

            int barColor = DamageEngineConfig.getInstance().progressBarColor | 0xFF000000;

            guiGraphics.pose().pushMatrix();
            guiGraphics.pose().translate(barX, barY);
            guiGraphics.pose().scale(barWidth * smoothProgress, (float)barHeight);
            guiGraphics.fill(0, 0, 1, 1, barColor);
            guiGraphics.pose().popMatrix();
        }

        float damageScale = 1.5f;
        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().scale(damageScale, damageScale);
        int textWidth = font.width(totalText);
        float rightEdgeInScale = contentHalfWidth / damageScale;
        float textX = rightEdgeInScale - textWidth;
        guiGraphics.text(font, totalText, (int)textX, 0, colorWithAlpha);
        guiGraphics.pose().popMatrix();
    }

    public void renderHistory(GuiGraphicsExtractor guiGraphics, List<DamageSessionManager.DamageEntry> history, boolean isPreview, float globalAlpha, Minecraft client) {
        Font font = client.font;
        int limit = DamageEngineConfig.getInstance().historyLimit;
        int decimalPlaces = DamageEngineConfig.getInstance().historyDecimalPlaces;
        boolean recordOtherPlayers = DamageEngineConfig.getInstance().recordOtherPlayers;

        int renderIndex = 0;
        List<DamageSessionManager.DamageEntry> renderList = (history != null) ? new java.util.ArrayList<>(history) : java.util.Collections.emptyList();

        long now = System.currentTimeMillis();

        // Vertical push-down animation: when a newer entry arrives, older entries
        // slide down one slot over the same duration as the horizontal entrance,
        // so a new damage record never hard-cuts an entry that is in motion.
        long newestTs = renderList.isEmpty() ? now : renderList.get(renderList.size() - 1).timestamp();
        float downProgress = isPreview ? 1.0f : Mth.clamp((now - newestTs) / (float)HISTORY_ANIM_MS, 0.0f, 1.0f);

        int baseY = 15;

        // Player avatar for preview when recording other players.
        // In preview the player may not be in-game (e.g. opened from Mod Menu on the
        // main menu), so fall back to the SkinManager lookup via the game profile.
        Identifier previewSkin = null;
        if (isPreview && recordOtherPlayers) {
            previewSkin = getPreviewSkin(client);
        }
        int avatarGap = (previewSkin != null) ? 11 : 0;

        for (int i = renderList.size() - 1; i >= 0; i--) {
            if (renderIndex >= limit) break;
            DamageSessionManager.DamageEntry entry = renderList.get(i);
            if (entry == null) continue;

            long timeAlive = isPreview ? 0 : (now - entry.timestamp());
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

            // Per-entry entrance animation: each entry animates from its own
            // birth time, so new hits never interrupt entries that are already
            // animating. Entries born in the same instant animate together.
            float entryAnimProgress = isPreview ? 1.0f
                : Mth.clamp(timeAlive / (float)HISTORY_ANIM_MS, 0.0f, 1.0f);
            float slideOffsetX = 0f;
            float slideAlphaMul = 1.0f;
            if (entryAnimProgress < 1.0f) {
                // Purely horizontal right-to-left slide + fade-in.
                slideOffsetX = (1.0f - entryAnimProgress) * 20f;
                slideAlphaMul = entryAnimProgress;
            }

            finalItemAlpha *= globalAlpha * slideAlphaMul;
            if (finalItemAlpha <= 0) continue;
            finalItemAlpha = Mth.clamp(finalItemAlpha, 0.0f, 1.0f);
            int itemAlpha = (int)(255 * finalItemAlpha);
            if (itemAlpha < 5) continue;

            int critColorCfg = DamageEngineConfig.getInstance().critColor;
            int normalColorCfg = DamageEngineConfig.getInstance().normalColor;
            int entryColor = entry.isCrit() ? critColorCfg : normalColorCfg;
            int itemColorWithAlpha = (entryColor & 0x00FFFFFF) | (itemAlpha << 24);

            String valText = formatDamage(entry.damage(), decimalPlaces);
            int textWidth = font.width(valText);

            float targetY = baseY + renderIndex * 10;
            // Newest entry sits in its final slot (its entrance is the horizontal
            // slide above); older entries smoothly push down one slot when a newer
            // entry arrives, instead of teleporting mid-animation.
            float yPos;
            if (renderIndex == 0 || downProgress >= 1.0f) {
                yPos = targetY;
            } else {
                float oldY = targetY - 10;
                yPos = Mth.lerp(downProgress, oldY, targetY);
            }

            float xPos = contentRightX - textWidth + slideOffsetX;

            // Draw player avatar for other players' damage entries
            if (!isPreview && recordOtherPlayers && entry.attackerId() > 0 && client.level != null) {
                Entity attackerEntity = client.level.getEntity(entry.attackerId());
                if (attackerEntity instanceof AbstractClientPlayer attackerPlayer) {
                    Identifier skinTex = attackerPlayer.getSkin().body().texturePath();
                    if (skinTex != null) {
                        int faceColor = ((int)(255 * finalItemAlpha) << 24) | 0xFFFFFF;
                        drawPlayerFace(guiGraphics, skinTex, (int)(xPos - 11), (int)yPos, 8, true, faceColor);
                    }
                }
            }

            // Draw player avatar for preview mode
            if (avatarGap > 0 && previewSkin != null) {
                int faceColor = ((int)(255 * finalItemAlpha) << 24) | 0xFFFFFF;
                drawPlayerFace(guiGraphics, previewSkin, (int)(xPos - avatarGap), (int)yPos, 8, true, faceColor);
            }

            guiGraphics.text(font, valText, (int)xPos, (int)yPos, itemColorWithAlpha);
            renderIndex++;
        }
    }

    private void updateInfoAnimation(DamageSessionManager session, Minecraft client) {
        long now = System.currentTimeMillis();
        float dt = infoLastUpdateMs == 0 ? 0.016f : (now - infoLastUpdateMs) / 1000.0f;
        dt = Mth.clamp(dt, 0.0f, 0.1f);
        infoLastUpdateMs = now;

        int candidateTargetId = session.isInfoActive() ? session.getLastTargetEntityId() : infoLastTargetId;

        LivingEntity target = null;
        if (candidateTargetId != -1 && client.level != null) {
            Entity e = client.level.getEntity(candidateTargetId);
            if (e instanceof LivingEntity le) {
                target = le;
            }
        }

        boolean shouldBeActive = session.isInfoActive() && target != null && target.isAlive();

        if (shouldBeActive) {
            boolean wasInactive = !infoHasSnapshot || infoAlpha <= 0.01f;
            boolean targetAvatar = target instanceof AbstractClientPlayer;

            int targetId = session.getLastTargetEntityId();
            if (targetId != infoLastTargetId) {
                if (!wasInactive && infoSmoothRatio >= 0) {
                    infoPrevSmoothRatio = infoSmoothRatio;
                    infoPrevLagRatio = infoLagRatio;
                    infoPrevHealRatio = infoHealRatio;
                    infoPrevDamageTailActive = infoDamageTailActive;
                    infoPrevPlayerSkin = infoPlayerSkin;
                    infoSwitching = true;
                    infoSwitchStartMs = now;
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

            captureInfoSnapshot(target);
            infoFading = false;
            infoAlpha = 1.0f;
            infoDeathDrain = false;
            if (wasInactive) {
                infoAvatarFactor = targetAvatar ? 1.0f : 0.0f;
                infoAvatarAlpha = infoAvatarFactor;
                infoPrevPlayerSkin = null;
            }
            return;
        }

        if (!infoDeathDrain && !infoFading && candidateTargetId != -1 && (target == null || !target.isAlive()) && (session.isInfoActive() || infoHasSnapshot)) {
            if (target != null) {
                captureInfoSnapshot(target);
            } else if (!infoHasSnapshot) {
                // The target entity is already gone before any snapshot was captured
                // (e.g. one-hit kill of an entity that was removed immediately, or an
                // out-of-view kill). There is no real info to display, so keep the
                // panel hidden instead of showing a misleading "0".
                session.clearInfo();
                return;
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
            float eps = 0.1f;
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
            infoAlpha = 1.0f - Mth.clamp(t, 0.0f, 1.0f);
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
        infoIsPlayer = target instanceof AbstractClientPlayer;
        if (infoIsPlayer) {
            infoPlayerSkin = ((AbstractClientPlayer) target).getSkin().body().texturePath();
        } else {
            infoPlayerSkin = null;
        }
        infoHealth = target.getHealth();
        float newMaxHealth = target.getMaxHealth();
        if (newMaxHealth > 0) {
            infoMaxHealth = newMaxHealth;
        } else if (infoMaxHealth <= 0) {
            infoMaxHealth = 1f;
        }
        infoAbsorption = target.getAbsorptionAmount();
    }

    /**
     * Returns the local player's skin texture for HUD preview rendering.
     * When in-game use the player entity directly; otherwise (e.g. preview opened
     * from the main menu) fall back to the SkinManager lookup via the game profile.
     */
    private static Identifier getPreviewSkin(Minecraft client) {
        if (client.player != null) {
            return client.player.getSkin().body().texturePath();
        }
        if (client.getSkinManager() != null && client.getGameProfile() != null) {
            try {
                return client.getSkinManager().createLookup(client.getGameProfile(), true).get().body().texturePath();
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }

    public void renderInfo(GuiGraphicsExtractor guiGraphics, DamageSessionManager session, boolean isPreview, float globalAlpha, Minecraft client) {
        float health;
        float maxHealth;
        float absorption;
        String fullName;
        boolean isPlayer;
        Identifier playerSkin;

        if (isPreview) {
            health = 10.0f;
            maxHealth = 20.0f;
            absorption = 0.0f;
            isPlayer = true;
            fullName = client.player != null ? client.player.getName().getString() : "Player";
            playerSkin = getPreviewSkin(client);
            infoAvatarFactor = 1.0f;
            infoAvatarAlpha = 1.0f;
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
        int maxHp = maxHealth > 0f ? Math.max(1, Math.round(maxHealth)) : 0;
        float ratio = maxHealth > 0f ? Mth.clamp(health / maxHealth, 0.0f, 1.0f) : 0.0f;

        int baseAlpha = (int)(255 * globalAlpha);
        if (baseAlpha < 5) return;

        long now = System.currentTimeMillis();
        float layoutDt = infoLayoutLastUpdateMs == 0 ? 0.016f : (now - infoLayoutLastUpdateMs) / 1000.0f;
        layoutDt = Mth.clamp(layoutDt, 0.0f, 0.1f);
        infoLayoutLastUpdateMs = now;

        float targetAvatar = isPlayer ? 1.0f : 0.0f;
        float avatarSmoothing = 1.0f - (float)Math.pow(0.0001, layoutDt);
        infoAvatarFactor += (targetAvatar - infoAvatarFactor) * avatarSmoothing;
        infoAvatarFactor = Mth.clamp(infoAvatarFactor, 0.0f, 1.0f);

        float targetAvatarAlpha = targetAvatar;
        infoAvatarAlpha += (targetAvatarAlpha - infoAvatarAlpha) * avatarSmoothing;
        infoAvatarAlpha = Mth.clamp(infoAvatarAlpha, 0.0f, 1.0f);

        int panelH = 30;

        int bgOpacity = Mth.clamp(DamageEngineConfig.getInstance().infoBackgroundOpacity, 0, 100);
        int bgBaseAlpha = (int)Math.round(255 * (bgOpacity / 100.0f));
        int bgAlpha = (int)Math.round(bgBaseAlpha * globalAlpha);
        if (globalAlpha < 0.99f && bgAlpha < 25) {
            bgAlpha = Math.min(bgBaseAlpha, 25);
        }
        if (infoFading) {
            bgAlpha = (int)(bgBaseAlpha * infoAlpha);
        }
        int bgColor = DamageEngineConfig.getInstance().infoBackgroundColor;
        int bg = (bgColor & 0x00FFFFFF) | (bgAlpha << 24);

        int barW = 80;
        int barX = -barW / 2;

        int avatarSize = 18;
        int avatarSlotFull = avatarSize + 3;
        float layoutAvatarFactor = infoAvatarFactor;
        int avatarSlot = (int)Math.round(avatarSlotFull * layoutAvatarFactor);
        int gap = 2;
        int leftPad = 2;
        int rightPad = 2;

        int contentLeft = barX - gap - avatarSlot;
        int baseX = contentLeft - leftPad;
        int baseRight = barX + barW + rightPad;
        int panelW = baseRight - baseX;
        int baseY = -panelH / 2;
        if (DamageEngineConfig.getInstance().infoNoRoundedBorder) {
            guiGraphics.fill(baseX - 2, baseY - 2, baseX + panelW + 2, baseY + panelH + 2, bg);
        } else {
            drawRoundedRect(guiGraphics, baseX - 2, baseY - 2, panelW + 4, panelH + 4, 3, bg);
        }

        Font font = client.font;

        int avatarDrawX = (int)Math.round(contentLeft + avatarSlot - avatarSlotFull);
        int avatarY = (int)Math.round(baseY + (panelH - avatarSize) / 2.0);

        Identifier skinToDraw = playerSkin;

        float switchT = 1.0f;
        if (infoSwitching) {
            switchT = (System.currentTimeMillis() - infoSwitchStartMs) / (float)INFO_SWITCH_MS;
            if (switchT >= 1.0f) {
                switchT = 1.0f;
                infoSwitching = false;
                infoPrevPlayerSkin = null;
            } else if (switchT < 0.0f) {
                switchT = 0.0f;
            }
        } else {
            switchT = 1.0f;
        }

        boolean isSameSkin = (infoPrevPlayerSkin != null && skinToDraw != null && infoPrevPlayerSkin.equals(skinToDraw));

        if (infoAvatarAlpha > 0.01f) {
            guiGraphics.pose().pushMatrix();

            if (isSameSkin || !infoSwitching) {
                if (skinToDraw != null) {
                    int faceAlpha = (int)(255 * infoAvatarAlpha * globalAlpha);
                    drawPlayerFace(guiGraphics, skinToDraw, avatarDrawX + 1, avatarY + 1, 16, true, (faceAlpha << 24) | 0xFFFFFF);
                }
            } else {
                if (switchT < 0.5f) {
                    if (infoPrevPlayerSkin != null) {
                        float localT = switchT * 2.0f;
                        float alpha = infoAvatarAlpha * (1.0f - localT) * globalAlpha;
                        int faceAlpha = (int)(255 * alpha);
                        drawPlayerFace(guiGraphics, infoPrevPlayerSkin, avatarDrawX + 1, avatarY + 1, 16, true, (faceAlpha << 24) | 0xFFFFFF);
                    }
                } else {
                    if (skinToDraw != null) {
                        float localT = (switchT - 0.5f) * 2.0f;
                        float alpha = infoAvatarAlpha * localT * globalAlpha;
                        int faceAlpha = (int)(255 * alpha);
                        drawPlayerFace(guiGraphics, skinToDraw, avatarDrawX + 1, avatarY + 1, 16, true, (faceAlpha << 24) | 0xFFFFFF);
                    }
                }
            }

            guiGraphics.pose().popMatrix();
        }

        int barH = 5;
        int barY = baseY + (panelH - barH) / 2;

        int textX = barX;
        int textGap = 3;
        int textYName = barY - textGap - font.lineHeight;
        int textYHp = barY + barH + textGap;
        int maxTextWidth = barW;

        String name = fullName;
        if (font.width(name) > maxTextWidth) {
            String raw = name;
            while (!raw.isEmpty() && font.width(raw + "\u2026") > maxTextWidth) {
                raw = raw.substring(0, raw.length() - 1);
            }
            name = raw.isEmpty() ? "" : raw + "\u2026";
        }

        int textColor = (0x00FFFFFF) | (baseAlpha << 24);
        guiGraphics.text(font, Component.literal(name), textX, textYName, textColor);

        int barBg = ((int)(128 * globalAlpha) << 24);
        boolean rounded = !DamageEngineConfig.getInstance().infoNoRoundedBorder;
        if (rounded) {
            drawRoundedRect(guiGraphics, barX, barY, barW, barH, barH / 2, barBg);
        } else {
            guiGraphics.fill(barX, barY, barX + barW, barY + barH, barBg);
        }

        float dt = infoHealthLastUpdateMs == 0 ? 0.016f : (now - infoHealthLastUpdateMs) / 1000.0f;
        infoHealthLastUpdateMs = now;
        dt = Mth.clamp(dt, 0.0f, 0.1f);

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

        infoSmoothRatio = Mth.clamp(infoSmoothRatio, 0.0f, 1.0f);
        infoLagRatio = Mth.clamp(infoLagRatio, 0.0f, 1.0f);
        infoHealRatio = Mth.clamp(infoHealRatio, 0.0f, 1.0f);
        int barColor = DamageEngineConfig.getInstance().infoBarColor;
        int healColorBase = DamageEngineConfig.getInstance().infoBarHealColor;
        int damageColorBase = DamageEngineConfig.getInstance().infoBarDamageColor;

        if (oldAlphaMul() > 0.01f && infoPrevSmoothRatio >= 0) {
            float oldMul = oldAlphaMul();
            float s = Mth.clamp(infoPrevSmoothRatio, 0.0f, 1.0f);
            float lag = Mth.clamp(infoPrevLagRatio, 0.0f, 1.0f);
            float heal = Mth.clamp(infoPrevHealRatio, 0.0f, 1.0f);
            int a = (int)(baseAlpha * oldMul);

            int fillW = (int)Math.floor(barW * s);
            int barColorWithAlpha = (barColor & 0x00FFFFFF) | (a << 24);
            drawBarFill(guiGraphics, barX, barY, fillW, barH, barColorWithAlpha, rounded);

            if (heal > s) {
                int healStart = barX + (int)Math.floor(barW * s);
                int healEnd = barX + (int)Math.floor(barW * heal);
                int healAlpha = (int)(a * 0.45f);
                int healColor = (healColorBase & 0x00FFFFFF) | (healAlpha << 24);
                drawBarSegment(guiGraphics, healStart, barY, healEnd - healStart, barH, healColor, rounded);
            }

            if (infoPrevDamageTailActive && lag > s) {
                int lagStart = barX + (int)Math.floor(barW * s);
                int lagEnd = barX + (int)Math.floor(barW * lag);
                int lagColor = (damageColorBase & 0x00FFFFFF) | (a << 24);
                drawBarSegment(guiGraphics, lagStart, barY, lagEnd - lagStart, barH, lagColor, rounded);
            }
        }

        if (newAlphaMul() > 0.01f) {
            float newMul = newAlphaMul();
            int a = (int)(baseAlpha * newMul);
            int fillW = (int)Math.floor(barW * infoSmoothRatio);
            int barColorWithAlpha = (barColor & 0x00FFFFFF) | (a << 24);
            drawBarFill(guiGraphics, barX, barY, fillW, barH, barColorWithAlpha, rounded);

            if (infoHealRatio > infoSmoothRatio) {
                int healStart = barX + (int)Math.floor(barW * infoSmoothRatio);
                int healEnd = barX + (int)Math.floor(barW * infoHealRatio);
                int healAlpha = (int)(a * 0.45f);
                int healColor = (healColorBase & 0x00FFFFFF) | (healAlpha << 24);
                drawBarSegment(guiGraphics, healStart, barY, healEnd - healStart, barH, healColor, rounded);
            }

            if (infoDamageTailActive && infoLagRatio > infoSmoothRatio) {
                int lagStart = barX + (int)Math.floor(barW * infoSmoothRatio);
                int lagEnd = barX + (int)Math.floor(barW * infoLagRatio);
                int lagColor = (damageColorBase & 0x00FFFFFF) | (a << 24);
                drawBarSegment(guiGraphics, lagStart, barY, lagEnd - lagStart, barH, lagColor, rounded);
                if (Math.abs(infoLagRatio - infoSmoothRatio) < 0.0025f) {
                    infoDamageTailActive = false;
                }
            }
        }

        boolean hasAbsorption = absorption > 0.01f;
        Identifier heartTexture = Identifier.withDefaultNamespace(hasAbsorption ? "hud/heart/absorbing_full" : "hud/heart/full");

        // When max health is unknown (e.g. target entity was already removed after being
        // killed) skip the hp number entirely — only the heart icon and draining bar remain,
        // so the HUD never shows a misleading "0" after defeating a mob.
        if (maxHp > 0) {
            Component hpText = Component.literal(hp + "/" + maxHp);
            guiGraphics.text(font, hpText, textX + 10, textYHp, textColor);
        }
        guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, Identifier.withDefaultNamespace("hud/heart/container"), textX, textYHp, 9, 9);
        guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, heartTexture, textX, textYHp, 9, 9);
    }

    private float oldAlphaMul() {
        if (infoSwitching) {
            return 1.0f - Mth.clamp((System.currentTimeMillis() - infoSwitchStartMs) / (float)INFO_SWITCH_MS, 0.0f, 1.0f);
        }
        return 0.0f;
    }

    private float newAlphaMul() {
        if (infoSwitching) {
            return Mth.clamp((System.currentTimeMillis() - infoSwitchStartMs) / (float)INFO_SWITCH_MS, 0.0f, 1.0f);
        }
        return 1.0f;
    }

    private String formatDamage(float damage, int decimalPlaces) {
        return DamageNumberFormat.formatDamage(damage, decimalPlaces);
    }

    private static void drawRoundedRect(GuiGraphicsExtractor guiGraphics, int x, int y, int w, int h, int radius, int color) {
        if (radius <= 0 || w < radius * 2 || h < radius * 2) {
            guiGraphics.fill(x, y, x + w, y + h, color);
            return;
        }
        guiGraphics.fill(x, y + radius, x + w, y + h - radius, color);
        for (int i = 0; i < radius; i++) {
            int indent = radius - i - 1;
            guiGraphics.fill(x + indent, y + i, x + w - indent, y + i + 1, color);
        }
        for (int i = 0; i < radius; i++) {
            int indent = radius - i - 1;
            guiGraphics.fill(x + indent, y + h - i - 1, x + w - indent, y + h - i, color);
        }
    }

    private static void drawBarFill(GuiGraphicsExtractor guiGraphics, int x, int y, int w, int h, int color, boolean rounded) {
        if (rounded && w > 0) {
            drawRoundedRect(guiGraphics, x, y, w, h, h / 2, color);
        } else {
            guiGraphics.fill(x, y, x + w, y + h, color);
        }
    }

    private static void drawBarSegment(GuiGraphicsExtractor guiGraphics, int x, int y, int w, int h, int color, boolean rounded) {
        if (!rounded || w <= 0 || h < 4) {
            guiGraphics.fill(x, y, x + w, y + h, color);
            return;
        }
        int r = h / 2;
        guiGraphics.fill(x, y + r, x + w, y + h - r, color);
        for (int i = 0; i < r; i++) {
            int indent = r - i - 1;
            guiGraphics.fill(x, y + i, x + w - indent, y + i + 1, color);
        }
        for (int i = 0; i < r; i++) {
            int indent = r - i - 1;
            guiGraphics.fill(x, y + h - i - 1, x + w - indent, y + h - i, color);
        }
    }

    private static void drawPlayerFace(GuiGraphicsExtractor guiGraphics, Identifier skinTexture, int x, int y, int size, boolean hatVisible, int color) {
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, skinTexture, x, y, 8.0f, 8.0f, size, size, 8, 8, 64, 64, color);
        if (hatVisible) {
            guiGraphics.blit(RenderPipelines.GUI_TEXTURED, skinTexture, x, y, 40.0f, 8.0f, size, size, 8, 8, 64, 64, color);
        }
    }
}
