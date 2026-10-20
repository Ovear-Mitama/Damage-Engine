package damage.engine.client.gui.config;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;

import java.util.Collections;
import java.util.List;

/** 带按钮的操作行。 */
public class ButtonActionEntry extends OptionEntry {
    private final StyledButton button;

    public ButtonActionEntry(String labelKey, String buttonKey, Runnable action) {
        this(labelKey, Component.translatable(buttonKey), action);
    }

    public ButtonActionEntry(String labelKey, Component buttonText, Runnable action) {
        setLabel(labelKey);
        this.button = new StyledButton(0, 0, CONTROL_WIDTH, CONTROL_HEIGHT, buttonText, action);
    }

    @Override
    public void renderContent(GuiGraphicsExtractor g, int idx, int y, int x, int ew, int eh,
                              int mx, int my, boolean hovered, float dt) {
        renderLabel(g, x, y);
        renderButton(g, button, x, y, ew, mx, my, dt);
    }

    @Override public List<? extends GuiEventListener> children() { return Collections.singletonList(button); }
    @Override public List<? extends NarratableEntry> narratables() { return Collections.singletonList(button); }
}
