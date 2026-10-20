package damage.engine.client.gui.config;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/** 整数滑条行。 */
public class IntegerSliderEntry extends OptionEntry {
    private final StyledSliderWidget slider;

    public IntegerSliderEntry(String key, int cur, int min, int max, Consumer<Integer> onChange,
                              boolean soundOnRelease) {
        this(key, cur, min, max, onChange, soundOnRelease, "");
    }

    public IntegerSliderEntry(String key, int cur, int min, int max, Consumer<Integer> onChange,
                              boolean soundOnRelease, String suffix) {
        setLabel(key);
        float minF = min, maxF = max;
        this.slider = new StyledSliderWidget(0, 0, CONTROL_WIDTH, CONTROL_HEIGHT, Component.literal(cur + suffix),
            (cur - minF) / (maxF - minF), true, !soundOnRelease, soundOnRelease) {
            @Override
            protected void updateMessage() {
                this.setMessage(Component.literal(Math.round(minF + this.value * (maxF - minF)) + suffix));
            }

            @Override
            protected void applyValue() {
                onChange.accept((int) Math.round(minF + this.value * (maxF - minF)));
            }
        };
    }

    @Override
    public void renderContent(GuiGraphics g, int idx, int y, int x, int ew, int eh,
                              int mx, int my, boolean hovered, float dt) {
        renderLabel(g, x, y);
        renderControl(g, slider, x, y, ew, mx, my, dt);
    }

    @Override public List<? extends GuiEventListener> children() { return Collections.singletonList(slider); }
    @Override public List<? extends NarratableEntry> narratables() { return Collections.singletonList(slider); }
}
