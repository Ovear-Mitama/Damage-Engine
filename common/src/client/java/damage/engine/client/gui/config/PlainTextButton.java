package damage.engine.client.gui.config;

import damage.engine.client.gui.DamageConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** 纯文本按钮（无边框），用于折叠标题和删除操作。 */
public class PlainTextButton extends AbstractWidget {
    private final Runnable onPress;
    private final int hoverColor;
    private int defaultColor;
    private boolean forceHover;

    public PlainTextButton(int x, int y, int w, int h, Component msg, Runnable onPress,
                           int defaultColor, int hoverColor) {
        super(x, y, w, h, msg);
        this.onPress = onPress;
        this.defaultColor = defaultColor;
        this.hoverColor = hoverColor;
    }

    public void setForceHover(boolean v) { this.forceHover = v; }
    public void setDefaultColor(int c) { this.defaultColor = c; }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean bl) {
        if (this.active && this.visible && event.button() == 0 && this.isMouseOver(event.x(), event.y())) {
            this.playDownSound(Minecraft.getInstance().getSoundManager());
            this.onPress.run();
            return true;
        }
        return false;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float d) {
        if (isHovered() || forceHover) g.requestCursor(DamageConfigScreen.CURSOR_HAND);
        int c = (isHovered() || forceHover) ? hoverColor : defaultColor;
        g.centeredText(Minecraft.getInstance().font, getMessage(),
            getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, c);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput b) { this.defaultButtonNarrationText(b); }

    @Override
    public void playDownSound(net.minecraft.client.sounds.SoundManager sm) {
        if (Minecraft.getInstance().screen instanceof DamageConfigScreen s) s.playClickSound();
        else super.playDownSound(sm);
    }
}
