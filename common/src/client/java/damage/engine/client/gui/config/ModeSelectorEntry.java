package damage.engine.client.gui.config;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/** 多模式循环切换行。 */
public class ModeSelectorEntry extends OptionEntry {
    /** 关闭态的配色与开关按钮一致；其它模式统一用绿色。 */
    private static final int COLOR_ON = 0xFFB5F0C6;
    private static final int COLOR_OFF = 0xFFFC887E;

    private final StyledButton button;
    private String mode;

    private static Component modeText(String mode) {
        return Component.translatable("option.damage-engine.mode." + mode)
            .withColor("off".equals(mode) ? COLOR_OFF : COLOR_ON);
    }

    public ModeSelectorEntry(String key, String initial, Consumer<String> onChange) {
        this(key, initial, new String[]{"enhanced", "cloud"},
            Component.translatable("hint.damage-engine.indicatorMode"), onChange);
    }

    public ModeSelectorEntry(String key, String initial, String[] modes, Component tooltip,
                             Consumer<String> onChange) {
        setLabel(key);
        this.mode = initial;
        final StyledButton[] ref = new StyledButton[1];
        ref[0] = new StyledButton(0, 0, CONTROL_WIDTH, CONTROL_HEIGHT, modeText(mode), () -> {
            int idx = 0;
            for (int i = 0; i < modes.length; i++) {
                if (modes[i].equals(mode)) { idx = i; break; }
            }
            mode = modes[(idx + 1) % modes.length];
            ref[0].setMessage(modeText(mode));
            onChange.accept(mode);
        });
        this.button = ref[0];
        if (tooltip != null) {
            setHoverTooltip(tooltip);
        }
    }

    /** 布尔模式选择（屏蔽/显示）。 */
    public ModeSelectorEntry(String key, boolean initial, Consumer<Boolean> onChange) {
        setLabel(key);
        this.mode = initial ? "block" : "show";
        final StyledButton[] ref = new StyledButton[1];
        ref[0] = new StyledButton(0, 0, CONTROL_WIDTH, CONTROL_HEIGHT,
            Component.translatable("option.damage-engine.mode." + mode)
                .withColor(initial ? 0xFFFC887E : 0xFFB5F0C6), () -> {
            boolean v = "show".equals(mode);
            mode = v ? "block" : "show";
            ref[0].setMessage(Component.translatable("option.damage-engine.mode." + mode)
                .withColor(v ? 0xFFFC887E : 0xFFB5F0C6));
            onChange.accept(v);
        });
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
