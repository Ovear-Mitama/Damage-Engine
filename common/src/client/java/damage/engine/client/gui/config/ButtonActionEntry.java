package damage.engine.client.gui.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;

import java.util.Collections;
import java.util.List;

/** 带按钮的操作行。 */
public class ButtonActionEntry extends OptionEntry {
    private final StyledButton button;
    private final Component label;

    public ButtonActionEntry(String labelKey, String buttonKey, Runnable action) {
        this(labelKey, Component.translatable(buttonKey), action);
    }

    public ButtonActionEntry(String labelKey, Component buttonText, Runnable action) {
        this.label = Component.translatable(labelKey);
        setSearchText(this.label);
        this.button = new StyledButton(0, 0, 100, 20, buttonText, action);
    }

    @Override
    public void renderContent(GuiGraphics g, int idx, int y, int x, int ew, int eh,
                              int mx, int my, boolean hovered, float dt) {
        g.drawString(Minecraft.getInstance().font, label, x, y + 8, 0xFFFFFFFF);
        button.setX(x + ew - 110);
        button.setY(y + 2);
        button.setFocused(false);
        button.render(g, mx, my, dt);
    }

    @Override public List<? extends GuiEventListener> children() { return Collections.singletonList(button); }
    @Override public List<? extends NarratableEntry> narratables() { return Collections.singletonList(button); }
}
