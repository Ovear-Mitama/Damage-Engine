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
    private final Component label;

    public TextEntry(String key, String initial, Consumer<String> onChange) {
        this.label = Component.translatable(key);
        setSearchText(this.label);
        this.field = new EditBox(Minecraft.getInstance().font, 0, 0, 100, 20, Component.empty());
        this.field.setBordered(false);
        this.field.setMaxLength(1000);
        this.field.setValue(initial);
        this.field.setResponder(onChange);
    }

    @Override
    public void renderContent(GuiGraphicsExtractor g, int idx, int y, int x, int ew, int eh,
                              int mx, int my, boolean hovered, float dt) {
        g.text(Minecraft.getInstance().font, label, x, y + 8, 0xFFFFFFFF);
        int bx = x + ew - 110, by = y + 2, bw = 100, bh = 20;
        field.setX(bx + 4);
        field.setY(by + 6);
        field.setWidth(bw - 8);
        field.setHeight(12);
        g.fill(bx, by, bx + bw, by + bh, 0x20000000);
        int bc = (field.isFocused() || field.isMouseOver(mx, my)) ? 0xFFFFFFFF : 0xFFA0A0A0;
        g.fill(bx, by, bx + bw, by + 1, bc);
        g.fill(bx, by + bh - 1, bx + bw, by + bh, bc);
        g.fill(bx, by, bx + 1, by + bh, bc);
        g.fill(bx + bw - 1, by, bx + bw, by + bh, bc);
        field.extractRenderState(g, mx, my, dt);
    }

    @Override public List<? extends GuiEventListener> children() { return Collections.singletonList(field); }
    @Override public List<? extends NarratableEntry> narratables() { return Collections.singletonList(field); }
}
