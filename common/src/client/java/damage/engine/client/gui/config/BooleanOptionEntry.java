package damage.engine.client.gui.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/** 开关行。 */
public class BooleanOptionEntry extends OptionEntry {
    private final StyledButton button;
    private final Component label;
    private boolean state;

    public BooleanOptionEntry(String key, boolean initial, Consumer<Boolean> onToggle) {
        this(key, initial, onToggle, null);
    }

    public BooleanOptionEntry(String key, boolean initial, Consumer<Boolean> onToggle, Component tooltip) {
        this.state = initial;
        this.label = Component.translatable(key);
        setSearchText(this.label);
        final StyledButton[] ref = new StyledButton[1];
        ref[0] = new StyledButton(0, 0, 100, 20,
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
    public void renderContent(GuiGraphicsExtractor g, int idx, int y, int x, int ew, int eh,
                              int mx, int my, boolean hovered, float dt) {
        g.text(Minecraft.getInstance().font, label, x, y + 8, 0xFFFFFFFF);
        button.setX(x + ew - 110);
        button.setY(y + 2);
        button.setFocused(false);
        button.extractRenderState(g, mx, my, dt);
    }

    @Override public List<? extends GuiEventListener> children() { return Collections.singletonList(button); }
    @Override public List<? extends NarratableEntry> narratables() { return Collections.singletonList(button); }
}
