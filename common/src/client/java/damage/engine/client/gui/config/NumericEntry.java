package damage.engine.client.gui.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/** 浮点数输入行。 */
public class NumericEntry extends OptionEntry {
    private final EditBox field;
    private final Component label;

    public NumericEntry(String key, float initial, Consumer<Float> onChange) {
        this(key, initial, onChange, null);
    }

    public NumericEntry(String key, float initial, Consumer<Float> onChange, Component tooltip) {
        this.label = Component.translatable(key);
        setSearchText(this.label);
        this.field = new EditBox(Minecraft.getInstance().font, 0, 0, 100, 20, Component.empty());
        this.field.setBordered(false);
        this.field.setValue(formatFloat(initial));
        this.field.setResponder(s -> {
            try { onChange.accept(Float.parseFloat(s)); } catch (Exception ignored) {}
        });
        if (tooltip != null) {
            setHoverTooltip(tooltip);
        }
    }

    private static String formatFloat(float v) {
        if (v == (int) v) return String.valueOf((int) v);
        return String.format("%.1f", v);
    }

    @Override
    public void renderContent(GuiGraphics g, int idx, int y, int x, int ew, int eh,
                              int mx, int my, boolean hovered, float dt) {
        g.drawString(Minecraft.getInstance().font, label, x, y + 8, 0xFFFFFFFF);
        int bx = x + ew - 110, by = y + 2, bw = 100, bh = 20;
        field.setX(bx + 4);
        field.setY(by + 6);
        field.setWidth(bw - 8);
        g.fill(bx, by, bx + bw, by + bh, 0x20000000);
        int bc = (field.isFocused() || field.isMouseOver(mx, my)) ? 0xFFFFFFFF : 0xFFA0A0A0;
        g.fill(bx, by, bx + bw, by + 1, bc);
        g.fill(bx, by + bh - 1, bx + bw, by + bh, bc);
        g.fill(bx, by, bx + 1, by + bh, bc);
        g.fill(bx + bw - 1, by, bx + bw, by + bh, bc);
        field.render(g, mx, my, dt);
    }

    @Override public List<? extends GuiEventListener> children() { return Collections.singletonList(field); }
    @Override public List<? extends NarratableEntry> narratables() { return Collections.singletonList(field); }
}
