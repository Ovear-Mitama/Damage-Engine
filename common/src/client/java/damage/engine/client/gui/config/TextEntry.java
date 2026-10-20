package damage.engine.client.gui.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/** 文本输入行。 */
public class TextEntry extends OptionEntry {
    private final EditBox field;

    public TextEntry(String key, String initial, Consumer<String> onChange) {
        setLabel(key);
        this.field = new EditBox(Minecraft.getInstance().font, 0, 0, CONTROL_WIDTH, CONTROL_HEIGHT, Component.empty());
        this.field.setBordered(false);
        this.field.setMaxLength(1000);
        this.field.setValue(initial);
        this.field.setResponder(onChange);
    }

    @Override
    public void renderContent(GuiGraphicsExtractor g, int idx, int y, int x, int ew, int eh,
                              int mx, int my, boolean hovered, float dt) {
        renderLabel(g, x, y);
        layoutTextField(g, field, x, y, ew, mx, my, dt);
    }

    @Override public List<? extends GuiEventListener> children() { return Collections.singletonList(field); }
    @Override public List<? extends NarratableEntry> narratables() { return Collections.singletonList(field); }
}
