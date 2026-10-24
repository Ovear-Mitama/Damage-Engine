package damage.engine;

import damage.engine.hud.format.DamageNumberFormat;
import damage.engine.hud.info.InfoPanelRenderer;
import damage.engine.hud.rating.RatingManager;
import damage.engine.hud.rating.RatingRenderer;
import damage.engine.hud.session.DamageSessionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;

import java.util.List;

/** 1.12.2 版 HUD：总伤害 / 连击 / 伤害记录 / 评分，带滚动与入场动画。 */
public final class DamageHudRenderer {
    private static final long TOTAL_ANIM_MS = 350;
    private static final long COMBO_POP_MS = 220;
    private static final long HISTORY_ANIM_MS = 188;
    private static final int HISTORY_SLOT_H = 10;

    private static float displayedTotal = 0f;
    private static float totalFrom = 0f;
    private static float totalTarget = 0f;
    private static long totalAnimStart = 0;
    private static int lastCombo = 0;
    private static long comboPopAt = 0;

    // HUD 惯性（与 1.21.1 相同的弹簧模型）
    private static final float INERTIA_GAIN = 0.22f;
    private static final float INERTIA_MOVE_GAIN = 4.0f;
    private static final float INERTIA_STIFFNESS = 220f;
    private static final float INERTIA_MAX_OFFSET = 6f;
    private static boolean inertiaInit;
    private static float inertiaPrevYaw;
    private static float inertiaPrevPitch;
    private static double inertiaPrevX;
    private static double inertiaPrevY;
    private static double inertiaPrevZ;
    private static float inertiaX;
    private static float inertiaY;
    private static float inertiaVelX;
    private static float inertiaVelY;
    private static long inertiaLastNs;
    private static boolean inertiaShift;
    private static float inertiaShiftX;
    private static float inertiaShiftY;

    // 最近一次绘制的总伤害模块包围盒（模块局部坐标），供 HUD 编辑器校准选框
    private static float totalMinX;
    private static float totalMinY;
    private static float totalMaxX;
    private static float totalMaxY;

    /** @return {x, y, width, height}（模块局部坐标）；未绘制过时返回 null。 */
    public static float[] getLastTotalBounds() {
        if (totalMaxX <= totalMinX) return null;
        return new float[]{totalMinX, totalMinY, totalMaxX - totalMinX, totalMaxY - totalMinY};
    }

    private DamageHudRenderer() {
    }

    public static void render(float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        DamageEngineConfig config = DamageEngineConfig.getInstance();
        if (mc.gameSettings.hideGUI) return;

        ScaledResolution resolution = new ScaledResolution(mc);
        int guiW = resolution.getScaledWidth();
        int guiH = resolution.getScaledHeight();

        // 第一次渲染时记录布局基准，之后窗口缩放整套 HUD 等比缩放
        if (config.hudLayoutBaseHeight <= 0f) {
            config.hudLayoutBaseWidth = guiW;
            config.hudLayoutBaseHeight = guiH;
        }

        // 固定的 2D 绘制状态，避免被其它 HUD 模块留下的深度/混合状态污染
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableDepth();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);

        updateInertia(mc, partialTicks);

        DamageSessionManager session = DamageSessionManager.getInstance();
        FontRenderer font = mc.fontRenderer;

        // 准心生物信息：按原版实体交互距离取准心目标
        if (config.crosshairEntityInfo) {
            InfoPanelRenderer.setCrosshairTarget(crosshairTargetId(mc));
        } else {
            InfoPanelRenderer.setCrosshairTarget(-1);
        }

        // 实体信息面板：不依赖「总伤害」开关
        InfoPanelRenderer.updateAnimation();
        float infoAlpha = InfoPanelRenderer.alpha();
        if (config.showInfo && infoAlpha > 0.01f) {
            float alpha = infoAlpha;
            drawModule(config, config.infoConfig, guiW, guiH,
                () -> InfoPanelRenderer.render(alpha));
        }

        // 「显示伤害」只控制总伤害块；跳字/评分不受它影响
        float globalAlpha = 1.0f;
        if (config.showDamage && config.resetEnabled && session.isActive()) {
            long now = config.hudNow();
            long timeSinceLast = now - session.getLastHitTime();
            long resetTimeMs = (long) (config.resetTime * 1000);
            if (timeSinceLast > resetTimeMs) {
                globalAlpha = 1.0f - (timeSinceLast - resetTimeMs) / 1000.0f;
                globalAlpha = clamp(globalAlpha, 0.0f, 1.0f);
            }
        }

        if (config.showDamage && config.showDamageDisplay && session.isActive()
            && globalAlpha > 0.01f) {
            final float contentAlpha = globalAlpha;
            drawModule(config, config.totalDamageConfig, guiW, guiH,
                () -> renderDamageContent(config, session, font, contentAlpha));
        } else {
            lastCombo = 0;
            // 模块隐藏时清掉进度条状态，避免下次出现时残留满格闪一下
            smoothProgress = 0f;
        }

        // 伤害记录独立绘制：即使会话已经结束，也让每一条按自身寿命慢慢淡出
        if (config.showDamage && config.showDamageDisplay && config.showDamageHistory
            && !session.getDamageHistory().isEmpty()) {
            drawModule(config, config.totalDamageConfig, guiW, guiH,
                () -> renderHistorySection(config, session, font));
        }

        RatingManager rating = RatingManager.getInstance();
        float alpha = rating.getFadeAlpha();
        if (alpha > 5f / 255f) {
            if (config.showRating) {
                drawModule(config, config.ratingConfig, guiW, guiH,
                    () -> RatingRenderer.renderGrade(false, alpha, null, 0));
            }
            if (config.showRatingScore) {
                drawModule(config, config.ratingScoreConfig, guiW, guiH,
                    () -> RatingRenderer.renderScore(false, alpha));
            }
        }

        DamageIndicator.render(partialTicks);

        if (config.debugShowPerformance) {
            String info = "DE " + Minecraft.getDebugFPS() + " FPS | "
                + "Indicators " + DamageIndicator.activeCount() + " | "
                + "HUD " + (config.hudMaxFps <= 0 ? "unlimited" : config.hudMaxFps);
            font.drawStringWithShadow(info, 4, 4, 0xFFB5F0C6);
        }
    }

    /** 准心指向的可交互生物 id；没有则 -1。 */
    private static int crosshairTargetId(Minecraft mc) {
        if (mc.player == null || mc.objectMouseOver == null) return -1;
        if (mc.objectMouseOver.typeOfHit != net.minecraft.util.math.RayTraceResult.Type.ENTITY) return -1;
        if (!(mc.objectMouseOver.entityHit instanceof net.minecraft.entity.EntityLivingBase)) return -1;
        net.minecraft.entity.EntityLivingBase target =
            (net.minecraft.entity.EntityLivingBase) mc.objectMouseOver.entityHit;
        // 原版实体交互距离约 3 格（创造模式 5 格）
        double reach = mc.player.capabilities.isCreativeMode ? 5.0 : 3.0;
        return mc.player.getDistance(target) <= reach ? target.getEntityId() : -1;
    }

    private static final String DAMAGE_LABEL = "damage";
    private static final float LABEL_SCALE = 0.9f;
    private static float smoothProgress = 1f;
    private static int lastComboCount = 0;
    private static boolean refilling = false;

    /** 编辑器预览：与游戏内共用同一套布局。 */
    public static void renderPreviewTotal() {
        Minecraft mc = Minecraft.getMinecraft();
        renderDamageBlock(DamageEngineConfig.getInstance(), mc.fontRenderer,
            1234.5f, 12, 0.7f, 1.0f, true, null);
    }

    /** 配置界面里的 HUD 预览：按当前模块位置把各模块画一遍。 */
    public static void renderPreviewModules() {
        Minecraft mc = Minecraft.getMinecraft();
        DamageEngineConfig config = DamageEngineConfig.getInstance();
        ScaledResolution resolution = new ScaledResolution(mc);
        int guiW = resolution.getScaledWidth();
        int guiH = resolution.getScaledHeight();

        inertiaShift = false;

        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);

        if (config.showDamageDisplay) {
            drawModule(config, config.totalDamageConfig, guiW, guiH,
                DamageHudRenderer::renderPreviewTotal);
        }
        if (config.showInfo) {
            drawModule(config, config.infoConfig, guiW, guiH, InfoPanelRenderer::renderPreview);
        }
        if (config.showRating) {
            String gradeText = "A";
            int gradeColor = 0xFFD700;
            if (config.ratingGrades != null && !config.ratingGrades.isEmpty()) {
                DamageEngineConfig.RatingGrade grade = config.ratingGrades.get(
                    (int) ((System.currentTimeMillis() / 1500) % config.ratingGrades.size()));
                gradeText = grade.text;
                gradeColor = grade.color;
            }
            final String text = gradeText;
            final int color = gradeColor;
            drawModule(config, config.ratingConfig, guiW, guiH,
                () -> RatingRenderer.renderGrade(true, 0.85f, text, color));
        }
        if (config.showRatingScore) {
            drawModule(config, config.ratingScoreConfig, guiW, guiH,
                () -> RatingRenderer.renderScore(true, 0.85f));
        }
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    /** 总伤害、连击与伤害记录共用一个模块，带动画。 */
    private static void renderDamageContent(DamageEngineConfig config, DamageSessionManager session,
                                            FontRenderer font, float globalAlpha) {
        renderDamageBlock(config, font, session.getTotalDamage(), session.getComboCount(),
            session.getRemainingTimeProgress(), globalAlpha, false, session);
    }

    /** 伤害记录单独绘制（不依赖会话是否仍在进行），保证每条都能按寿命淡出。 */
    private static void renderHistorySection(DamageEngineConfig config, DamageSessionManager session,
                                             FontRenderer font) {
        String anchorLabel = config.showCombo ? DAMAGE_LABEL + "  x99" : DAMAGE_LABEL;
        float half = font.getStringWidth(anchorLabel) * LABEL_SCALE / 2.0f;
        renderHistory(config, session, font, 1.0f, half);
    }

    private static void renderDamageBlock(DamageEngineConfig config, FontRenderer font,
                                          float target, int combo, float targetProgress,
                                          float globalAlpha, boolean isPreview,
                                          DamageSessionManager session) {
        // 总伤害数字不做逐字/滚动动画，直接显示当前累计值
        float shown = target;
        int color = thresholdColor(config, target);
        int alpha = (int) (255 * globalAlpha);
        int totalArgb = (color & 0x00FFFFFF) | (alpha << 24);

        boolean hasProgressBar = config.showProgressBar && config.resetEnabled;
        boolean showCombo = config.showCombo;
        // 固定参考块：为连击预留 "x99" 的空间，连击位数变化时整体不左右移动
        String anchorLabel = showCombo ? DAMAGE_LABEL + "  x99" : DAMAGE_LABEL;
        int anchorW = font.getStringWidth(anchorLabel);
        float contentHalfWidth = anchorW * LABEL_SCALE / 2.0f;

        // 标题 + 连击（连击右对齐到固定锚点右端）
        int labelY = hasProgressBar ? -15 : -12;
        GlStateManager.pushMatrix();
        GlStateManager.scale(LABEL_SCALE, LABEL_SCALE, 1.0f);
        font.drawStringWithShadow(DAMAGE_LABEL, -anchorW / 2.0f, labelY,
            (0xFFFFFF) | (alpha << 24));
        if (showCombo && combo > 0) {
            String comboText = "x" + combo;
            int comboArgb = (config.comboColor & 0x00FFFFFF) | (alpha << 24);
            font.drawStringWithShadow(comboText,
                anchorW / 2.0f - font.getStringWidth(comboText), labelY, comboArgb);
        }
        GlStateManager.popMatrix();

        // 重置倒计时进度条
        if (hasProgressBar) {
            if (isPreview || config.reduceAnimations) {
                smoothProgress = targetProgress;
            } else {
                // 倒计时本身由真实时间驱动，直接跟随即可保持逐帧平滑；
                // 命中回填（进度瞬间回到 1）用一个短的时间补间，避免硬跳
                if (targetProgress > smoothProgress + 0.002f) {
                    float dt = frameDeltaSeconds();
                    smoothProgress += (targetProgress - smoothProgress)
                        * (1.0f - (float) Math.exp(-16.0f * dt));
                    if (targetProgress - smoothProgress < 0.004f) {
                        smoothProgress = targetProgress;
                    }
                } else {
                    smoothProgress = targetProgress;
                }
            }
            smoothProgress = clamp(smoothProgress, 0.0f, 1.0f);

            int barY = -4;
            int bgAlpha = (int) (100 * globalAlpha);
            Gui.drawRect((int) -contentHalfWidth, barY, (int) contentHalfWidth, barY + 1,
                bgAlpha << 24);
            int barColor = 0xFF000000 | (config.progressBarColor & 0x00FFFFFF);
            Gui.drawRect((int) -contentHalfWidth, barY,
                (int) (-contentHalfWidth + contentHalfWidth * 2.0f * smoothProgress), barY + 1,
                barColor);
        }

        // 总伤害数字：右边缘（或左边缘）与固定锚点对齐
        String totalText = DamageNumberFormat.formatDamage(shown, config.decimalPlaces);
        float damageScale = 1.5f;
        GlStateManager.pushMatrix();
        GlStateManager.scale(damageScale, damageScale, 1.0f);
        int textWidth = font.getStringWidth(totalText);
        float rightEdge = contentHalfWidth / damageScale;
        boolean alignLeft = "left".equals(config.alignMode);
        float textX = alignLeft ? -rightEdge : (rightEdge - textWidth);
        font.drawStringWithShadow(totalText, textX, 0.0f, totalArgb);
        GlStateManager.popMatrix();

        if (config.showDamageHistory && isPreview) {
            renderPreviewHistory(config, font, contentHalfWidth);
        }

        // 记录实际内容的包围盒：编辑器用它画选框，避免框比内容大或小
        float boundMinX = -contentHalfWidth;
        float boundMaxX = contentHalfWidth;
        if (alignLeft) {
            boundMaxX = Math.max(boundMaxX, -contentHalfWidth + textWidth * damageScale);
        } else {
            boundMinX = Math.min(boundMinX, contentHalfWidth - textWidth * damageScale);
        }
        float boundMinY = Math.min(labelY * LABEL_SCALE, 0f);
        float boundMaxY = font.FONT_HEIGHT * damageScale;
        if (config.showDamageHistory) {
            int lines;
            if (isPreview) {
                lines = Math.min(5, Math.max(1, config.historyLimit));
            } else {
                lines = session == null ? 1
                    : Math.max(1, Math.min(config.historyLimit, session.getDamageHistory().size()));
            }
            boundMaxY = Math.max(boundMaxY,
                15 + (lines - 1) * HISTORY_SLOT_H + font.FONT_HEIGHT);
        }
        totalMinX = boundMinX;
        totalMinY = boundMinY;
        totalMaxX = boundMaxX;
        totalMaxY = boundMaxY;
    }

    private static void renderPreviewHistory(DamageEngineConfig config, FontRenderer font,
                                             float half) {
        int baseY = 15;
        int count = Math.min(5, Math.max(1, config.historyLimit));
        for (int i = 0; i < count; i++) {
            String text = DamageNumberFormat.formatDamage(12.3f + i * 2.1f,
                config.historyDecimalPlaces);
            int color = i == 0 ? config.critColor : config.normalColor;
            font.drawStringWithShadow(text, half - font.getStringWidth(text),
                baseY + i * HISTORY_SLOT_H, color & 0x00FFFFFF);
        }
    }

    private static void renderHistory(DamageEngineConfig config, DamageSessionManager session,
                                      FontRenderer font, float globalAlpha, float half) {
        List<DamageSessionManager.DamageEntry> history = session.getDamageHistory();
        if (history.isEmpty()) return;

        long now = config.hudNow();
        long newestTs = history.get(history.size() - 1).timestamp();
        int batchCount = 0;
        for (int i = history.size() - 1; i >= 0; i--) {
            if (history.get(i).timestamp() != newestTs) break;
            batchCount++;
        }
        if (batchCount < 1) batchCount = 1;
        float settle = config.reduceAnimations
            ? 1.0f : clamp((now - newestTs) / (float) HISTORY_ANIM_MS, 0.0f, 1.0f);

        long lifeMs = (long) (config.historyDisappearanceTime * 1000);
        int limit = Math.max(1, config.historyLimit);
        boolean alignLeft = "left".equals(config.alignMode);
        int drawn = 0;

        for (int i = history.size() - 1; i >= 0 && drawn <= limit; i--) {
            DamageSessionManager.DamageEntry entry = history.get(i);
            long age = now - entry.timestamp();

            float itemAlpha = config.reduceAnimations
                ? 1.0f : easeOutCubic(clamp(age / (float) HISTORY_ANIM_MS, 0.0f, 1.0f));
            if (lifeMs > 0) {
                // 寿命最后一段逐条渐隐（最多 2 秒，短寿命时按 1/3 取）
                long fadeMs = Math.max(400L, Math.min(2000L, lifeMs / 3));
                long fadeStart = Math.max(0, lifeMs - fadeMs);
                if (age > fadeStart) {
                    itemAlpha *= clamp((lifeMs - age) / (float) fadeMs, 0.0f, 1.0f);
                }
            }
            if (drawn >= limit) {
                // 多画的那一条是被挤出列表的记录：随本批入场动画一起淡出，而不是瞬间消失
                itemAlpha *= clamp(1.0f - settle, 0.0f, 1.0f);
            }
            // 1.12.2 的 FontRenderer 在 alpha < 4 时会强制改为不透明，
            // 所以接近 0 时直接不画，避免淡出末尾闪一下
            if (itemAlpha <= 0.03f) {
                drawn++;
                continue;
            }
            int lineAlpha = (int) (255 * itemAlpha * globalAlpha);
            if (lineAlpha < 8) {
                drawn++;
                continue;
            }

            int entryColor = entry.isCrit() ? config.critColor : config.normalColor;
            int lineArgb = (entryColor & 0x00FFFFFF) | (lineAlpha << 24);
            String text = DamageNumberFormat.formatDamage(entry.damage(), config.historyDecimalPlaces);
            float x = alignLeft ? -half : half - font.getStringWidth(text);
            // 自上而下：整列从上方 batchCount 格落到目标位，老条目正好从下移前的原位开始
            float targetY = 15.0f + drawn * HISTORY_SLOT_H;
            float y = settle >= 1.0f ? targetY
                : targetY - (1.0f - settle) * batchCount * HISTORY_SLOT_H;
            font.drawStringWithShadow(text, x, y, lineArgb);
            drawn++;
        }
    }

    private static float updateTotal(float target, boolean preview) {
        long now = DamageEngineConfig.getInstance().hudNow();
        if (preview) {
            displayedTotal = target;
            totalTarget = target;
            totalFrom = target;
            totalAnimStart = 0;
            return displayedTotal;
        }
        if (totalTarget < 0f || target < totalTarget - 0.01f) {
            // 会话重置 / 伤害清空：直接归零
            displayedTotal = target;
            totalFrom = target;
            totalTarget = target;
            totalAnimStart = 0;
        } else if (target > totalTarget + 0.01f) {
            totalFrom = displayedTotal;
            totalTarget = target;
            totalAnimStart = now;
        }

        if (totalAnimStart > 0) {
            float t = clamp((now - totalAnimStart) / (float) TOTAL_ANIM_MS, 0.0f, 1.0f);
            displayedTotal = totalFrom + (totalTarget - totalFrom) * easeOutCubic(t);
            if (t >= 1.0f) {
                displayedTotal = totalTarget;
                totalFrom = totalTarget;
                totalAnimStart = 0;
            }
        }
        return displayedTotal;
    }

    private static float easeOutCubic(float t) {
        float u = 1.0f - t;
        return 1.0f - u * u * u;
    }

    private static float clamp(float value, float min, float max) {
        return value < min ? min : (value > max ? max : value);
    }

    private static long frameLastNs;

    /** 当前渲染帧的时长（秒），用于与帧率无关的补间。 */
    private static float frameDeltaSeconds() {
        long now = System.nanoTime();
        float dt = frameLastNs == 0L ? 0.016f
            : Math.min(0.1f, (now - frameLastNs) / 1.0E9f);
        frameLastNs = now;
        return dt;
    }

    /** 根据相机转动 / 移动给 HUD 施加一个反向的弹簧偏移。 */
    private static void updateInertia(Minecraft mc, float partialTicks) {
        DamageEngineConfig config = DamageEngineConfig.getInstance();
        long now = System.nanoTime();
        float dt = inertiaLastNs == 0 ? 0.016f
            : Math.min(0.1f, (now - inertiaLastNs) / 1.0E9f);
        inertiaLastNs = now;

        float strength = clamp(config.hudInertiaStrength, 0f, 200f) / 100f;
        boolean active = !config.reduceAnimations
            && !"off".equals(config.hudInertiaMode)
            && (config.hudMaxFps <= 0 || config.hudMaxFps >= 30)
            && mc.player != null;

        if (!active) {
            inertiaInit = false;
        } else {
            Entity view = mc.getRenderViewEntity();
            if (view != null) {
                float yaw = view.prevRotationYaw
                    + (view.rotationYaw - view.prevRotationYaw) * partialTicks;
                float pitch = view.prevRotationPitch
                    + (view.rotationPitch - view.prevRotationPitch) * partialTicks;
                double px = view.lastTickPosX + (view.posX - view.lastTickPosX) * partialTicks;
                double py = view.lastTickPosY + (view.posY - view.lastTickPosY) * partialTicks;
                double pz = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * partialTicks;

                if (inertiaInit) {
                    float dYaw = net.minecraft.util.math.MathHelper.wrapDegrees(yaw - inertiaPrevYaw);
                    float dPitch = pitch - inertiaPrevPitch;
                    if (Math.abs(dYaw) < 90f && Math.abs(dPitch) < 90f) {
                        inertiaX -= dYaw * INERTIA_GAIN * strength;
                        inertiaY -= dPitch * INERTIA_GAIN * strength;
                    }
                    double dx = px - inertiaPrevX;
                    double dy = py - inertiaPrevY;
                    double dz = pz - inertiaPrevZ;
                    if (dx * dx + dy * dy + dz * dz < 16.0) {
                        // 世界位移转到视角系：右向量与 forward × up 一致（取反），
                        // 否则横向让位会跟移动方向同向
                        double yawRad = Math.toRadians(yaw);
                        double rx = -Math.cos(yawRad);
                        double rz = -Math.sin(yawRad);
                        double lateral = dx * rx + dz * rz;
                        inertiaX -= (float) lateral * INERTIA_MOVE_GAIN * strength;
                        inertiaY += (float) dy * INERTIA_MOVE_GAIN * strength;
                    }
                }
                inertiaPrevYaw = yaw;
                inertiaPrevPitch = pitch;
                inertiaPrevX = px;
                inertiaPrevY = py;
                inertiaPrevZ = pz;
                inertiaInit = true;
            }
        }

        float maxOffset = INERTIA_MAX_OFFSET * strength;
        float damp = 2.0f * (float) Math.sqrt(INERTIA_STIFFNESS);
        inertiaVelX += (-INERTIA_STIFFNESS * inertiaX - damp * inertiaVelX) * dt;
        inertiaVelY += (-INERTIA_STIFFNESS * inertiaY - damp * inertiaVelY) * dt;
        inertiaX = clamp(inertiaX + inertiaVelX * dt, -maxOffset, maxOffset);
        inertiaY = clamp(inertiaY + inertiaVelY * dt, -maxOffset, maxOffset);

        if (Math.abs(inertiaX) < 0.02f && Math.abs(inertiaVelX) < 0.02f) {
            inertiaX = 0f;
            inertiaVelX = 0f;
        }
        if (Math.abs(inertiaY) < 0.02f && Math.abs(inertiaVelY) < 0.02f) {
            inertiaY = 0f;
            inertiaVelY = 0f;
        }

        inertiaShift = inertiaX != 0f || inertiaY != 0f;
        inertiaShiftX = inertiaX;
        inertiaShiftY = inertiaY;
    }

    private static void drawModule(DamageEngineConfig config, DamageEngineConfig.ModuleConfig module,
                                   int guiW, int guiH, Runnable action) {
        if (!module.enabled) return;

        float scale = config.resolveHudScale(module, guiH);
        int x = config.resolveHudX(module, guiW, guiH);
        int y = config.resolveHudY(module, guiH);

        GlStateManager.pushMatrix();
        GlStateManager.translate(x + (inertiaShift ? inertiaShiftX : 0f),
            y + (inertiaShift ? inertiaShiftY : 0f), 0.0f);
        GlStateManager.scale(scale, scale, 1.0f);
        // 每个模块都重置混合状态，保证 alpha 淡入淡出生效
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        action.run();
        GlStateManager.popMatrix();
    }

    private static int thresholdColor(DamageEngineConfig config, float value) {
        int color = 0xFFFFFF;
        if (config.damageThresholds == null) return color;
        for (DamageEngineConfig.DamageThreshold threshold : config.damageThresholds) {
            if (threshold != null && value >= threshold.threshold) {
                color = threshold.color & 0xFFFFFF;
            }
        }
        return color;
    }
}
