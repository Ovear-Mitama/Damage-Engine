package damage.engine.hud;

import damage.engine.DamageEngineClient;
import damage.engine.DamageEngineConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import com.mojang.blaze3d.vertex.PoseStack;
import damage.engine.compat.GuiGraphics;
import damage.engine.compat.PlayerFaceRenderer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


public class DamageHud {
    /** HUD 渲染共用实例:惯性/整层偏移等状态需要跨 mixin 共享 */
    public static final DamageHud INSTANCE = new DamageHud();

    private float smoothProgress = 0f;
    private boolean isRefilling = false;
    private int lastComboCount = 0;
    private int infoLastTargetId = -1;
    /** 非玩家追踪目标:用于直接渲染 3D 头像 */
    private LivingEntity infoAvatarEntity = null;
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
    private ResourceLocation infoResourceLocation = null;
    private ResourceLocation infoPrevResourceLocation = null;
    private float infoHealth = 0f;
    private float infoMaxHealth = 1f;
    private float infoAbsorption = 0f;


    private static final long HISTORY_ANIM_MS = 188;
    /** 伤害记录每一条占用的行高。 */
    private static final int HISTORY_SLOT_H = 10;
    private float contentRightX = 20f;
    private String previewGrade = "";
    private int previewGradeColor = 0xFFFFFFFF;

    /** 没有玩家实体时(主菜单里开预览)用的兜底皮肤 UUID。 */
    private static final UUID FALLBACK_SKIN_UUID = new UUID(0L, 0L);

    /**
     * Get the player's skin texture. Uses in-game player if available, otherwise the vanilla
     * default skin.
     * <p>
     * 默认皮肤路径各版本不同(1.18/1.19.2 是 {@code textures/entity/steve.png},1.19.3 起改成
     * {@code textures/entity/player/wide/steve.png}),写死会在不匹配的版本上变成缺失贴图
     * —— 预览里的信息头像会整块发紫。这里交由 {@link DefaultPlayerSkin} 按版本返回。
     */
    private static ResourceLocation getPlayerSkin(Minecraft client) {
        if (client.player != null) {
            ResourceLocation skin = client.player.getSkinTextureLocation();
            if (skin != null) {
                return skin;
            }
        }
        return DefaultPlayerSkin.getDefaultSkin(FALLBACK_SKIN_UUID);
    }

    public void cyclePreviewGrades() {
        List<DamageEngineConfig.RatingGrade> grades = DamageEngineConfig.getInstance().ratingGrades;
        if (grades != null && !grades.isEmpty()) {
            int idx = (int)((System.currentTimeMillis() / 1500) % grades.size());
            previewGrade = grades.get(idx).text;
            previewGradeColor = grades.get(idx).color;
        }
    }

    public void onHudRender(PoseStack pose, float partialTick) {
        GuiGraphics guiGraphics = GuiGraphics.of(pose);
        try {
            Minecraft client = Minecraft.getInstance();
            if (client.screen instanceof damage.engine.client.gui.DamageConfigScreen) return;
            if (client.screen instanceof damage.engine.client.gui.HudEditorScreen) return;

            DamageEngineConfig config = DamageEngineConfig.getInstance();

            if (config.hideOnF1 && client.options.hideGui) return;

            // TaCZ's crosshair hit feedback enables depth test / blend and never
            // restores them, so HUD draws that run after it (ours included) inherit
            // polluted state and flicker in sync with the crosshair. Force a stable
            // 2D state for every module rendered below, and keep it that way for the
            // rest of the frame.
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
            com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

            DamageSessionManager session = DamageSessionManager.getInstance();

            updateInfoAnimation(session, client);
            if (config.showInfo && infoAlpha > 0.01f) {
                float a = infoAlpha;
                renderModule(pose, config.infoConfig, client, a, () -> {
                    renderInfo(pose, session, false, a, client);
                });
            }

            if (!config.showDamage) return;
            if (!config.showDamageDisplay) {
                return;
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
                    renderDamageContent(pose, session.getTotalDamage(), session.getComboCount(),
                        session.getRemainingTimeProgress(), session.getDamageHistory(), false, globalAlpha);
                }
            } else {
                lastComboCount = 0;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void renderPreview(PoseStack pose, int centerX, int centerY) {
        GuiGraphics guiGraphics = GuiGraphics.of(pose);
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

        renderDamageContent(pose, total, combo, progress, history, true, 1.0f);

        if (config.showInfo) {
            renderModule(pose, config.infoConfig, client, 1.0f, () -> {
                renderInfo(pose, null, true, 1.0f, client);
            });
        }
    }

    private void renderDamageContent(PoseStack pose, float total, int combo, float targetProgress, List<DamageSessionManager.DamageEntry> history, boolean isPreview, float globalAlpha) {
        GuiGraphics guiGraphics = GuiGraphics.of(pose);
        DamageEngineConfig config = DamageEngineConfig.getInstance();
        Minecraft client = Minecraft.getInstance();

        RatingManager rm = RatingManager.getInstance();
        if ((isPreview && config.showRating) || (!isPreview && config.showRating && rm.isVisible())) {
            renderModule(pose, config.ratingConfig, client, globalAlpha, () -> {
                renderRating(pose, isPreview, globalAlpha, client);
            });
        }

        if (!isPreview || config.showDamageDisplay) {
            renderModule(pose, config.totalDamageConfig, client, globalAlpha, () -> {
                renderTotalDamage(pose, total, targetProgress, isPreview, globalAlpha, combo, client);

                if (config.showDamageHistory) {
                    renderHistory(pose, history, isPreview, globalAlpha, client);
                }
            });
        }
    }

    public void renderRating(PoseStack pose, boolean isPreview, float globalAlpha, Minecraft client) {
        GuiGraphics guiGraphics = GuiGraphics.of(pose);
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

                    ResourceLocation texId = new ResourceLocation("damage-engine", "rating_" + imagePath);
                    net.minecraft.client.renderer.texture.DynamicTexture dynamicTexture = new net.minecraft.client.renderer.texture.DynamicTexture(nativeImage);
                    client.getTextureManager().register(texId, dynamicTexture);

                    guiGraphics.pose().pushPose();
                    guiGraphics.pose().scale(0.15f, 0.15f, 1.0f);

                    com.mojang.blaze3d.systems.RenderSystem.enableBlend();
                    com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
                    guiGraphics.setColor(1.0f, 1.0f, 1.0f, (float)rAlpha / 255.0f);

                    int imgWidth = nativeImage.getWidth();
                    int imgHeight = nativeImage.getHeight();
                    guiGraphics.blit(texId, -imgWidth / 2, -imgHeight / 2, 0, 0, imgWidth, imgHeight, imgWidth, imgHeight);

                    guiGraphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
                    com.mojang.blaze3d.systems.RenderSystem.disableBlend();

                    guiGraphics.pose().popPose();
                } catch (Exception e) {
                    DamageEngineClient.LOGGER.error("Failed to load rating image: " + e.getMessage());
                    float rs = 1.2f;
                    guiGraphics.pose().pushPose();
                    guiGraphics.pose().scale(rs, rs, 1.0f);
                    int gw = font.width(ratingText);
                    guiGraphics.drawString(font, ratingText, -gw / 2, -font.lineHeight / 2, rColor);
                    guiGraphics.pose().popPose();
                }
            } else {
                float rs = 1.2f;
                guiGraphics.pose().pushPose();
                guiGraphics.pose().scale(rs, rs, 1.0f);
                int gw = font.width(ratingText);
                guiGraphics.drawString(font, ratingText, -gw / 2, -font.lineHeight / 2, rColor);
                guiGraphics.pose().popPose();
            }
        }
    }

    public void renderModule(PoseStack pose, DamageEngineConfig.ModuleConfig moduleConfig, Minecraft client, float globalAlpha, Runnable renderAction) {
        GuiGraphics guiGraphics = GuiGraphics.of(pose);
        if (!moduleConfig.enabled) return;

        int x = moduleConfig.x == -1.0f ? client.getWindow().getGuiScaledWidth() / 2 : (int)(moduleConfig.x * client.getWindow().getGuiScaledWidth());
        int y = moduleConfig.y == -1.0f ? client.getWindow().getGuiScaledHeight() / 2 : (int)(moduleConfig.y * client.getWindow().getGuiScaledHeight());

        // 屏幕坐标系里的子项必须自己带上偏移;pose 里是否再叠一次看模式——"整层 HUD"
        // 模式已由外层统一偏移,这里不再叠加。用浮点不用取整,否则位移会被量化成一格一格。
        this.moduleScreenX = x + inertiaX;
        this.moduleScreenY = y + inertiaY;
        this.moduleScreenScale = moduleConfig.scale;

        float inertiaOffX = applyOwnInertiaOffset() ? inertiaX : 0f;
        float inertiaOffY = applyOwnInertiaOffset() ? inertiaY : 0f;

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x + inertiaOffX, y + inertiaOffY, 0);
        guiGraphics.pose().scale(moduleConfig.scale, moduleConfig.scale, 1.0f);

        renderAction.run();

        guiGraphics.pose().popPose();
    }

    // ---- HUD 惯性:视角转动/移动时整层 HUD 先朝反方向让一点,再靠临界阻尼弹簧平滑回正 ----

    /** 视角每转 1 度,HUD 反向让出多少像素。 */
    private static final float INERTIA_GAIN = 0.22f;
    /** 每移动 1 格,HUD 反向让出多少像素。 */
    private static final float INERTIA_MOVE_GAIN = 4.0f;
    /** 弹簧刚度,越大回正越快。 */
    private static final float INERTIA_STIFFNESS = 220f;
    /** 最大让位距离(像素)。 */
    private static final float INERTIA_MAX_OFFSET = 6f;

    private float inertiaX = 0f;
    private float inertiaY = 0f;
    private float inertiaVelX = 0f;
    private float inertiaVelY = 0f;
    private float inertiaPrevYaw = 0f;
    private float inertiaPrevPitch = 0f;
    private Vec3 inertiaPrevPos = null;
    private long inertiaLastMs = 0L;
    private boolean inertiaPrevInit = false;
    /** 整层偏移当前是否已经压进 HUD 的矩阵栈 */
    private boolean globalShiftActive = false;
    /** 已压进矩阵栈的偏移量,用于给整屏特效临时抵消 */
    private float globalShiftX = 0f;
    private float globalShiftY = 0f;
    /** 模块在屏幕坐标系里的落点(含惯性偏移),供不吃 pose 的子项使用。 */
    private float moduleScreenX = 0f;
    private float moduleScreenY = 0f;
    private float moduleScreenScale = 1f;

    /** 只让 DE 自己的 HUD 让位时才在模块变换里叠偏移;"整层 HUD"模式由外层统一偏移。 */
    private static boolean applyOwnInertiaOffset() {
        return "de_only".equals(DamageEngineConfig.getInstance().hudInertiaMode);
    }

    /** 当前惯性强度倍率:100 = 默认幅度。 */
    private static float inertiaStrength() {
        return Mth.clamp(DamageEngineConfig.getInstance().hudInertiaStrength, 0, 200) / 100f;
    }

    /**
     * 每帧更新惯性偏移。朝反方向的位移由本帧视角变化量与位移量直接推动,回正交给弹簧,
     * 所以转动视角或移动时 HUD 会先让开、随后自己平滑归位且不过冲。
     */
    public void updateHudInertia() {
        Minecraft client = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        float rawDt = inertiaLastMs == 0L ? 0.016f : (now - inertiaLastMs) / 1000.0f;
        inertiaLastMs = now;
        // 上一帧隔太久说明 HUD 这段时间没渲染(F1 隐藏/切界面),当作重新开始,免得一开就甩一下
        if (rawDt > 0.25f) {
            inertiaPrevInit = false;
        }
        float dt = Mth.clamp(rawDt, 1f / 240f, 0.1f);

        Camera camera = client.level == null ? null : client.gameRenderer.getMainCamera();
        if (camera == null || "off".equals(DamageEngineConfig.getInstance().hudInertiaMode)) {
            // 关掉或还没进世界:本帧不再推动,只让弹簧把残余偏移收回去
            inertiaPrevInit = false;
        } else {
            // 取相机朝向/位置而不是 player.getYRot():后者一个 tick 才更新一次,
            // 按帧读会变成"一跳一跳",看起来卡顿,单帧 delta 也大得离谱。
            float yaw = camera.getYRot();
            float pitch = camera.getXRot();
            Vec3 pos = camera.getPosition();
            float strength = inertiaStrength();
            if (inertiaPrevInit) {
                float dYaw = Mth.wrapDegrees(yaw - inertiaPrevYaw);
                float dPitch = pitch - inertiaPrevPitch;
                // 一帧转 90 度以上只可能是传送/切维度,不当玩家操作
                if (Math.abs(dYaw) < 90f && Math.abs(dPitch) < 90f) {
                    // 向右转 yaw 增大、向下看 pitch 增大,HUD 要往屏幕反方向走,所以是减
                    inertiaX -= dYaw * INERTIA_GAIN * strength;
                    inertiaY -= dPitch * INERTIA_GAIN * strength;
                }
                if (inertiaPrevPos != null) {
                    Vec3 delta = pos.subtract(inertiaPrevPos);
                    // 一帧走 4 格以上(含飞行/坐骑)只可能是传送
                    if (delta.lengthSqr() < 16.0) {
                        // 把世界位移转到视角坐标系:此时 x 是左右、y 是上下,
                        // 往哪边走 HUD 就往屏幕反方向让。视角系 y 向上、GUI 的 y 向下,故 y 取加号。
                        // 1.18 没有 JOML,用 com.mojang.math.Vector3f,transform 是原地变换不返回新对象。
                        com.mojang.math.Vector3f v = new com.mojang.math.Vector3f((float) delta.x, (float) delta.y, (float) delta.z);
                        v.transform(camera.rotation());
                        inertiaX -= v.x() * INERTIA_MOVE_GAIN * strength;
                        inertiaY += v.y() * INERTIA_MOVE_GAIN * strength;
                    }
                }
            }
            inertiaPrevYaw = yaw;
            inertiaPrevPitch = pitch;
            inertiaPrevPos = pos;
            inertiaPrevInit = true;
        }

        float maxOffset = INERTIA_MAX_OFFSET * inertiaStrength();
        float damp = 2f * (float) Math.sqrt(INERTIA_STIFFNESS);
        inertiaVelX += (-INERTIA_STIFFNESS * inertiaX - damp * inertiaVelX) * dt;
        inertiaVelY += (-INERTIA_STIFFNESS * inertiaY - damp * inertiaVelY) * dt;
        inertiaX = Mth.clamp(inertiaX + inertiaVelX * dt, -maxOffset, maxOffset);
        inertiaY = Mth.clamp(inertiaY + inertiaVelY * dt, -maxOffset, maxOffset);

        if (Math.abs(inertiaX) < 0.02f && Math.abs(inertiaVelX) < 0.02f) {
            inertiaX = 0f;
            inertiaVelX = 0f;
        }
        if (Math.abs(inertiaY) < 0.02f && Math.abs(inertiaVelY) < 0.02f) {
            inertiaY = 0f;
            inertiaVelY = 0f;
        }
    }

    /**
     * "整层 HUD"模式下,把偏移压到整个 HUD 的矩阵栈上,让原版和其它模组的 HUD 一起让位。
     * 偏移为 0 时不压栈,常态下没有任何额外开销。需与 {@link #endGlobalShift} 成对调用。
     */
    public void beginGlobalShift(PoseStack pose) {
        GuiGraphics guiGraphics = GuiGraphics.of(pose);
        globalShiftActive = false;
        globalShiftX = 0f;
        globalShiftY = 0f;
        if (!"all".equals(DamageEngineConfig.getInstance().hudInertiaMode)) return;
        if (inertiaX == 0f && inertiaY == 0f) return;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(inertiaX, inertiaY, 0);
        globalShiftX = inertiaX;
        globalShiftY = inertiaY;
        globalShiftActive = true;
    }

    public void endGlobalShift(PoseStack pose) {
        GuiGraphics guiGraphics = GuiGraphics.of(pose);
        if (globalShiftActive) {
            guiGraphics.pose().popPose();
            globalShiftActive = false;
            globalShiftX = 0f;
            globalShiftY = 0f;
        }
    }

    /**
     * 临时抵消整层偏移,供整屏特效(暗角、传送门、望远镜)使用。
     * <p>
     * 那些是铺满整屏的渐变,属于"屏幕特效"而不是 HUD;整屏渐变被平移几像素时,
     * 人的感知是"整个屏幕在晃",比 HUD 移动明显得多,所以它们不参与让位。
     * 必须与 {@link #resumeGlobalShift} 成对调用。
     */
    public void suspendGlobalShift(PoseStack pose) {
        GuiGraphics guiGraphics = GuiGraphics.of(pose);
        if (!globalShiftActive) return;
        guiGraphics.pose().translate(-globalShiftX, -globalShiftY, 0);
    }

    public void resumeGlobalShift(PoseStack pose) {
        GuiGraphics guiGraphics = GuiGraphics.of(pose);
        if (!globalShiftActive) return;
        guiGraphics.pose().translate(globalShiftX, globalShiftY, 0);
    }

    /** 总伤害与伤害记录的对齐:默认靠右(数字右边缘对齐),靠左时改为左边缘对齐。 */
    private static boolean isAlignLeft() {
        return "left".equals(DamageEngineConfig.getInstance().alignMode);
    }

    public void renderTotalDamage(PoseStack pose, float total, float targetProgress, boolean isPreview, float globalAlpha, int combo, Minecraft client) {
        GuiGraphics guiGraphics = GuiGraphics.of(pose);
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

        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(labelScale, labelScale, 1.0f);
        int fullLabelWidth = font.width(fullLabel);
        guiGraphics.pose().popPose();

        float contentHalfWidth = (fullLabelWidth * labelScale) / 2.0f;
        contentRightX = contentHalfWidth;

        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(labelScale, labelScale, 1.0f);
        int lblW = font.width(damageLabel);
        // Label shifts down when progress bar is hidden
        boolean hasProgressBar = DamageEngineConfig.getInstance().showProgressBar && DamageEngineConfig.getInstance().resetEnabled;
        int labelY = hasProgressBar ? -15 : -12;
        if (showCombo && combo > 0) {
            guiGraphics.drawString(font, damageLabel, -fullLabelWidth / 2, labelY, labelColor);
            String comboText = "x" + combo;
            int comboColorCfg = DamageEngineConfig.getInstance().comboColor;
            int comboColor = (comboColorCfg & 0x00FFFFFF) | (labelAlpha << 24);
            guiGraphics.drawString(font, comboText, fullLabelWidth / 2 - font.width(comboText), labelY, comboColor);
        } else {
            guiGraphics.drawString(font, damageLabel, -fullLabelWidth / 2 + (fullLabelWidth - lblW), labelY, labelColor);
        }
        guiGraphics.pose().popPose();

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

            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(barX, barY, 0);
            guiGraphics.pose().scale(barWidth * smoothProgress, (float)barHeight, 1.0f);
            guiGraphics.fill(0, 0, 1, 1, barColor);
            guiGraphics.pose().popPose();
        }

        float damageScale = 1.5f;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(damageScale, damageScale, 1.0f);
        int textWidth = font.width(totalText);
        float rightEdgeInScale = contentHalfWidth / damageScale;
        float textX = isAlignLeft() ? -rightEdgeInScale : (rightEdgeInScale - textWidth);
        guiGraphics.drawString(font, totalText, (int)textX, 0, colorWithAlpha);
        guiGraphics.pose().popPose();
    }

    public void renderHistory(PoseStack pose, List<DamageSessionManager.DamageEntry> history, boolean isPreview, float globalAlpha, Minecraft client) {
        GuiGraphics guiGraphics = GuiGraphics.of(pose);
        Font font = client.font;
        int limit = DamageEngineConfig.getInstance().historyLimit;
        int decimalPlaces = DamageEngineConfig.getInstance().historyDecimalPlaces;
        boolean recordOtherPlayers = DamageEngineConfig.getInstance().recordOtherPlayers;

        int renderIndex = 0;
        List<DamageSessionManager.DamageEntry> renderList = (history != null) ? new java.util.ArrayList<>(history) : java.util.Collections.emptyList();

        long now = System.currentTimeMillis();

        // 与最新条目同一时间刻到达的算一批:它们作为一整组自上而下滑入,
        // 整列老条目也一次整体下移对应的格数(而不是每来一条就各自动一下)。
        long newestTs = renderList.isEmpty() ? now : renderList.get(renderList.size() - 1).timestamp();
        int batchCount = 0;
        for (int i = renderList.size() - 1; i >= 0; i--) {
            if (renderList.get(i).timestamp() != newestTs) break;
            batchCount++;
        }
        if (batchCount < 1) batchCount = 1;
        float settleProgress = isPreview ? 1.0f
            : Mth.clamp((now - newestTs) / (float)HISTORY_ANIM_MS, 0.0f, 1.0f);

        int baseY = 15;

        // Player avatar for preview
        ResourceLocation previewSkin = null;
        if (isPreview) {
            previewSkin = getPlayerSkin(client);
        }
        int avatarGap = (isPreview && previewSkin != null && recordOtherPlayers) ? 11 : 0;

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

            // 只有还在自己入场窗口内的条目做淡入。
            // 按条目自己的年龄算而不是按整批的进度算,否则一条已经在淡入的条目
            // 会因为随后又来了一条而把进度重置,表现为"闪一下又消失"。
            float entryAlphaMul = 1.0f;
            if (!isPreview) {
                float ownProgress = Mth.clamp((now - entry.timestamp()) / (float)HISTORY_ANIM_MS, 0.0f, 1.0f);
                if (ownProgress < 1.0f) entryAlphaMul = ownProgress;
            }

            finalItemAlpha *= globalAlpha * entryAlphaMul;
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

            float targetY = baseY + renderIndex * HISTORY_SLOT_H;
            // 自上而下:整列从"上方 batchCount 格"落到目标位。
            // 于是新的一批是从列表顶边上方落下来的,而老条目正好从它们下移前的原位开始,
            // 一次整体下移 batchCount 格。
            float yPos = settleProgress >= 1.0f
                ? targetY
                : Mth.lerp(settleProgress, targetY - batchCount * HISTORY_SLOT_H, targetY);

            // 靠右:每行的右边缘对齐;靠左:左边缘对齐
            boolean alignLeft = isAlignLeft();
            float xPos = alignLeft ? -contentRightX : (contentRightX - textWidth);
            // 头像始终挂在数字外侧(靠右时在左、靠左时在右),别压到数字上
            int avatarX = alignLeft ? (int)(xPos + textWidth + 2) : (int)(xPos - 11);

            // Draw the OTHER player's avatar next to their damage entry (own entries get no avatar)
            if (!isPreview && recordOtherPlayers && entry.attackerId() > 0 && client.level != null) {
                Entity attackerEntity = client.level.getEntity(entry.attackerId());
                if (attackerEntity instanceof AbstractClientPlayer attackerPlayer) {
                    ResourceLocation skin = attackerPlayer.getSkinTextureLocation();
                    if (skin != null) {
                        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, finalItemAlpha);
                        // drawString's y is the text top; align the avatar's top with it
                        PlayerFaceRenderer.draw(pose, skin, avatarX, (int)yPos, 8);
                        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                    }
                }
            }

            // Draw player avatar for preview mode
            if (avatarGap > 0 && previewSkin != null) {
                com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, finalItemAlpha);
                int previewAvatarX = alignLeft ? (int)(xPos + textWidth + 2) : (int)(xPos - avatarGap);
                PlayerFaceRenderer.draw(pose, previewSkin, previewAvatarX, (int)yPos, 8);
                com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            }

            guiGraphics.drawString(font, valText, (int)xPos, (int)yPos, itemColorWithAlpha);
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
                // Update avatar state when target changes
                infoAvatarFactor = targetAvatar ? 1.0f : 0.0f;
                infoAvatarAlpha = infoAvatarFactor;
            }

            captureInfoSnapshot(target);
            infoFading = false;
            infoAlpha = 1.0f;
            infoDeathDrain = false;
            if (wasInactive) {
                infoPrevResourceLocation = null;
            }
            return;
        }

        if (!infoDeathDrain && !infoFading && candidateTargetId != -1 && (target == null || !target.isAlive()) && (session.isInfoActive() || infoHasSnapshot)) {
            if (target != null) {
                captureInfoSnapshot(target);
            } else if (!infoHasSnapshot) {
                // 目标已从世界移除且此前从未显示过:没有可显示的 HP 信息。
                // 不再硬编码 "0/1",直接淡出。
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
            infoResourceLocation = ((AbstractClientPlayer) target).getSkinTextureLocation();
            infoAvatarEntity = null;
        } else {
            infoResourceLocation = null;
            // 非玩家实体:保留引用以便直接渲染 3D 头像(死亡淡出期间仍可绘制)
            infoAvatarEntity = target;
        }
        infoHealth = target.getHealth();
        float newMaxHealth = target.getMaxHealth();
        // 只在拿到有效最大值时更新;死亡实体的 getMaxHealth() 可能返回 0,
        // 保留上次有效值,避免血条数字退化成 "0/1"。
        if (newMaxHealth > 0) {
            infoMaxHealth = newMaxHealth;
        }
        infoAbsorption = target.getAbsorptionAmount();
    }

    public void renderInfo(PoseStack pose, DamageSessionManager session, boolean isPreview, float globalAlpha, Minecraft client) {
        GuiGraphics guiGraphics = GuiGraphics.of(pose);
        float health;
        float maxHealth;
        float absorption;
        String fullName;
        boolean isPlayer;
        ResourceLocation playerSkin;

        if (isPreview) {
            health = 10.0f;
            maxHealth = 20.0f;
            absorption = 0.0f;
            isPlayer = true;
            fullName = client.player != null ? client.player.getName().getString() : "Player";
            playerSkin = getPlayerSkin(client);
            infoAvatarFactor = 1.0f;
            infoAvatarAlpha = 1.0f;
            infoSwitching = false;
        } else {
            if (!infoHasSnapshot) return;
            health = infoHealth;
            maxHealth = infoMaxHealth;
            absorption = infoAbsorption;
            fullName = infoName;
            isPlayer = infoIsPlayer;
            playerSkin = infoResourceLocation;
        }

        int hp = Math.max(0, Math.round(health + absorption));
        int maxHp = Math.max(1, Math.round(maxHealth));
        float ratio = Mth.clamp(health / maxHealth, 0.0f, 1.0f);

        int baseAlpha = (int)(255 * globalAlpha);
        if (baseAlpha < 5) return;

        long now = System.currentTimeMillis();
        float layoutDt = infoLayoutLastUpdateMs == 0 ? 0.016f : (now - infoLayoutLastUpdateMs) / 1000.0f;
        layoutDt = Mth.clamp(layoutDt, 0.0f, 0.1f);
        infoLayoutLastUpdateMs = now;

        // 玩家恒有头像(皮肤脸);非玩家实体在开启实体渲染且有目标时展开头像槽
        boolean entityRenderEnabled = DamageEngineConfig.getInstance().entityRenderEnabled;
        float targetAvatar = (isPlayer || (entityRenderEnabled && infoAvatarEntity != null)) ? 1.0f : 0.0f;
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

        int avatarSize = 24;
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
            drawRoundedRect(pose, baseX - 2, baseY - 2, panelW + 4, panelH + 4, 3, bg);
        }

        Font font = client.font;

        int avatarDrawX = (int)Math.round(contentLeft + avatarSlot - avatarSlotFull);
        int avatarY = (int)Math.round(baseY + (panelH - avatarSize) / 2.0);
        // 玩家脸固定 16px,在更大的头像槽内居中
        int faceSize = 16;
        int faceOffset = (avatarSize - faceSize) / 2;

        ResourceLocation skinToDraw = playerSkin;

        float switchT = 1.0f;
        if (infoSwitching) {
            switchT = (System.currentTimeMillis() - infoSwitchStartMs) / (float)INFO_SWITCH_MS;
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

        boolean isSameSkin = (infoPrevResourceLocation != null && skinToDraw != null && infoPrevResourceLocation.equals(skinToDraw));

        if (infoAvatarAlpha > 0.01f) {
            guiGraphics.pose().pushPose();

            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
            com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();

            if (isPlayer) {
                if (isSameSkin || !infoSwitching) {
                    if (skinToDraw != null) {
                        float fade = infoAvatarAlpha * globalAlpha;
                        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, fade);
                        PlayerFaceRenderer.draw(pose, skinToDraw, avatarDrawX + faceOffset, avatarY + faceOffset, faceSize);
                    }
                } else {
                    if (switchT < 0.5f) {
                        if (infoPrevResourceLocation != null) {
                            float localT = switchT * 2.0f;
                            float alpha = infoAvatarAlpha * (1.0f - localT) * globalAlpha;
                            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha);
                            PlayerFaceRenderer.draw(pose, infoPrevResourceLocation, avatarDrawX + faceOffset, avatarY + faceOffset, faceSize);
                        }
                    } else {
                        if (skinToDraw != null) {
                            float localT = (switchT - 0.5f) * 2.0f;
                            float alpha = infoAvatarAlpha * localT * globalAlpha;
                            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha);
                            PlayerFaceRenderer.draw(pose, skinToDraw, avatarDrawX + faceOffset, avatarY + faceOffset, faceSize);
                        }
                    }
                }
            } else if (infoAvatarEntity != null && entityRenderEnabled) {
                // 非玩家实体:直接渲染 3D 模型(3D 模型需要深度测试,此处临时开启)
                float fade = infoAvatarAlpha * globalAlpha;
                boolean followRotation = "follow".equals(DamageEngineConfig.getInstance().entityRenderRotation);
                int rotationAngle = DamageEngineConfig.getInstance().entityRenderRotationAngle;
                // 不做边缘裁剪:模型允许溢出头像槽,本段先于下方文本与血条绘制,溢出的模型自然压在文本之下。
                // 渲染前刷新一次绘制批次,替代原先 enableScissor 顺带完成的批次刷新。
                guiGraphics.flush();
                com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
                EntityIconRenderer.render(pose, infoAvatarEntity, avatarDrawX, avatarY, avatarSize, fade, followRotation, rotationAngle);
            }

            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            // NOTE: do NOT re-enable depth test here - onHudRender forces a stable
            // 2D state (depth test off) for all modules and the rest of the frame.
            guiGraphics.pose().popPose();
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
        guiGraphics.drawString(font, new TextComponent(name), textX, textYName, textColor);

        int barBg = ((int)(128 * globalAlpha) << 24);
        boolean rounded = !DamageEngineConfig.getInstance().infoNoRoundedBorder;
        if (rounded) {
            drawRoundedRect(pose, barX, barY, barW, barH, barH / 2, barBg);
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
            drawBarRange(pose, barX, barY, barW, barH, barX, barX + fillW, barColorWithAlpha, rounded);

            if (heal > s) {
                int healStart = barX + (int)Math.floor(barW * s);
                int healEnd = barX + (int)Math.floor(barW * heal);
                int healAlpha = (int)(a * 0.45f);
                int healColor = (healColorBase & 0x00FFFFFF) | (healAlpha << 24);
                drawBarRange(pose, barX, barY, barW, barH, healStart, healEnd, healColor, rounded);
            }

            if (infoPrevDamageTailActive && lag > s) {
                int lagStart = barX + (int)Math.floor(barW * s);
                int lagEnd = barX + (int)Math.floor(barW * lag);
                int lagColor = (damageColorBase & 0x00FFFFFF) | (a << 24);
                drawBarRange(pose, barX, barY, barW, barH, lagStart, lagEnd, lagColor, rounded);
            }
        }

        if (newAlphaMul() > 0.01f) {
            float newMul = newAlphaMul();
            int a = (int)(baseAlpha * newMul);
            int fillW = (int)Math.floor(barW * infoSmoothRatio);
            int barColorWithAlpha = (barColor & 0x00FFFFFF) | (a << 24);
            drawBarRange(pose, barX, barY, barW, barH, barX, barX + fillW, barColorWithAlpha, rounded);

            if (infoHealRatio > infoSmoothRatio) {
                int healStart = barX + (int)Math.floor(barW * infoSmoothRatio);
                int healEnd = barX + (int)Math.floor(barW * infoHealRatio);
                int healAlpha = (int)(a * 0.45f);
                int healColor = (healColorBase & 0x00FFFFFF) | (healAlpha << 24);
                drawBarRange(pose, barX, barY, barW, barH, healStart, healEnd, healColor, rounded);
            }

            if (infoDamageTailActive && infoLagRatio > infoSmoothRatio) {
                int lagStart = barX + (int)Math.floor(barW * infoSmoothRatio);
                int lagEnd = barX + (int)Math.floor(barW * infoLagRatio);
                int lagColor = (damageColorBase & 0x00FFFFFF) | (a << 24);
                drawBarRange(pose, barX, barY, barW, barH, lagStart, lagEnd, lagColor, rounded);
                if (Math.abs(infoLagRatio - infoSmoothRatio) < 0.0025f) {
                    infoDamageTailActive = false;
                }
            }
        }

        boolean hasAbsorption = absorption > 0.01f;
        ResourceLocation GUI_ICONS = new ResourceLocation("textures/gui/icons.png");

        Component hpText = new TextComponent(hp + "/" + maxHp);
        guiGraphics.drawString(font, hpText, textX + 10, textYHp, textColor);
        // Container heart (u=16, v=0, 9x9)
        guiGraphics.blit(GUI_ICONS, textX, textYHp, 16, 0, 9, 9);
        // Full heart: normal (u=52, v=0) or absorbing (u=160, v=45)
        if (hasAbsorption) {
            guiGraphics.blit(GUI_ICONS, textX, textYHp, 160, 45, 9, 9);
        } else {
            guiGraphics.blit(GUI_ICONS, textX, textYHp, 52, 0, 9, 9);
        }
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

    private static void drawRoundedRect(PoseStack pose, int x, int y, int w, int h, int radius, int color) {
        GuiGraphics guiGraphics = GuiGraphics.of(pose);
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

    /**
     * 在圆角血条里填一段区间 [fromX, toX)。
     * <p>
     * 圆角外形属于<b>整条血条</b>,不是属于每一段:所以只有贴到血条最左/最右的段才吃到圆角,
     * 中间的分段(绿条右侧、红条的左侧与右侧)一律画成直角。逐段各自圆角会在接缝处留下缺口
     * (绿条右端被削掉的两个像素没人补),而给段单独画直角又会让血条最左端变成方的。
     */
    private static void drawBarRange(PoseStack pose, int barX, int barY, int barW, int barH,
                                     int fromX, int toX, int color, boolean rounded) {
        GuiGraphics guiGraphics = GuiGraphics.of(pose);
        int x0 = Math.max(fromX, barX);
        int x1 = Math.min(toX, barX + barW);
        if (x1 <= x0) return;
        if (!rounded || barH < 4) {
            guiGraphics.fill(x0, barY, x1, barY + barH, color);
            return;
        }
        int r = barH / 2;
        for (int i = 0; i < barH; i++) {
            // 与 drawRoundedRect 的圆角算法保持一致,这样各段拼出来的外形正好等于血条背景的圆角外形
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
                guiGraphics.fill(rowX0, barY + i, rowX1, barY + i + 1, color);
            }
        }
    }
}
