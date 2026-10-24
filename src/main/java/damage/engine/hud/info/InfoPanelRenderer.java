package damage.engine.hud.info;

import damage.engine.DamageEngineConfig;
import damage.engine.hud.session.DamageSessionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;

/**
 * 1.12.2 版实体信息面板：头像（玩家皮肤 / 3D 实体模型）、名称、血量条与血量数字。
 * <p>
 * 面板绘制在模块原点（0,0）附近，位置与缩放由 HUD 的模块变换负责。
 */
public final class InfoPanelRenderer {
    private static final long INFO_FADE_MS = 500;
    private static final long INFO_SWITCH_MS = 500;
    private static final ResourceLocation ICONS = new ResourceLocation("textures/gui/icons.png");

    private static int infoLastTargetId = -1;
    private static int crosshairTargetId = -1;
    private static EntityLivingBase infoAvatarEntity = null;
    private static float infoSmoothRatio = -1f;
    private static float infoLagRatio = -1f;
    private static float infoHealRatio = -1f;
    private static boolean infoLagHold = false;
    private static boolean infoHealHold = false;
    private static float infoAvatarFactor = 0f;
    private static float infoAvatarAlpha = 0f;
    private static boolean infoSwitching = false;
    private static long infoSwitchStartMs = 0;
    private static float infoPrevSmoothRatio = -1f;
    private static float infoPrevLagRatio = -1f;
    private static float infoPrevHealRatio = -1f;
    private static boolean infoPrevDamageTailActive = false;
    private static long infoLayoutLastUpdateMs = 0;
    private static long infoLastUpdateMs = 0;
    private static long infoHealthLastUpdateMs = 0;
    private static float infoAlpha = 0f;
    private static boolean infoFading = false;
    private static long infoFadeStartMs = 0;
    private static boolean infoDeathDrain = false;
    private static boolean infoDamageTailActive = false;
    private static boolean infoDamageTailPending = false;
    private static boolean infoHasSnapshot = false;
    private static String infoName = "";
    private static boolean infoIsPlayer = false;
    private static ResourceLocation infoResourceLocation = null;
    private static ResourceLocation infoPrevResourceLocation = null;
    private static float infoHealth = 0f;
    private static float infoMaxHealth = 1f;
    private static float infoAbsorption = 0f;

    private InfoPanelRenderer() {
    }

    /** 当前面板整体透明度（0 = 不显示）。 */
    public static float alpha() {
        return infoAlpha;
    }

    /** 准心指向的生物（-1 = 没有）；只在与准心生物信息同时开启时生效。 */
    public static void setCrosshairTarget(int entityId) {
        crosshairTargetId = entityId;
    }

    private static float clamp(float value, float min, float max) {
        return value < min ? min : (value > max ? max : value);
    }

    /** 每帧更新面板状态：目标切换、死亡排空、淡入淡出。 */
    public static void updateAnimation() {
        Minecraft mc = Minecraft.getMinecraft();
        DamageSessionManager session = DamageSessionManager.getInstance();
        DamageEngineConfig config = DamageEngineConfig.getInstance();
        long now = config.hudNow();
        float dt = infoLastUpdateMs == 0 ? 0.016f : (now - infoLastUpdateMs) / 1000.0f;
        dt = clamp(dt, 0.0f, 0.1f);
        infoLastUpdateMs = now;

        int candidateTargetId = session.isInfoActive() ? session.getLastTargetEntityId() : infoLastTargetId;

        boolean crosshairActive = config.crosshairEntityInfo && crosshairTargetId >= 0;
        if (crosshairActive) {
            candidateTargetId = crosshairTargetId;
        }

        EntityLivingBase target = null;
        if (candidateTargetId != -1 && mc.world != null) {
            Entity e = mc.world.getEntityByID(candidateTargetId);
            if (e instanceof EntityLivingBase) {
                target = (EntityLivingBase) e;
            }
        }

        boolean shouldBeActive = (session.isInfoActive() || crosshairActive)
            && target != null && target.isEntityAlive();

        if (shouldBeActive) {
            boolean wasInactive = !infoHasSnapshot || infoAlpha <= 0.01f;
            boolean targetAvatar = target instanceof EntityPlayer;

            int targetId = crosshairActive ? crosshairTargetId : session.getLastTargetEntityId();
            if (targetId != infoLastTargetId) {
                if (!wasInactive && infoSmoothRatio >= 0) {
                    infoPrevSmoothRatio = infoSmoothRatio;
                    infoPrevLagRatio = infoLagRatio;
                    infoPrevHealRatio = infoHealRatio;
                    infoPrevDamageTailActive = infoDamageTailActive;
                    infoPrevResourceLocation = infoResourceLocation;
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
                // 头像槽是否需要存在：非玩家实体在开启实体渲染时同样有 3D 头像，
                // 否则玩家↔生物切换时头像槽会塌陷再展开一遍
                boolean hasAvatar = targetAvatar || DamageEngineConfig.getInstance().entityRenderEnabled;
                infoAvatarFactor = hasAvatar ? 1.0f : 0.0f;
                infoAvatarAlpha = infoAvatarFactor;
            }

            captureSnapshot(target);
            infoFading = false;
            infoAlpha = 1.0f;
            infoDeathDrain = false;
            if (wasInactive) {
                // 面板本来就没显示时头像槽直接按最终状态展开，不播放"左侧变宽"动画
                boolean hasAvatar = targetAvatar
                    || (DamageEngineConfig.getInstance().entityRenderEnabled && infoAvatarEntity != null);
                infoAvatarFactor = hasAvatar ? 1.0f : 0.0f;
                infoAvatarAlpha = infoAvatarFactor;
                infoPrevResourceLocation = null;
            }
            return;
        }

        if (!infoDeathDrain && !infoFading && candidateTargetId != -1
            && (target == null || !target.isEntityAlive())
            && (session.isInfoActive() || infoHasSnapshot)) {
            if (target != null) {
                captureSnapshot(target);
            } else if (!infoHasSnapshot) {
                infoFading = true;
                infoFadeStartMs = now;
                infoLastTargetId = -1;
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
            float t = (now - infoFadeStartMs) / (float) INFO_FADE_MS;
            infoAlpha = 1.0f - clamp(t, 0.0f, 1.0f);
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

    private static void captureSnapshot(EntityLivingBase target) {
        infoHasSnapshot = true;
        infoName = target.getName();
        infoIsPlayer = target instanceof EntityPlayer;
        if (infoIsPlayer) {
            infoResourceLocation = target instanceof AbstractClientPlayer
                ? ((AbstractClientPlayer) target).getLocationSkin() : null;
            infoAvatarEntity = null;
        } else {
            infoResourceLocation = null;
            // 非玩家实体：保留引用以便直接渲染 3D 头像（死亡淡出期间仍可绘制）
            infoAvatarEntity = target;
        }
        infoHealth = target.getHealth();
        float newMaxHealth = target.getMaxHealth();
        if (newMaxHealth > 0) {
            infoMaxHealth = newMaxHealth;
        }
        infoAbsorption = target.getAbsorptionAmount();
    }

    /** 在当前模块坐标系里绘制面板（模块原点 = 血条中心线的中点附近）。 */
    public static void render(float globalAlpha) {
        if (!infoHasSnapshot || globalAlpha <= 0.01f) return;
        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer font = mc.fontRenderer;

        float health = infoHealth;
        float maxHealth = Math.max(infoMaxHealth, 0.01f);
        float absorption = infoAbsorption;
        boolean isPlayer = infoIsPlayer;

        int hp = Math.max(0, Math.round(health + absorption));
        int maxHp = Math.max(1, Math.round(maxHealth));
        float ratio = clamp(health / maxHealth, 0.0f, 1.0f);

        int baseAlpha = (int) (255 * globalAlpha);
        if (baseAlpha < 5) return;

        long now = System.currentTimeMillis();
        float layoutDt = infoLayoutLastUpdateMs == 0 ? 0.016f : (now - infoLayoutLastUpdateMs) / 1000.0f;
        layoutDt = clamp(layoutDt, 0.0f, 0.1f);
        infoLayoutLastUpdateMs = now;

        DamageEngineConfig config = DamageEngineConfig.getInstance();
        boolean entityRenderEnabled = config.entityRenderEnabled;

        // 玩家恒有头像（皮肤脸）；非玩家实体在开启实体渲染且有目标时展开头像槽
        float targetAvatar = (isPlayer || (entityRenderEnabled && infoAvatarEntity != null)) ? 1.0f : 0.0f;
        float avatarSmoothing = 1.0f - (float) Math.pow(0.0001, layoutDt);
        infoAvatarFactor += (targetAvatar - infoAvatarFactor) * avatarSmoothing;
        infoAvatarFactor = clamp(infoAvatarFactor, 0.0f, 1.0f);

        infoAvatarAlpha += (targetAvatar - infoAvatarAlpha) * avatarSmoothing;
        infoAvatarAlpha = clamp(infoAvatarAlpha, 0.0f, 1.0f);

        int panelH = 30;

        int bgOpacity = Math.max(0, Math.min(100, config.infoBackgroundOpacity));
        int bgBaseAlpha = (int) Math.round(255 * (bgOpacity / 100.0f));
        int bgAlpha = (int) Math.round(bgBaseAlpha * globalAlpha);
        if (globalAlpha < 0.99f && bgAlpha < 25) {
            bgAlpha = Math.min(bgBaseAlpha, 25);
        }
        if (infoFading) {
            bgAlpha = (int) (bgBaseAlpha * infoAlpha);
        }
        int bgColor = config.infoBackgroundColor;
        int bg = (bgColor & 0x00FFFFFF) | (bgAlpha << 24);

        int barW = 80;
        int barX = -barW / 2;

        int avatarSize = 24;
        int avatarSlotFull = avatarSize + 3;
        int avatarSlot = (int) Math.round(avatarSlotFull * infoAvatarFactor);
        int gap = 2;
        int leftPad = 2;
        int rightPad = 2;

        int contentLeft = barX - gap - avatarSlot;
        int baseX = contentLeft - leftPad;
        int baseRight = barX + barW + rightPad;
        int panelW = baseRight - baseX;
        int baseY = -panelH / 2;

        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        if (config.infoNoRoundedBorder) {
            Gui.drawRect(baseX - 2, baseY - 2, baseX + panelW + 2, baseY + panelH + 2, bg);
        } else {
            drawRoundedRect(baseX - 2, baseY - 2, panelW + 4, panelH + 4, 3, bg);
        }

        int avatarDrawX = (int) Math.round(contentLeft + avatarSlot - avatarSlotFull);
        int avatarY = (int) Math.round(baseY + (panelH - avatarSize) / 2.0);
        int faceSize = 16;
        int faceOffset = (avatarSize - faceSize) / 2;

        float switchT;
        if (infoSwitching) {
            switchT = (System.currentTimeMillis() - infoSwitchStartMs) / (float) INFO_SWITCH_MS;
            if (switchT >= 1.0f) {
                switchT = 1.0f;
                infoSwitching = false;
                infoPrevResourceLocation = null;
            } else if (switchT < 0.0f) {
                switchT = 0.0f;
            }
        } else {
            switchT = 1.0f;
        }

        ResourceLocation skinToDraw = infoResourceLocation;
        boolean isSameSkin = infoPrevResourceLocation != null && skinToDraw != null
            && infoPrevResourceLocation.equals(skinToDraw);

        if (infoAvatarAlpha > 0.01f) {
            if (isPlayer) {
                if (isSameSkin || !infoSwitching) {
                    if (skinToDraw != null) {
                        drawFace(mc, skinToDraw, avatarDrawX + faceOffset, avatarY + faceOffset,
                            faceSize, infoAvatarAlpha * globalAlpha);
                    }
                } else if (switchT < 0.5f) {
                    if (infoPrevResourceLocation != null) {
                        float alpha = infoAvatarAlpha * (1.0f - switchT * 2.0f) * globalAlpha;
                        drawFace(mc, infoPrevResourceLocation, avatarDrawX + faceOffset, avatarY + faceOffset,
                            faceSize, alpha);
                    }
                } else if (skinToDraw != null) {
                    float alpha = infoAvatarAlpha * ((switchT - 0.5f) * 2.0f) * globalAlpha;
                    drawFace(mc, skinToDraw, avatarDrawX + faceOffset, avatarY + faceOffset, faceSize, alpha);
                }
            } else if (infoAvatarEntity != null && entityRenderEnabled) {
                // 非玩家实体：直接渲染 3D 模型（允许溢出头像槽，本段先于文本与血条绘制）
                EntityIconRenderer.render(infoAvatarEntity, avatarDrawX, avatarY, avatarSize,
                    infoAvatarAlpha * globalAlpha,
                    "follow".equals(config.entityRenderRotation), config.entityRenderRotationAngle);
            }
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        }

        int barH = 5;
        int barY = baseY + (panelH - barH) / 2;

        int textX = barX;
        int textGap = 3;
        int textYName = barY - textGap - font.FONT_HEIGHT;
        int textYHp = barY + barH + textGap;
        int maxTextWidth = barW;

        String name = infoName == null ? "" : infoName;
        if (font.getStringWidth(name) > maxTextWidth) {
            while (!name.isEmpty() && font.getStringWidth(name + "\u2026") > maxTextWidth) {
                name = name.substring(0, name.length() - 1);
            }
            name = name.isEmpty() ? "" : name + "\u2026";
        }

        int textColor = (0x00FFFFFF) | (baseAlpha << 24);
        if (baseAlpha > 4) {
            font.drawStringWithShadow(name, textX, textYName, textColor);
        }

        int barBg = ((int) (128 * globalAlpha) << 24);
        boolean rounded = !config.infoNoRoundedBorder;
        if (rounded) {
            drawRoundedRect(barX, barY, barW, barH, barH / 2, barBg);
        } else {
            Gui.drawRect(barX, barY, barX + barW, barY + barH, barBg);
        }

        float dt = infoHealthLastUpdateMs == 0 ? 0.016f : (now - infoHealthLastUpdateMs) / 1000.0f;
        infoHealthLastUpdateMs = now;
        dt = clamp(dt, 0.0f, 0.1f);

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

            float downSmoothing = 1.0f - (float) Math.pow(0.001, dt);
            float upSmoothing = 1.0f - (float) Math.pow(0.05, dt);
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

            if (infoLagHold && Math.abs(infoSmoothRatio - ratio) < 0.0025f) {
                infoLagHold = false;
            }

            float tailSmoothing = 1.0f - (float) Math.pow(0.02, dt);
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

        infoSmoothRatio = clamp(infoSmoothRatio, 0.0f, 1.0f);
        infoLagRatio = clamp(infoLagRatio, 0.0f, 1.0f);
        infoHealRatio = clamp(infoHealRatio, 0.0f, 1.0f);

        int barColor = config.infoBarColor;
        int healColorBase = config.infoBarHealColor;
        int damageColorBase = config.infoBarDamageColor;

        if (oldAlphaMul() > 0.01f && infoPrevSmoothRatio >= 0) {
            float oldMul = oldAlphaMul();
            float s = clamp(infoPrevSmoothRatio, 0.0f, 1.0f);
            float lag = clamp(infoPrevLagRatio, 0.0f, 1.0f);
            float heal = clamp(infoPrevHealRatio, 0.0f, 1.0f);
            int a = (int) (baseAlpha * oldMul);

            int fillW = (int) Math.floor(barW * s);
            drawBarRange(barX, barY, barW, barH, barX, barX + fillW,
                (barColor & 0x00FFFFFF) | (a << 24), rounded);

            if (heal > s) {
                int healStart = barX + (int) Math.floor(barW * s);
                int healEnd = barX + (int) Math.floor(barW * heal);
                int healColor = (healColorBase & 0x00FFFFFF) | ((int) (a * 0.45f) << 24);
                drawBarRange(barX, barY, barW, barH, healStart, healEnd, healColor, rounded);
            }

            if (infoPrevDamageTailActive && lag > s) {
                int lagStart = barX + (int) Math.floor(barW * s);
                int lagEnd = barX + (int) Math.floor(barW * lag);
                int lagColor = (damageColorBase & 0x00FFFFFF) | (a << 24);
                drawBarRange(barX, barY, barW, barH, lagStart, lagEnd, lagColor, rounded);
            }
        }

        if (newAlphaMul() > 0.01f) {
            float newMul = newAlphaMul();
            int a = (int) (baseAlpha * newMul);
            int fillW = (int) Math.floor(barW * infoSmoothRatio);
            drawBarRange(barX, barY, barW, barH, barX, barX + fillW,
                (barColor & 0x00FFFFFF) | (a << 24), rounded);

            if (infoHealRatio > infoSmoothRatio) {
                int healStart = barX + (int) Math.floor(barW * infoSmoothRatio);
                int healEnd = barX + (int) Math.floor(barW * infoHealRatio);
                int healColor = (healColorBase & 0x00FFFFFF) | ((int) (a * 0.45f) << 24);
                drawBarRange(barX, barY, barW, barH, healStart, healEnd, healColor, rounded);
            }

            if (infoDamageTailActive && infoLagRatio > infoSmoothRatio) {
                int lagStart = barX + (int) Math.floor(barW * infoSmoothRatio);
                int lagEnd = barX + (int) Math.floor(barW * infoLagRatio);
                int lagColor = (damageColorBase & 0x00FFFFFF) | (a << 24);
                drawBarRange(barX, barY, barW, barH, lagStart, lagEnd, lagColor, rounded);
                if (Math.abs(infoLagRatio - infoSmoothRatio) < 0.0025f) {
                    infoDamageTailActive = false;
                }
            }
        }

        if (baseAlpha > 4) {
            boolean hasAbsorption = absorption > 0.01f;
            font.drawStringWithShadow(hp + "/" + maxHp, textX + 10, textYHp, textColor);

            mc.getTextureManager().bindTexture(ICONS);
            GlStateManager.color(1.0f, 1.0f, 1.0f, globalAlpha);
            // 心形容器（u=16,v=0）与满心（u=52,v=0 / 吸收 u=160,v=0）
            Gui.drawModalRectWithCustomSizedTexture(textX, textYHp, 16, 0, 9, 9, 256.0f, 256.0f);
            if (hasAbsorption) {
                Gui.drawModalRectWithCustomSizedTexture(textX, textYHp, 160, 0, 9, 9, 256.0f, 256.0f);
            } else {
                Gui.drawModalRectWithCustomSizedTexture(textX, textYHp, 52, 0, 9, 9, 256.0f, 256.0f);
            }
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        }
    }

    /**
     * HUD 编辑器用的静态预览：用本地玩家显示一个满状态的样板面板。
     * <p>只用于编辑器，不需要恢复这些状态；关闭编辑器后正常的
     * {@link #updateAnimation()} 会把面板淡出。</p>
     */
    public static void renderPreview() {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.player;
        if (player == null) return;

        infoHasSnapshot = true;
        infoName = player.getName();
        infoIsPlayer = true;
        infoResourceLocation = player instanceof AbstractClientPlayer
            ? ((AbstractClientPlayer) player).getLocationSkin() : null;
        infoAvatarEntity = null;
        infoHealth = 10.0f;
        infoMaxHealth = 20.0f;
        infoAbsorption = 0.0f;
        infoAlpha = 1.0f;
        infoFading = false;
        infoDeathDrain = false;
        infoSwitching = false;
        infoSmoothRatio = 0.5f;
        infoLagRatio = 0.5f;
        infoHealRatio = 0.5f;
        infoAvatarFactor = 1.0f;
        infoAvatarAlpha = 1.0f;
        render(1.0f);
    }

    private static void drawFace(Minecraft mc, ResourceLocation skin, int x, int y, int size, float alpha) {
        if (alpha <= 0.01f) return;
        mc.getTextureManager().bindTexture(skin);
        GlStateManager.color(1.0f, 1.0f, 1.0f, alpha);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        Gui.drawScaledCustomSizeModalRect(x, y, 8.0f, 8.0f, 8, 8, size, size, 64.0f, 64.0f);
        Gui.drawScaledCustomSizeModalRect(x, y, 40.0f, 8.0f, 8, 8, size, size, 64.0f, 64.0f);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private static float oldAlphaMul() {
        if (infoSwitching) {
            return 1.0f - clamp((System.currentTimeMillis() - infoSwitchStartMs) / (float) INFO_SWITCH_MS, 0.0f, 1.0f);
        }
        return 0.0f;
    }

    private static float newAlphaMul() {
        if (infoSwitching) {
            return clamp((System.currentTimeMillis() - infoSwitchStartMs) / (float) INFO_SWITCH_MS, 0.0f, 1.0f);
        }
        return 1.0f;
    }

    private static void drawRoundedRect(int x, int y, int w, int h, int radius, int color) {
        if (radius <= 0 || w < radius * 2 || h < radius * 2) {
            Gui.drawRect(x, y, x + w, y + h, color);
            return;
        }
        Gui.drawRect(x, y + radius, x + w, y + h - radius, color);
        for (int i = 0; i < radius; i++) {
            int indent = radius - i - 1;
            Gui.drawRect(x + indent, y + i, x + w - indent, y + i + 1, color);
        }
        for (int i = 0; i < radius; i++) {
            int indent = radius - i - 1;
            Gui.drawRect(x + indent, y + h - i - 1, x + w - indent, y + h - i, color);
        }
    }

    /** 在圆角血条里填一段区间；只有贴到血条两端的段才吃圆角。 */
    private static void drawBarRange(int barX, int barY, int barW, int barH,
                                     int fromX, int toX, int color, boolean rounded) {
        int x0 = Math.max(fromX, barX);
        int x1 = Math.min(toX, barX + barW);
        if (x1 <= x0) return;
        if (!rounded || barH < 4) {
            Gui.drawRect(x0, barY, x1, barY + barH, color);
            return;
        }
        int r = barH / 2;
        for (int i = 0; i < barH; i++) {
            int inset;
            if (i < r) {
                inset = r - i - 1;
            } else if (i >= barH - r) {
                inset = r - (barH - 1 - i) - 1;
            } else {
                inset = 0;
            }
            int rowX0 = Math.max(x0, barX + inset);
            int rowX1 = Math.min(x1, barX + barW - inset);
            if (rowX1 > rowX0) {
                Gui.drawRect(rowX0, barY + i, rowX1, barY + i + 1, color);
            }
        }
    }
}
