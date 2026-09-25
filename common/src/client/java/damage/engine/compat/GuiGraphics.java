package damage.engine.compat;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * 1.18 兼容层:1.18 没有 {@code net.minecraft.client.gui.GuiGraphics}(1.20 才引入)。
 * <p>
 * 内部持有 1.18 时代的 {@link PoseStack} 与 GUI 缓冲源,对外提供与 1.20 GuiGraphics
 * 同名同签名的方法,这样各界面 / HUD 的绘制方法体可以原样保留,只需把方法签名里的
 * {@code GuiGraphics} 参数换成 {@code PoseStack},并在方法体首行用
 * {@code GuiGraphics.of(pose)} 包装即可。
 */
public final class GuiGraphics {
    private final PoseStack pose;
    private final MultiBufferSource.BufferSource bufferSource;

    private GuiGraphics(PoseStack pose, MultiBufferSource.BufferSource bufferSource) {
        this.pose = pose;
        this.bufferSource = bufferSource;
    }

    /** 包装一个 PoseStack;缓冲源取当前 Minecraft 的 GUI 缓冲源(与 1.20 GuiGraphics 一致)。 */
    public static GuiGraphics of(PoseStack pose) {
        return new GuiGraphics(pose, Minecraft.getInstance().renderBuffers().bufferSource());
    }

    public PoseStack pose() {
        return this.pose;
    }

    public MultiBufferSource.BufferSource bufferSource() {
        return this.bufferSource;
    }

    /** 立即提交缓冲:1.20 的 flush 对应 1.18 的 endBatch。 */
    public void flush() {
        this.bufferSource.endBatch();
    }

    public void fill(int x1, int y1, int x2, int y2, int color) {
        GuiComponent.fill(this.pose, x1, y1, x2, y2, color);
    }

    /** 1.20 的带 z 版本填充;1.18 无等价物,用平移模拟深度偏移。 */
    public void fill(int x1, int y1, int x2, int y2, int z, int color) {
        this.pose.pushPose();
        this.pose.translate(0.0, 0.0, z);
        GuiComponent.fill(this.pose, x1, y1, x2, y2, color);
        this.pose.popPose();
    }

    public int drawString(Font font, String text, int x, int y, int color) {
        GuiComponent.drawString(this.pose, font, text, x, y, color);
        return x + font.width(text);
    }

    public int drawString(Font font, String text, int x, int y, int color, boolean dropShadow) {
        if (dropShadow) {
            return font.drawShadow(this.pose, text, x, y, color);
        }
        return this.drawString(font, text, x, y, color);
    }

    public int drawString(Font font, Component text, int x, int y, int color) {
        GuiComponent.drawString(this.pose, font, text, x, y, color);
        return x + font.width(text);
    }

    public int drawString(Font font, Component text, int x, int y, int color, boolean dropShadow) {
        if (dropShadow) {
            return font.drawShadow(this.pose, text, x, y, color);
        }
        return this.drawString(font, text, x, y, color);
    }

    public int drawString(Font font, FormattedCharSequence text, int x, int y, int color) {
        GuiComponent.drawString(this.pose, font, text, x, y, color);
        return x + font.width(text);
    }

    public int drawString(Font font, FormattedCharSequence text, int x, int y, int color, boolean dropShadow) {
        if (dropShadow) {
            return font.drawShadow(this.pose, text, x, y, color);
        }
        return this.drawString(font, text, x, y, color);
    }

    public int drawCenteredString(Font font, String text, int x, int y, int color) {
        GuiComponent.drawCenteredString(this.pose, font, text, x, y, color);
        return x + font.width(text) / 2;
    }

    public int drawCenteredString(Font font, Component text, int x, int y, int color) {
        GuiComponent.drawCenteredString(this.pose, font, text, x, y, color);
        return x + font.width(text) / 2;
    }

    public void blit(ResourceLocation texture, int x, int y, int uOffset, int vOffset, int width, int height) {
        this.prepareTexture(texture);
        GuiComponent.blit(this.pose, x, y, 0, (float) uOffset, (float) vOffset, width, height, 256, 256);
    }

    public void blit(ResourceLocation texture, int x, int y, int uOffset, int vOffset, int width, int height,
                     int textureWidth, int textureHeight) {
        this.prepareTexture(texture);
        GuiComponent.blit(this.pose, x, y, 0, (float) uOffset, (float) vOffset, width, height, textureWidth, textureHeight);
    }

    public void blit(ResourceLocation texture, int x, int y, int width, int height, float uOffset, float vOffset,
                     int uWidth, int vHeight, int textureWidth, int textureHeight) {
        this.prepareTexture(texture);
        GuiComponent.blit(this.pose, x, y, width, height, uOffset, vOffset, uWidth, vHeight, textureWidth, textureHeight);
    }

    /** 1.18 的 GuiComponent.blit 要求调用方自己设置着色器与贴图。 */
    private void prepareTexture(ResourceLocation texture) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, texture);
    }

    public void setColor(float red, float green, float blue, float alpha) {
        RenderSystem.setShaderColor(red, green, blue, alpha);
    }

    /** 1.18 的裁剪要按 GUI 缩放换算成屏幕像素,并把 Y 轴翻转到 GL 的左下原点。 */
    public void enableScissor(int x1, int y1, int x2, int y2) {
        Minecraft minecraft = Minecraft.getInstance();
        int scale = (int) minecraft.getWindow().getGuiScale();
        int screenHeight = minecraft.getWindow().getHeight();
        RenderSystem.enableScissor(x1 * scale, screenHeight - y2 * scale, (x2 - x1) * scale, (y2 - y1) * scale);
    }

    public void disableScissor() {
        RenderSystem.disableScissor();
    }

    public void renderTooltip(Font font, List<? extends FormattedCharSequence> lines, int x, int y) {
        Screen screen = Minecraft.getInstance().screen;
        if (screen != null) {
            screen.renderTooltip(this.pose, lines, x, y);
        }
    }
}
