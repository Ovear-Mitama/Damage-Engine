package damage.engine.client.gui.config;

import damage.engine.client.gui.DamageConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

/** 配置界面通用滑条。 */
public class StyledSliderWidget extends AbstractSliderButton {
    private final boolean showValue, soundOnPress, soundOnRelease;
    private boolean pendingReleaseSound;
    private double valueBeforeInteraction;

    public StyledSliderWidget(int x, int y, int w, int h, Component text, double value,
                              boolean showValue, boolean soundOnPress, boolean soundOnRelease) {
        super(x, y, w, h, text, value);
        this.showValue = showValue;
        this.soundOnPress = soundOnPress;
        this.soundOnRelease = soundOnRelease;
    }

    @Override protected void updateMessage() {}
    @Override protected void applyValue() {}

    private void playClick() {
        if (Minecraft.getInstance().screen instanceof DamageConfigScreen s) s.playClickSound();
        else super.playDownSound(Minecraft.getInstance().getSoundManager());
    }

    @Override
    public void playDownSound(net.minecraft.client.sounds.SoundManager sm) {
        if (!soundOnPress) return;
        playClick();
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean bl) {
        boolean h = super.mouseClicked(event, bl);
        if (h && soundOnRelease) {
            pendingReleaseSound = true;
            valueBeforeInteraction = this.value;
        }
        return h;
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        boolean h = super.mouseReleased(event);
        if (soundOnRelease && pendingReleaseSound) {
            pendingReleaseSound = false;
            if (Math.abs(this.value - valueBeforeInteraction) > 1.0E-9) playClick();
        }
        return h;
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float d) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        g.fill(x, y, x + w, y + h, 0x20000000);
        int bc = isHovered() ? 0xFFFFFFFF : 0xFFA0A0A0;
        g.fill(x, y, x + w, y + 1, bc);
        g.fill(x, y + h - 1, x + w, y + h, bc);
        g.fill(x, y, x + 1, y + h, bc);
        g.fill(x + w - 1, y, x + w, y + h, bc);
        int hw = 8, hx = x + (int) (this.value * (double) (w - hw));
        g.fill(hx, y, hx + hw, y + h, 0xFF000000);
        int hbc = (isHovered() || isFocused()) ? 0xFFFFFFFF : 0xFFCCCCCC;
        g.fill(hx, y, hx + hw, y + 1, hbc);
        g.fill(hx, y + h - 1, hx + hw, y + h, hbc);
        g.fill(hx, y, hx + 1, y + h, hbc);
        g.fill(hx + hw - 1, y, hx + hw, y + h, hbc);
        if (showValue) {
            g.centeredText(Minecraft.getInstance().font, getMessage(),
                x + w / 2, y + (h - 8) / 2, 0xFFFFFFFF);
        }
    }
}
