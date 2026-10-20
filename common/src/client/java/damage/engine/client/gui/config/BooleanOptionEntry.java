package damage.engine.client.gui.config;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/** 开关行。 */
public class BooleanOptionEntry extends OptionEntry {
    private final StyledButton button;
    private boolean state;

    public BooleanOptionEntry(String key, boolean initial, Consumer<Boolean> onToggle) {
        this(key, initial, onToggle, null);
    }

    public BooleanOptionEntry(String key, boolean initial, Consumer<Boolean> onToggle, Component tooltip) {
        setLabel(key);
        this.state = initial;
        final StyledButton[] ref = new StyledButton[1];
        ref[0] = new StyledButton(0, 0, CONTROL_WIDTH, CONTROL_HEIGHT,
            Component.translatable(state ? "options.on" : "options.off")
                .withColor(state ? 0xFFB5F0C6 : 0xFFFC887E), () -> {
            state = !state;
            ref[0].setMessage(Component.translatable(state ? "options.on" : "options.off")
                .withColor(state ? 0xFFB5F0C6 : 0xFFFC887E));
            onToggle.accept(state);
        });
        if (tooltip != null) {
            setHoverTooltip(tooltip);
        }
        this.button = ref[0];
    }

    @Override
    public void renderContent(GuiGraphics g, int idx, int y, int x, int ew, int eh,
                              int mx, int my, boolean hovered, float dt) {
        renderLabel(g, x, y);
        renderButton(g, button, x, y, ew, mx, my, dt);
    }

    @Override public List<? extends GuiEventListener> children() { return Collections.singletonList(button); }
    @Override public List<? extends NarratableEntry> narratables() { return Collections.singletonList(button); }
}
