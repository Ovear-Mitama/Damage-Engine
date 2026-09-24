package damage.engine.compat;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * 1.19 兼容层:1.19 没有 {@code net.minecraft.client.gui.components.PlayerFaceRenderer}(1.19.4 才引入)。
 * <p>
 * 按与 1.20 相同的贴图区域把皮肤画成头像:脸部 8x8(u=8,v=8) + 帽子层 8x8(u=40,v=8),
 * 皮肤贴图尺寸 64x64。1.19 用 PoseStack 作为绘制载体。
 */
public final class PlayerFaceRenderer {
    private PlayerFaceRenderer() {
    }

    public static void draw(PoseStack pose, ResourceLocation skin, int x, int y, int size) {
        GuiGraphics guiGraphics = GuiGraphics.of(pose);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, skin);
        guiGraphics.blit(skin, x, y, size, size, 8.0f, 8.0f, 8, 8, 64, 64);
        guiGraphics.blit(skin, x, y, size, size, 40.0f, 8.0f, 8, 8, 64, 64);
    }
}
