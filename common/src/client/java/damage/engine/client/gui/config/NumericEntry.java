package damage.engine.client.gui.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/** 浮点数输入行。 */
public class NumericEntry extends OptionEntry {
    private final EditBox field;

    public NumericEntry(String key, float initial, Consumer<Float> onChange) {
        this(key, initial, onChange, null);
    }

    public NumericEntry(String key, float initial, Consumer<Float> onChange, Component tooltip) {
        setLabel(key);
        this.field = new EditBox(Minecraft.getInstance().font, 0, 0, CONTROL_WIDTH, CONTROL_HEIGHT, Component.empty());
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
        renderLabel(g, x, y);
        layoutTextField(g, field, x, y, ew, mx, my, dt);
    }

    @Override public List<? extends GuiEventListener> children() { return Collections.singletonList(field); }
    @Override public List<? extends NarratableEntry> narratables() { return Collections.singletonList(field); }
}
