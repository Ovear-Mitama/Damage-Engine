package damage.engine.hud.info;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.EntityLivingBase;

/**
 * 1.12.2 版实体头像渲染：把 3D 实体模型直接画到 HUD 的信息面板上。
 * <p>
 * 做法与原版 {@code GuiInventory.drawEntityOnScreen} 相同：复用 GUI 的投影矩阵，
 * 由 {@link RenderManager#renderEntity} 在 z=50 处绘制实体。
 */
public final class EntityIconRenderer {
    /** 换算基准：玩家身高对应的格数。1 格 = 头像槽像素 / 1.8，保证不同体型按真实比例显示。 */
    private static final float PLAYER_HEIGHT_BLOCKS = 1.8f;

    private EntityIconRenderer() {
    }

    /**
     * 将实体渲染到 HUD 的指定方形区域内。
     *
     * @param x              区域左上角 x
     * @param y              区域左上角 y
     * @param size           区域边长（像素）
     * @param alpha          整体透明度（0~1）
     * @param followRotation true = 跟随实际朝向（以玩家视角为基准），false = 按自定义角度
     * @param customAngle    自定义朝向（0 = 正面朝向观察者，角度增大为顺时针）
     */
    public static void render(EntityLivingBase entity, int x, int y, int size,
                              float alpha, boolean followRotation, int customAngle) {
        if (entity == null || alpha <= 0.01f) return;
        Minecraft mc = Minecraft.getMinecraft();
        RenderManager rm = mc.getRenderManager();
        if (rm == null) return;

        float scale = size / PLAYER_HEIGHT_BLOCKS;
        float height = Math.max(entity.height, 0.1f);

        // GUI 里 yaw = 0 时模型正面朝向观察者：
        // 跟随模式按"实体相对玩家的朝向"换算（正对玩家 = 正面，背对 = 背面），
        // 自定义模式取负值，让角度增大在屏幕上表现为顺时针。
        float renderedYaw = followRotation && mc.player != null
            ? 180.0f - (entity.rotationYaw - mc.player.rotationYaw)
            : -customAngle;

        float savedYaw = entity.rotationYaw;
        float savedPrevYaw = entity.prevRotationYaw;
        float savedYawOffset = entity.renderYawOffset;
        float savedPrevYawOffset = entity.prevRenderYawOffset;
        float savedPitch = entity.rotationPitch;
        float savedPrevPitch = entity.prevRotationPitch;
        float savedHeadYaw = entity.rotationYawHead;
        float savedPrevHeadYaw = entity.prevRotationYawHead;

        entity.rotationYaw = renderedYaw;
        entity.prevRotationYaw = renderedYaw;
        entity.renderYawOffset = renderedYaw;
        entity.prevRenderYawOffset = renderedYaw;
        entity.rotationYawHead = renderedYaw;
        entity.prevRotationYawHead = renderedYaw;
        entity.rotationPitch = 0.0f;
        entity.prevRotationPitch = 0.0f;

        GlStateManager.pushMatrix();
        try {
            GlStateManager.enableColorMaterial();
            GlStateManager.enableDepth();
            GlStateManager.enableRescaleNormal();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.color(1.0f, 1.0f, 1.0f, alpha);

            // 模型从脚底向上绘制（模型空间 y=0 是脚底），把模型垂直居中到头像槽里：
            // 脚底应落在槽中心往下半个模型高的位置，否则整只生物会浮在槽上方
            GlStateManager.translate(x + size / 2.0f, y + size / 2.0f + height * scale / 2.0f, 50.0f);
            GlStateManager.scale(-scale, scale, scale);
            GlStateManager.rotate(180.0f, 0.0f, 0.0f, 1.0f);

            // 与原版一致：先转到光照角度再开启标准物品光照，让模型正面受光
            GlStateManager.rotate(135.0f, 0.0f, 1.0f, 0.0f);
            RenderHelper.enableStandardItemLighting();
            GlStateManager.rotate(-135.0f, 0.0f, 1.0f, 0.0f);

            rm.setPlayerViewY(180.0f);
            rm.setRenderShadow(false);
            try {
                rm.renderEntity(entity, 0.0, 0.0, 0.0, renderedYaw, 1.0f, false);
            } finally {
                rm.setRenderShadow(true);
            }

            RenderHelper.disableStandardItemLighting();
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        } catch (Throwable ignored) {
            // 个别实体的渲染器出错时不拖垮整个 HUD
        } finally {
            GlStateManager.disableRescaleNormal();
            GlStateManager.disableDepth();
            GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
            GlStateManager.disableTexture2D();
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GlStateManager.popMatrix();

            entity.rotationYaw = savedYaw;
            entity.prevRotationYaw = savedPrevYaw;
            entity.renderYawOffset = savedYawOffset;
            entity.prevRenderYawOffset = savedPrevYawOffset;
            entity.rotationPitch = savedPitch;
            entity.prevRotationPitch = savedPrevPitch;
            entity.rotationYawHead = savedHeadYaw;
            entity.prevRotationYawHead = savedPrevHeadYaw;
        }
    }
}
