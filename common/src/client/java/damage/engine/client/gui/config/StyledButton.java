package damage.engine.client.gui.config;

import damage.engine.client.gui.DamageConfigScreen;
import damage.engine.client.gui.HudEditorScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** 配置界面通用描边按钮。 */
public class StyledButton extends AbstractWidget {
    private final Runnable onPress;

    public StyledButton(int x, int y, int width, int height, Component message, Runnable onPress) {
        super(x, y, width, height, message);
        this.onPress = onPress;
    }

    @Override
    public void playDownSound(net.minecraft.client.sounds.SoundManager sm) {
        if (Minecraft.getInstance().screen instanceof DamageConfigScreen s) s.playClickSound();
        else if (Minecraft.getInstance().screen instanceof HudEditorScreen s) s.playClickSound();
        else super.playDownSound(sm);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (this.active && this.visible && btn == 0 && this.isMouseOver(mx, my)) {
            this.playDownSound(Minecraft.getInstance().getSoundManager());
            this.onPress.run();
            return true;
        }
        return false;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mx, int my, float d) {
        g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0x20000000);
        int bc = isHovered() ? 0xFFFFFFFF : 0xFFA0A0A0;
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        g.fill(x, y, x + w, y + 1, bc);
        g.fill(x, y + h - 1, x + w, y + h, bc);
        g.fill(x, y, x + 1, y + h, bc);
        g.fill(x + w - 1, y, x + w, y + h, bc);
        g.drawCenteredString(Minecraft.getInstance().font, getMessage(), x + w / 2, y + (h - 8) / 2, 0xFFFFFFFF);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput b) { this.defaultButtonNarrationText(b); }
}
