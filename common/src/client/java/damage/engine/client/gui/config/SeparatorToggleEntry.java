package damage.engine.client.gui.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/** 千位分隔符开关（1,000 / 1000）。 */
public class SeparatorToggleEntry extends OptionEntry {
    private final StyledButton button;
    private final Component label;
    private boolean state;

    public SeparatorToggleEntry(String key, boolean initial, Consumer<Boolean> onChange) {
        this.label = Component.translatable(key);
        setSearchText(this.label);
        this.state = initial;
        final StyledButton[] ref = new StyledButton[1];
        ref[0] = new StyledButton(0, 0, 100, 20,
            Component.literal(state ? "1,000" : "1000"), () -> {
            state = !state;
            ref[0].setMessage(Component.literal(state ? "1,000" : "1000"));
            onChange.accept(state);
        });
        this.button = ref[0];
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
