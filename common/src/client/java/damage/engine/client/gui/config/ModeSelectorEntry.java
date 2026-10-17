package damage.engine.client.gui.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/** 多模式循环切换行。 */
public class ModeSelectorEntry extends OptionEntry {
    private final StyledButton button;
    private final Component label;
    private String mode;

    public ModeSelectorEntry(String key, String initial, Consumer<String> onChange) {
        this(key, initial, new String[]{"enhanced", "cloud"},
            Component.translatable("hint.damage-engine.indicatorMode"), onChange);
    }

    public ModeSelectorEntry(String key, String initial, String[] modes, Component tooltip,
                             Consumer<String> onChange) {
        this.label = Component.translatable(key);
        setSearchText(this.label);
        this.mode = initial;
        final StyledButton[] ref = new StyledButton[1];
        ref[0] = new StyledButton(0, 0, 100, 20,
            Component.translatable("option.damage-engine.mode." + mode), () -> {
            int idx = 0;
            for (int i = 0; i < modes.length; i++) {
                if (modes[i].equals(mode)) { idx = i; break; }
            }
            mode = modes[(idx + 1) % modes.length];
            ref[0].setMessage(Component.translatable("option.damage-engine.mode." + mode));
            onChange.accept(mode);
        });
        this.button = ref[0];
        if (tooltip != null) {
            setHoverTooltip(tooltip);
        }
    }

    /** 布尔模式选择（屏蔽/显示）。 */
    public ModeSelectorEntry(String key, boolean initial, Consumer<Boolean> onChange) {
        this.label = Component.translatable(key);
        setSearchText(this.label);
        this.mode = initial ? "block" : "show";
        final StyledButton[] ref = new StyledButton[1];
        ref[0] = new StyledButton(0, 0, 100, 20,
            Component.translatable("option.damage-engine.mode." + mode)
                .withStyle(s -> s.withColor(net.minecraft.network.chat.TextColor.fromRgb(initial ? 0xFFFC887E : 0xFFB5F0C6 & 0xFFFFFF))), () -> {
            boolean v = "show".equals(mode);
            mode = v ? "block" : "show";
            ref[0].setMessage(Component.translatable("option.damage-engine.mode." + mode)
                .withStyle(s -> s.withColor(net.minecraft.network.chat.TextColor.fromRgb(v ? 0xFFFC887E : 0xFFB5F0C6 & 0xFFFFFF))));
            onChange.accept(v);
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
