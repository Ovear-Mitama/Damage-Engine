package damage.engine;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * 1.12.2 配置界面，布局对齐 1.21.1 版：
 * 顶部搜索框 + 可横向滚动的标签栏 + 全宽选项列表 + 底部操作栏，全部自绘。
 */
public class GuiDamageConfig extends GuiScreen {
    private static final String[] TAB_KEYS = {
        "tab.damage-engine.general",
        "tab.damage-engine.damage_interface",
        "tab.damage-engine.damage_numbers",
        "tab.damage-engine.entity_info",
        "tab.damage-engine.rating",
        "tab.damage-engine.keybinds",
        "tab.damage-engine.other"
    };
    private static final int TOP_BAR_H = 22;
    private static final int ROW_H = 28;
    private static final int HEADER_H = 26;
    private static final int BTN_H = 20;
    private static final int CONTROL_H = 20;
    private static final int CONTROL_W = 80;

    private static final int COLOR_TEXT = 0xFFE8E8EC;
    private static final int COLOR_MUTED = 0xFFA0A0A0;
    private static final int COLOR_ON = 0xFFB5F0C6;
    private static final int COLOR_OFF = 0xFFFC887E;
    private static final int COLOR_BORDER = 0xFF555555;
    private static final int[] COLOR_PALETTE = {
        0xFFFFFFFF, 0xFFFFD700, 0xFFB5F0C6, 0xFFFC887E,
        0xFF3FA9F0, 0xFFFC54FC, 0xFFFFA500
    };

    private final DamageEngineConfig config = DamageEngineConfig.getInstance();
    /** 关闭后返回的界面。 */
    private final GuiScreen parent;
    private final List<Row> rows = new ArrayList<>();
    private final List<Btn> buttons = new ArrayList<>();
    private final int[] tabWidths = new int[TAB_KEYS.length];
    private int selectedTab = 0;
    private float tabScroll;
    private float tabScrollTarget;
    private long lastFrameMs;
    private int tabsLeft;
    private int tabsRight;
    private GuiTextField searchBox;
    private String searchQuery = "";
    private int listTop;
    private int listBottom;
    private float listScroll;
    private float listScrollTarget;
    private boolean draggingScrollbar;
    private OptionRow draggingSlider;
    private int sliderDragX;
    private int sliderDragW;
    /** 正在等待按键的绑定行。 */
    private KeybindRow listeningKeybind;
    /** 本次点击的鼠标 Y（组内控件需要行内纵向命中）。 */
    private int clickMouseY;
    /** 顶部选中标签的绿色下划线位置（带滑动补间）。 */
    private float indicatorX = -1f;
    private float indicatorW;
    /** 展开/收起分组的状态（1.21.1 同款折叠项）。 */
    private final Map<String, Boolean> expandStates = new HashMap<>();
    /** 折叠组内的文本输入框（阈值 / 等级 / 实体规则等）。 */
    private final List<NestedField> nestedFields = new ArrayList<>();
    /** 重置按钮的两段式确认状态：0 正常、1 待确认、2 已重置。 */
    private int resetState;
    private long resetActionTime;

    private static final int SLIDER_W = CONTROL_W;
    private static final int RIGHT_EDGE = 8;

    public GuiDamageConfig() {
        this(null);
    }

    public GuiDamageConfig(GuiScreen parent) {
        this.parent = parent;
    }

    // ================= 行 =================

    private abstract class Row {
        abstract int height();

        abstract void draw(int x, int y, int w, boolean hovered, int mouseX, int mouseY);

        abstract void click(int mouseX, int rowY, int button);

        boolean matches(String query) {
            return false;
        }
    }

    /** 分组标题。 */
    private final class HeaderRow extends Row {
        private final String key;

        HeaderRow(String key) {
            this.key = key;
        }

        @Override
        int height() {
            return HEADER_H;
        }

        @Override
        void draw(int x, int y, int w, boolean hovered, int mouseX, int mouseY) {
            fontRenderer.drawStringWithShadow(I18n.format(key), x, y + 10, COLOR_ON);
            Gui.drawRect(x, y + 23, x + w, y + 24, 0x50B5F0C6);
        }

        @Override
        void click(int mouseX, int rowY, int button) {
        }
    }

    private abstract class OptionRow extends Row {
        final String labelKey;

        OptionRow(String labelKey) {
            this.labelKey = labelKey;
        }

        abstract String valueText();

        abstract void click(int button);

        int valueColor() {
            return COLOR_ON;
        }

        /** 数值行右侧是否显示 - / + 按钮。 */
        boolean hasStepper() {
            return false;
        }

        /** 值是否画成小按钮（模式选择 / 动作行 / 开关）。 */
        boolean boxedValue() {
            return false;
        }

        /** 颜色行返回颜色值用于画预览色块；-1 = 不画。 */
        int swatchColor() {
            return -1;
        }

        void step(int direction) {
            click(direction < 0 ? 1 : 0);
        }

        /** 数值行在 0~1 之间的位置（用于拖动条）。 */
        float fraction() {
            return 0f;
        }

        /** 拖动条位置 → 数值。 */
        void setFromFraction(float fraction) {
        }

        @Override
        int height() {
            return ROW_H;
        }

        @Override
        boolean matches(String query) {
            String q = query.toLowerCase(Locale.ROOT);
            return I18n.format(labelKey).toLowerCase(Locale.ROOT).contains(q)
                || valueText().toLowerCase(Locale.ROOT).contains(q);
        }

        /** 选项的自定义提示（与 1.21.1 一致：hint.* 键，缺省则不显示）。 */
        String hintText() {
            if (labelKey == null || !labelKey.startsWith("option.")) return null;
            // option.damage-engine.foo → hint.damage-engine.foo
            String key = "hint." + labelKey.substring("option.".length());
            return I18n.hasKey(key) ? I18n.format(key) : null;
        }

        @Override
        void draw(int x, int y, int w, boolean hovered, int mouseX, int mouseY) {
            if (hovered) {
                Gui.drawRect(0, y, width, y + ROW_H - 2, 0x14FFFFFF);
            }
            int textY = y + (ROW_H - 8) / 2;
            fontRenderer.drawStringWithShadow(I18n.format(labelKey), x, textY, COLOR_TEXT);
            String value = valueText();
            int valueRight = x + w;

            if (hasStepper()) {
                int sy = y + (ROW_H - CONTROL_H) / 2;
                int sx = valueRight - RIGHT_EDGE - SLIDER_W;
                drawSlider(sx, sy, SLIDER_W, CONTROL_H, value, mouseX, mouseY);
                return;
            }

            int valueWidth = fontRenderer.getStringWidth(value);
            if (boxedValue()) {
                int swatch = swatchColor();
                int swatchW = swatch != -1 ? CONTROL_H - 2 : 0;
                // 统一尺寸：所有按钮/输入框等宽等高，并共用同一条右边缘
                int boxW = CONTROL_W;
                int bh = CONTROL_H;
                int bx = valueRight - RIGHT_EDGE - boxW;
                int by = y + (ROW_H - bh) / 2;
                boolean overBox = mouseX >= bx && mouseX <= bx + boxW
                    && mouseY >= by && mouseY <= by + bh;
                Gui.drawRect(bx, by, bx + boxW, by + bh,
                    overBox ? 0x50000000 : 0x30000000);
                int border = overBox ? 0xFFFFFFFF : 0xFFA0A0A0;
                Gui.drawRect(bx, by, bx + boxW, by + 1, border);
                Gui.drawRect(bx, by + bh - 1, bx + boxW, by + bh, border);
                Gui.drawRect(bx, by, bx + 1, by + bh, border);
                Gui.drawRect(bx + boxW - 1, by, bx + boxW, by + bh, border);

                float textX;
                if (swatchW > 0) {
                    Gui.drawRect(bx + 3, by + 3, bx + 15, by + 15,
                        0xFF000000 | (swatch & 0x00FFFFFF));
                    int swatchBorder = 0xFF808080;
                    Gui.drawRect(bx + 3, by + 3, bx + 15, by + 4, swatchBorder);
                    Gui.drawRect(bx + 3, by + 14, bx + 15, by + 15, swatchBorder);
                    Gui.drawRect(bx + 3, by + 3, bx + 4, by + 15, swatchBorder);
                    Gui.drawRect(bx + 14, by + 3, bx + 15, by + 15, swatchBorder);
                    textX = bx + 17 + (boxW - 17 - valueWidth) / 2.0f;
                } else {
                    textX = bx + (boxW - valueWidth) / 2.0f;
                }
                fontRenderer.drawStringWithShadow(value, textX,
                    by + (bh - 8) / 2.0f, valueColor());
            } else {
                fontRenderer.drawStringWithShadow(value,
                    valueRight - valueWidth, textY, valueColor());
            }
        }

        private float clamp01(float value) {
            return value < 0f ? 0f : (value > 1f ? 1f : value);
        }

        private void drawSlider(int x, int y, int w, int h, String value, int mouseX, int mouseY) {
            boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
            Gui.drawRect(x, y, x + w, y + h, 0x60000000);
            int border = hovered ? 0xFFFFFFFF : 0xFFA0A0A0;
            Gui.drawRect(x, y, x + w, y + 1, border);
            Gui.drawRect(x, y + h - 1, x + w, y + h, border);
            Gui.drawRect(x, y, x + 1, y + h, border);
            Gui.drawRect(x + w - 1, y, x + w, y + h, border);
            // 与 1.21.1 一致：8px 宽、满高的拖动块，不再画细线
            int hw = 8;
            int hx = x + Math.round(clamp01(fraction()) * (w - hw));
            Gui.drawRect(hx, y, hx + hw, y + h, 0xFF000000);
            int hbc = hovered ? 0xFFFFFFFF : 0xFFCCCCCC;
            Gui.drawRect(hx, y, hx + hw, y + 1, hbc);
            Gui.drawRect(hx, y + h - 1, hx + hw, y + h, hbc);
            Gui.drawRect(hx, y, hx + 1, y + h, hbc);
            Gui.drawRect(hx + hw - 1, y, hx + hw, y + h, hbc);
            fontRenderer.drawStringWithShadow(value,
                x + (w - fontRenderer.getStringWidth(value)) / 2.0f,
                y + (h - 8) / 2.0f, 0xFFFFFFFF);
        }

        /** 点击是否落在右侧控件区域内（避免点标签/看提示时误触开关）。 */
        boolean inControlArea(int mouseX) {
            int w = width - 20;
            int right = 10 + w - RIGHT_EDGE;
            return mouseX >= right - CONTROL_W && mouseX <= right;
        }

        @Override
        void click(int mouseX, int rowY, int button) {
            if (hasStepper()) {
                if (button != 0) return;
                int w = width - 20;
                int sx = 10 + w - RIGHT_EDGE - SLIDER_W;
                if (mouseX >= sx && mouseX <= sx + SLIDER_W) {
                    // 开始拖动数值条（按 8px 拖动块的行程换算）
                    draggingSlider = this;
                    sliderDragX = sx;
                    sliderDragW = SLIDER_W;
                    setFromFraction(clamp01((mouseX - sx - 4.0f) / (SLIDER_W - 8.0f)));
                }
                return;
            }
            // 只有右侧的值/按钮区域响应点击，与 1.21.1 一致
            if (!inControlArea(mouseX)) return;
            click(button);
        }
    }

    private final class BoolRow extends OptionRow {
        private final BooleanSupplier getter;
        private final Consumer<Boolean> setter;

        BoolRow(String key, BooleanSupplier getter, Consumer<Boolean> setter) {
            super(key);
            this.getter = getter;
            this.setter = setter;
        }

        @Override
        String valueText() {
            return I18n.format(getter.getAsBoolean() ? "options.on" : "options.off");
        }

        @Override
        boolean boxedValue() {
            return true;
        }

        @Override
        int valueColor() {
            return getter.getAsBoolean() ? COLOR_ON : COLOR_OFF;
        }

        @Override
        void click(int button) {
            setter.accept(!getter.getAsBoolean());
        }
    }

    private final class IntRow extends OptionRow {
        private final IntSupplier getter;
        private final IntConsumer setter;
        private final int min;
        private final int max;
        private final int step;

        IntRow(String key, IntSupplier getter, IntConsumer setter, int min, int max, int step) {
            super(key);
            this.getter = getter;
            this.setter = setter;
            this.min = min;
            this.max = max;
            this.step = step;
        }

        @Override
        String valueText() {
            return String.valueOf(getter.getAsInt());
        }

        @Override
        boolean hasStepper() {
            return true;
        }

        @Override
        float fraction() {
            if (max <= min) return 0f;
            return (getter.getAsInt() - min) / (float) (max - min);
        }

        @Override
        void setFromFraction(float fraction) {
            // 拖动时按 1 递增，不再吸附到步进（避免只能一挡一挡地跳）
            int value = min + Math.round(fraction * (max - min));
            setter.accept(Math.max(min, Math.min(max, value)));
        }

        @Override
        int valueColor() {
            return COLOR_TEXT;
        }

        @Override
        void click(int button) {
            int value = getter.getAsInt() + (button == 1 ? -step : step);
            setter.accept(Math.max(min, Math.min(max, value)));
        }
    }

    private final class FloatRow extends OptionRow {
        private final DoubleSupplier getter;
        private final DoubleConsumer setter;
        private final double min;
        private final double max;
        private final double step;

        FloatRow(String key, DoubleSupplier getter, DoubleConsumer setter,
                 double min, double max, double step) {
            super(key);
            this.getter = getter;
            this.setter = setter;
            this.min = min;
            this.max = max;
            this.step = step;
        }

        @Override
        String valueText() {
            return String.format(Locale.ROOT, "%.1f", getter.getAsDouble());
        }

        @Override
        boolean hasStepper() {
            return true;
        }

        @Override
        float fraction() {
            if (max <= min) return 0f;
            return (float) ((getter.getAsDouble() - min) / (max - min));
        }

        @Override
        void setFromFraction(float fraction) {
            // 拖动时保留一位小数，不做步进吸附
            double value = min + fraction * (max - min);
            value = Math.round(value * 10.0) / 10.0;
            setter.accept(Math.max(min, Math.min(max, value)));
        }

        @Override
        int valueColor() {
            return COLOR_TEXT;
        }

        @Override
        void click(int button) {
            double value = getter.getAsDouble() + (button == 1 ? -step : step);
            setter.accept(Math.max(min, Math.min(max, value)));
        }
    }

    private final class ChoiceRow extends OptionRow {
        private final Supplier<String> getter;
        private final Consumer<String> setter;
        private final String[] values;

        ChoiceRow(String key, Supplier<String> getter, Consumer<String> setter, String[] values) {
            super(key);
            this.getter = getter;
            this.setter = setter;
            this.values = values;
        }

        @Override
        String valueText() {
            String current = getter.get();
            for (String value : values) {
                if (value.equals(current)) {
                    return I18n.format("option.damage-engine.mode." + value);
                }
            }
            return String.valueOf(current);
        }

        @Override
        boolean boxedValue() {
            return true;
        }

        @Override
        void click(int button) {
            String current = getter.get();
            int index = 0;
            for (int i = 0; i < values.length; i++) {
                if (values[i].equals(current)) {
                    index = i;
                    break;
                }
            }
            index = (index + (button == 1 ? -1 : 1) + values.length) % values.length;
            setter.accept(values[index]);
        }
    }

    /** 颜色行：在常用颜色预设之间循环，值用该颜色显示。 */
    private final class ColorRow extends OptionRow {
        private final IntSupplier getter;
        private final IntConsumer setter;

        ColorRow(String key, IntSupplier getter, IntConsumer setter) {
            super(key);
            this.getter = getter;
            this.setter = setter;
        }

        @Override
        String valueText() {
            return String.format(Locale.ROOT, "#%06X", getter.getAsInt() & 0xFFFFFF);
        }

        @Override
        int valueColor() {
            return 0xFF000000 | (getter.getAsInt() & 0xFFFFFF);
        }

        @Override
        void draw(int x, int y, int w, boolean hovered, int mouseX, int mouseY) {
            if (hovered) {
                Gui.drawRect(0, y, width, y + ROW_H - 2, 0x14FFFFFF);
            }
            int textY = y + (ROW_H - 8) / 2;
            fontRenderer.drawStringWithShadow(I18n.format(labelKey), x, textY, COLOR_TEXT);

            int right = x + w - RIGHT_EDGE;
            int boxH = CONTROL_H;
            int swatchSize = CONTROL_H;
            // 输入框 + 间隔 + 色块的总宽与其他控件的 CONTROL_W 对齐
            int boxW = CONTROL_W - swatchSize - 4;
            int swatchX = right - swatchSize;
            int boxX = swatchX - 4 - boxW;
            int boxY = y + (ROW_H - boxH) / 2;

            // 左侧 Hex 输入框（点击打开颜色选择器）
            boolean overBox = mouseX >= boxX && mouseX <= boxX + boxW
                && mouseY >= boxY && mouseY <= boxY + boxH;
            Gui.drawRect(boxX, boxY, boxX + boxW, boxY + boxH,
                overBox ? 0x50000000 : 0x30000000);
            int boxBorder = overBox ? 0xFFFFFFFF : 0xFFA0A0A0;
            Gui.drawRect(boxX, boxY, boxX + boxW, boxY + 1, boxBorder);
            Gui.drawRect(boxX, boxY + boxH - 1, boxX + boxW, boxY + boxH, boxBorder);
            Gui.drawRect(boxX, boxY, boxX + 1, boxY + boxH, boxBorder);
            Gui.drawRect(boxX + boxW - 1, boxY, boxX + boxW, boxY + boxH, boxBorder);
            String value = valueText();
            fontRenderer.drawStringWithShadow(value,
                boxX + (boxW - fontRenderer.getStringWidth(value)) / 2.0f,
                boxY + (boxH - 8) / 2.0f, COLOR_TEXT);

            // 右侧独立的颜色预览块
            Gui.drawRect(swatchX, boxY, swatchX + swatchSize, boxY + swatchSize,
                0xFF000000 | (getter.getAsInt() & 0x00FFFFFF));
            int swatchBorder = mouseX >= swatchX && mouseX <= swatchX + swatchSize
                && mouseY >= boxY && mouseY <= boxY + swatchSize ? 0xFFFFFFFF : 0xFF808080;
            Gui.drawRect(swatchX, boxY, swatchX + swatchSize, boxY + 1, swatchBorder);
            Gui.drawRect(swatchX, boxY + swatchSize - 1, swatchX + swatchSize, boxY + swatchSize,
                swatchBorder);
            Gui.drawRect(swatchX, boxY, swatchX + 1, boxY + swatchSize, swatchBorder);
            Gui.drawRect(swatchX + swatchSize - 1, boxY, swatchX + swatchSize, boxY + swatchSize,
                swatchBorder);
        }

        @Override
        void click(int button) {
            if (button == 0) {
                // 左键打开颜色选择器
                mc.displayGuiScreen(new GuiColorPicker(GuiDamageConfig.this,
                    getter.getAsInt(), v -> setter.accept(v)));
                return;
            }
            int current = getter.getAsInt() & 0xFFFFFF;
            int index = 0;
            for (int i = 0; i < COLOR_PALETTE.length; i++) {
                if ((COLOR_PALETTE[i] & 0xFFFFFF) == current) {
                    index = i;
                    break;
                }
            }
            index = (index + (button == 1 ? -1 : 1) + COLOR_PALETTE.length) % COLOR_PALETTE.length;
            setter.accept(0xFF000000 | (COLOR_PALETTE[index] & 0xFFFFFF));
        }
    }

    /** 点击后执行动作的行（例如打开加分设置 / HUD 编辑器）。 */
    private final class ActionRow extends OptionRow {
        private final String valueKey;
        private final Runnable action;
        private final boolean literal;
        private final int color;

        ActionRow(String key, String valueKey, Runnable action) {
            this(key, valueKey, action, false, COLOR_ON);
        }

        ActionRow(String key, String valueKey, Runnable action, boolean literal, int color) {
            super(key);
            this.valueKey = valueKey;
            this.action = action;
            this.literal = literal;
            this.color = color;
        }

        @Override
        String valueText() {
            return literal ? valueKey : I18n.format(valueKey);
        }

        @Override
        int valueColor() {
            return color;
        }

        @Override
        boolean boxedValue() {
            return true;
        }

        @Override
        void click(int button) {
            if (button == 0) {
                action.run();
            }
        }
    }

    /** 按键绑定行：点击后进入监听状态，按下的键直接写回原版按键设置。 */
    private final class KeybindRow extends OptionRow {
        private final KeyBinding binding;

        KeybindRow(String key, KeyBinding binding) {
            super(key);
            this.binding = binding;
        }

        @Override
        String valueText() {
            int code = binding.getKeyCode();
            String name = code == Keyboard.KEY_NONE
                ? I18n.format("key.damage_engine.not_bound") : Keyboard.getKeyName(code);
            if (listeningKeybind == this) {
                // 编辑状态与 1.21.1 一致：> 当前按键 <
                return "> " + name + " <";
            }
            if (code == Keyboard.KEY_NONE) return name;
            return conflicting(binding) ? "[ " + name + " ]" : name;
        }

        @Override
        int valueColor() {
            if (listeningKeybind == this) return 0xFFFFFF55;
            if (binding.getKeyCode() == Keyboard.KEY_NONE) return COLOR_TEXT;
            return conflicting(binding) ? COLOR_OFF : COLOR_ON;
        }

        @Override
        boolean boxedValue() {
            return true;
        }

        /** 冲突时提示具体冲突的按键。 */
        @Override
        String hintText() {
            if (listeningKeybind == this) return null;
            int code = binding.getKeyCode();
            if (code == Keyboard.KEY_NONE) return null;
            StringBuilder names = new StringBuilder();
            for (KeyBinding other : mc.gameSettings.keyBindings) {
                if (other != binding && other.getKeyCode() == code) {
                    if (names.length() > 0) names.append(", ");
                    names.append(I18n.format(other.getKeyDescription()));
                }
            }
            if (names.length() == 0) return null;
            return I18n.format("text.damage-engine.key_conflict") + "\n" + names;
        }

        @Override
        void click(int button) {
            if (button == 0) {
                listeningKeybind = this;
            }
        }
    }

    private boolean conflicting(KeyBinding binding) {
        int code = binding.getKeyCode();
        if (code == Keyboard.KEY_NONE) return false;
        for (KeyBinding other : mc.gameSettings.keyBindings) {
            if (other != binding && other.getKeyCode() == code) {
                return true;
            }
        }
        return false;
    }

    // ================= 折叠分组 =================

    private boolean isExpanded(String key) {
        // 搜索时展开所有分组，保证全局搜索能搜到组内选项
        if (!searchQuery.isEmpty()) return true;
        Boolean value = expandStates.get(key);
        return value != null && value;
    }

    private void rebuild() {
        float scroll = listScroll;
        buildRows();
        listScroll = Math.max(0f, Math.min(scroll, maxScroll()));
        listScrollTarget = listScroll;
    }

    /** 折叠组标题：整行可点，悬停高亮，右侧 +/- 提示（不使用按钮样式）。 */
    private final class ExpandRow extends Row {
        private final String labelKey;
        private final String expandKey;

        ExpandRow(String labelKey, String expandKey) {
            this.labelKey = labelKey;
            this.expandKey = expandKey;
        }

        @Override
        int height() {
            return ROW_H;
        }

        @Override
        void draw(int x, int y, int w, boolean hovered, int mouseX, int mouseY) {
            if (hovered) {
                Gui.drawRect(0, y, width, y + ROW_H - 2, 0x14FFFFFF);
            }
            int color = hovered ? 0xFFFBFB54 : 0xFFFFFFFF;
            fontRenderer.drawStringWithShadow(I18n.format(labelKey), x, y + (ROW_H - 8) / 2, color);
            String mark = isExpanded(expandKey) ? "-" : "+";
            fontRenderer.drawStringWithShadow(mark,
                x + w - RIGHT_EDGE - fontRenderer.getStringWidth(mark),
                y + (ROW_H - 8) / 2, hovered ? 0xFFFBFB54 : COLOR_MUTED);
        }

        @Override
        void click(int mouseX, int rowY, int button) {
            if (button != 0) return;
            expandStates.put(expandKey, !isExpanded(expandKey));
            rebuild();
        }

        @Override
        boolean matches(String query) {
            return I18n.format(labelKey).toLowerCase(Locale.ROOT)
                .contains(query.toLowerCase(Locale.ROOT));
        }
    }

    /** 组内的空列表 / 说明文字。 */
    private final class InfoRow extends Row {
        private final String key;

        InfoRow(String key) {
            this.key = key;
        }

        @Override
        int height() {
            return ROW_H;
        }

        @Override
        void draw(int x, int y, int w, boolean hovered, int mouseX, int mouseY) {
            fontRenderer.drawStringWithShadow(I18n.format(key), x, y + (ROW_H - 8) / 2,
                COLOR_MUTED);
        }

        @Override
        void click(int mouseX, int rowY, int button) {
        }
    }

    /** 组内的小号文本框。1.12.2 的 GuiTextField 没有 responder，这里自己轮询文本变化。 */
    private final class NestedField {
        final GuiTextField field;
        private Consumer<String> listener;
        private String lastText;

        NestedField(String value, int maxLength) {
            this.field = new GuiTextField(0, fontRenderer, -1000, -1000, 60, 12);
            this.field.setEnableBackgroundDrawing(false);
            this.field.setTextColor(0xFFFFFFFF);
            this.field.setDisabledTextColour(0xFF808080);
            this.field.setMaxStringLength(maxLength);
            this.field.setText(value);
            this.lastText = value;
            nestedFields.add(this);
        }

        void onChange(Consumer<String> listener) {
            this.listener = listener;
        }

        void tick() {
            String text = field.getText();
            if (!text.equals(lastText)) {
                lastText = text;
                if (listener != null) listener.accept(text);
            }
        }

        void setText(String text) {
            field.setText(text);
            lastText = text;
        }
    }

    private NestedField makeNestedField(String value, int maxLength) {
        return new NestedField(value, maxLength);
    }

    private void drawNestedBox(int x, int y, int w, int h, boolean focused) {
        Gui.drawRect(x, y, x + w, y + h, 0x30000000);
        int border = focused ? 0xFFFFFFFF : 0xFFA0A0A0;
        Gui.drawRect(x, y, x + w, y + 1, border);
        Gui.drawRect(x, y + h - 1, x + w, y + h, border);
        Gui.drawRect(x, y, x + 1, y + h, border);
        Gui.drawRect(x + w - 1, y, x + w, y + h, border);
    }

    /** 组内的删除按钮（纯文字 ×）。 */
    private void drawDelete(int x, int y, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + 16 && mouseY >= y && mouseY <= y + CONTROL_H;
        fontRenderer.drawStringWithShadow("\u00d7", x + 4, y + (CONTROL_H - 8) / 2,
            hovered ? 0xFFFC887E : COLOR_MUTED);
    }

    private boolean deleteHovered(int x, int y, int mouseX, int mouseY) {
        return mouseX >= x && mouseX <= x + 16 && mouseY >= y && mouseY <= y + CONTROL_H;
    }

    /** 组内的颜色预览块（点击打开颜色选择器）。 */
    private void drawSwatch(int x, int y, int color, int mouseX, int mouseY) {
        Gui.drawRect(x, y, x + CONTROL_H, y + CONTROL_H,
            0xFF000000 | (color & 0x00FFFFFF));
        boolean hovered = mouseX >= x && mouseX <= x + CONTROL_H
            && mouseY >= y && mouseY <= y + CONTROL_H;
        int border = hovered ? 0xFFFFFFFF : 0xFF808080;
        Gui.drawRect(x, y, x + CONTROL_H, y + 1, border);
        Gui.drawRect(x, y + CONTROL_H - 1, x + CONTROL_H, y + CONTROL_H, border);
        Gui.drawRect(x, y, x + 1, y + CONTROL_H, border);
        Gui.drawRect(x + CONTROL_H - 1, y, x + CONTROL_H, y + CONTROL_H, border);
    }

    private boolean swatchHovered(int x, int y, int mouseX, int mouseY) {
        return mouseX >= x && mouseX <= x + CONTROL_H && mouseY >= y && mouseY <= y + CONTROL_H;
    }

    /** 伤害阈值：阈值输入框 + 颜色（预览块 / Hex）+ 删除。 */
    private final class ThresholdRow extends Row {
        private final DamageEngineConfig.DamageThreshold threshold;
        private final NestedField valueField;
        private final NestedField hexField;

        ThresholdRow(DamageEngineConfig.DamageThreshold threshold) {
            this.threshold = threshold;
            this.valueField = makeNestedField(
                String.format(Locale.ROOT, "%.0f", threshold.threshold), 6);
            this.valueField.onChange(text -> {
                try {
                    threshold.threshold = Float.parseFloat(text.trim());
                } catch (Exception ignored) {
                }
            });
            this.hexField = makeNestedField(
                String.format(Locale.ROOT, "%06X", threshold.color & 0xFFFFFF), 6);
            this.hexField.onChange(text -> {
                String filtered = text.replaceAll("[^0-9a-fA-F]", "");
                if (!filtered.equals(text)) {
                    hexField.setText(filtered);
                    return;
                }
                try {
                    threshold.color = 0xFF000000 | (int) Long.parseLong(filtered, 16);
                } catch (Exception ignored) {
                }
            });
        }

        @Override
        int height() {
            return ROW_H;
        }

        @Override
        void draw(int x, int y, int w, boolean hovered, int mouseX, int mouseY) {
            if (hovered) {
                Gui.drawRect(0, y, width, y + ROW_H - 2, 0x14FFFFFF);
            }
            int cy = y + (ROW_H - CONTROL_H) / 2;
            int right = x + w - RIGHT_EDGE;
            int delX = right - 16;
            int swatchX = delX - 4 - CONTROL_H;
            int hexW = 52;
            int hexX = swatchX - 4 - hexW;
            int valW = 36;
            int valX = hexX - 4 - valW;

            fontRenderer.drawStringWithShadow(I18n.format("text.damage-engine.damage_reach"),
                x, y + (ROW_H - 8) / 2, COLOR_TEXT);
            drawNestedBox(valX, cy, valW, CONTROL_H, valueField.field.isFocused());
            valueField.field.x = valX + 4;
            valueField.field.y = cy + 6;
            valueField.field.width = valW - 8;
            valueField.field.drawTextBox();

            drawNestedBox(hexX, cy, hexW, CONTROL_H, hexField.field.isFocused());
            hexField.field.x = hexX + 4;
            hexField.field.y = cy + 6;
            hexField.field.width = hexW - 8;
            hexField.field.drawTextBox();

            drawSwatch(swatchX, cy, threshold.color, mouseX, mouseY);
            drawDelete(delX, cy, mouseX, mouseY);
        }

        @Override
        void click(int mouseX, int rowY, int button) {
            int cy = rowY + (ROW_H - CONTROL_H) / 2;
            int right = 10 + (width - 20) - RIGHT_EDGE;
            int delX = right - 16;
            int swatchX = delX - 4 - CONTROL_H;
            int hexW = 52;
            int hexX = swatchX - 4 - hexW;
            int valW = 36;
            int valX = hexX - 4 - valW;
            if (button == 0 && deleteHovered(delX, cy, mouseX, clickMouseY)) {
                config.damageThresholds.remove(threshold);
                config.save();
                rebuild();
                return;
            }
            if (button == 0 && (swatchHovered(swatchX, cy, mouseX, clickMouseY)
                || (mouseX >= hexX && mouseX <= hexX + hexW
                    && clickMouseY >= cy && clickMouseY <= cy + CONTROL_H))) {
                mc.displayGuiScreen(new GuiColorPicker(GuiDamageConfig.this,
                    threshold.color, v -> {
                        threshold.color = v;
                        hexField.setText(String.format(Locale.ROOT, "%06X", v & 0xFFFFFF));
                    }));
                return;
            }
            valueField.field.mouseClicked(mouseX, clickMouseY, button);
            hexField.field.mouseClicked(mouseX, clickMouseY, button);
        }
    }

    /** 实体信息：注册名 + 距离 + 删除。 */
    private final class EntityRuleRow extends Row {
        private final DamageEngineConfig.GlobalEntityRule rule;
        private final NestedField nameField;
        private final NestedField distField;

        EntityRuleRow(DamageEngineConfig.GlobalEntityRule rule) {
            this.rule = rule;
            this.nameField = makeNestedField(
                rule.registryName == null ? "" : rule.registryName, 64);
            this.nameField.onChange(text -> rule.registryName = text);
            this.distField = makeNestedField(
                String.format(Locale.ROOT, "%.0f", rule.distance), 7);
            this.distField.onChange(text -> {
                String filtered = text.replaceAll("[^0-9.]", "");
                if (!filtered.equals(text)) {
                    distField.setText(filtered);
                    return;
                }
                try {
                    rule.distance = Float.parseFloat(text);
                } catch (Exception ignored) {
                }
            });
        }

        @Override
        int height() {
            return ROW_H;
        }

        @Override
        void draw(int x, int y, int w, boolean hovered, int mouseX, int mouseY) {
            if (hovered) {
                Gui.drawRect(0, y, width, y + ROW_H - 2, 0x14FFFFFF);
            }
            int cy = y + (ROW_H - CONTROL_H) / 2;
            int right = x + w - RIGHT_EDGE;
            int delX = right - 16;

            String nameLabel = I18n.format("text.damage-engine.registry_name");
            fontRenderer.drawStringWithShadow(nameLabel, x, y + (ROW_H - 8) / 2, COLOR_TEXT);
            int nameX = x + fontRenderer.getStringWidth(nameLabel) + 6;
            int nameW = 96;
            drawNestedBox(nameX, cy, nameW, CONTROL_H, nameField.field.isFocused());
            nameField.field.x = nameX + 4;
            nameField.field.y = cy + 6;
            nameField.field.width = nameW - 8;
            nameField.field.drawTextBox();

            String distLabel = I18n.format("text.damage-engine.distance");
            int distLabelX = nameX + nameW + 10;
            fontRenderer.drawStringWithShadow(distLabel, distLabelX, y + (ROW_H - 8) / 2, COLOR_TEXT);
            int distX = distLabelX + fontRenderer.getStringWidth(distLabel) + 6;
            int distW = 40;
            drawNestedBox(distX, cy, distW, CONTROL_H, distField.field.isFocused());
            distField.field.x = distX + 4;
            distField.field.y = cy + 6;
            distField.field.width = distW - 8;
            distField.field.drawTextBox();

            drawDelete(delX, cy, mouseX, mouseY);
        }

        @Override
        void click(int mouseX, int rowY, int button) {
            int cy = rowY + (ROW_H - CONTROL_H) / 2;
            int right = 10 + (width - 20) - RIGHT_EDGE;
            int delX = right - 16;
            if (clickMouseY >= cy && clickMouseY <= cy + CONTROL_H
                && deleteHovered(delX, cy, mouseX, clickMouseY)) {
                if (button == 0) {
                    config.globalEntityRules.remove(rule);
                    config.save();
                    rebuild();
                }
                return;
            }
            nameField.field.mouseClicked(mouseX, clickMouseY, button);
            distField.field.mouseClicked(mouseX, clickMouseY, button);
        }
    }

    /** 评分等级：等级文本 + 最低分数 + 颜色 + 删除。 */
    private final class GradeRow extends Row {
        private final DamageEngineConfig.RatingGrade grade;
        private final NestedField textField;
        private final NestedField scoreField;

        GradeRow(DamageEngineConfig.RatingGrade grade) {
            this.grade = grade;
            this.textField = makeNestedField(grade.text == null ? "" : grade.text, 16);
            this.textField.onChange(text -> grade.text = text);
            this.scoreField = makeNestedField(
                String.format(Locale.ROOT, "%.0f", grade.minScore), 7);
            this.scoreField.onChange(text -> {
                try {
                    grade.minScore = Float.parseFloat(text.trim());
                } catch (Exception ignored) {
                }
            });
        }

        @Override
        int height() {
            return ROW_H;
        }

        @Override
        void draw(int x, int y, int w, boolean hovered, int mouseX, int mouseY) {
            if (hovered) {
                Gui.drawRect(0, y, width, y + ROW_H - 2, 0x14FFFFFF);
            }
            int cy = y + (ROW_H - CONTROL_H) / 2;
            int right = x + w - RIGHT_EDGE;
            int delX = right - 16;
            int swatchX = delX - 4 - CONTROL_H;

            int textW = 56;
            int textX = x;
            drawNestedBox(textX, cy, textW, CONTROL_H, textField.field.isFocused());
            textField.field.x = textX + 4;
            textField.field.y = cy + 6;
            textField.field.width = textW - 8;
            textField.field.drawTextBox();

            int scoreW = 44;
            int scoreX = textX + textW + 8;
            drawNestedBox(scoreX, cy, scoreW, CONTROL_H, scoreField.field.isFocused());
            scoreField.field.x = scoreX + 4;
            scoreField.field.y = cy + 6;
            scoreField.field.width = scoreW - 8;
            scoreField.field.drawTextBox();

            drawSwatch(swatchX, cy, grade.color, mouseX, mouseY);
            drawDelete(delX, cy, mouseX, mouseY);
        }

        @Override
        void click(int mouseX, int rowY, int button) {
            int cy = rowY + (ROW_H - CONTROL_H) / 2;
            int right = 10 + (width - 20) - RIGHT_EDGE;
            int delX = right - 16;
            int swatchX = delX - 4 - CONTROL_H;
            int textW = 56;
            int textX = 10;
            if (button == 0 && deleteHovered(delX, cy, mouseX, clickMouseY)) {
                config.ratingGrades.remove(grade);
                config.save();
                rebuild();
                return;
            }
            if (button == 0 && swatchHovered(swatchX, cy, mouseX, clickMouseY)) {
                mc.displayGuiScreen(new GuiColorPicker(GuiDamageConfig.this, grade.color, v -> {
                    grade.color = v;
                }));
                return;
            }
            textField.field.mouseClicked(mouseX, clickMouseY, button);
            scoreField.field.mouseClicked(mouseX, clickMouseY, button);
        }
    }

    /** 击杀文本：右侧一个输入框。 */
    private final class TextRow extends OptionRow {
        private final NestedField field;
        private final Supplier<String> getter;
        private final Consumer<String> setter;

        TextRow(String key, Supplier<String> getter, Consumer<String> setter) {
            super(key);
            this.getter = getter;
            this.setter = setter;
            this.field = makeNestedField(getter.get(), 64);
            this.field.onChange(setter);
        }

        @Override
        String valueText() {
            return getter.get();
        }

        @Override
        void draw(int x, int y, int w, boolean hovered, int mouseX, int mouseY) {
            if (hovered) {
                Gui.drawRect(0, y, width, y + ROW_H - 2, 0x14FFFFFF);
            }
            int cy = y + (ROW_H - CONTROL_H) / 2;
            int boxX = x + w - RIGHT_EDGE - CONTROL_W;
            fontRenderer.drawStringWithShadow(I18n.format(labelKey), x, y + (ROW_H - 8) / 2,
                COLOR_TEXT);
            drawNestedBox(boxX, cy, CONTROL_W, CONTROL_H, field.field.isFocused());
            field.field.x = boxX + 4;
            field.field.y = cy + 6;
            field.field.width = CONTROL_W - 8;
            field.field.drawTextBox();
        }

        @Override
        void click(int mouseX, int rowY, int button) {
            if (!inControlArea(mouseX)) return;
            field.field.mouseClicked(mouseX, clickMouseY, button);
        }

        @Override
        void click(int button) {
        }
    }

    /** 重置所有设置：两段式确认，确认后清空折叠状态并回到第一页。 */
    private final class ResetRow extends OptionRow {
        ResetRow() {
            super("option.damage-engine.reset");
        }

        @Override
        String valueText() {
            if (resetState == 1) return I18n.format("gui.confirm") + "?";
            if (resetState == 2) return I18n.format("text.damage-engine.reset_done");
            return I18n.format("gui.reset");
        }

        @Override
        int valueColor() {
            return resetState == 2 ? COLOR_ON : COLOR_OFF;
        }

        @Override
        boolean boxedValue() {
            return true;
        }

        @Override
        void click(int button) {
            if (button != 0) return;
            long now = System.currentTimeMillis();
            if (resetState == 1 && now - resetActionTime <= 5000) {
                resetState = 2;
                resetActionTime = now;
                config.resetToDefaults();
                expandStates.clear();
                selectedTab = 0;
                listScroll = 0f;
                listScrollTarget = 0f;
                buildRows();
                return;
            }
            resetState = 1;
            resetActionTime = now;
        }
    }

    /** 数值输入框行（对应 1.21.1 的 NumericEntry，而不是滑条）。 */
    private final class NumberRow extends OptionRow {
        private final NestedField field;
        private final DoubleSupplier getter;
        private final DoubleConsumer setter;
        private final double min;
        private final double max;
        private final boolean integer;

        NumberRow(String key, DoubleSupplier getter, DoubleConsumer setter,
                  double min, double max, boolean integer) {
            super(key);
            this.getter = getter;
            this.setter = setter;
            this.min = min;
            this.max = max;
            this.integer = integer;
            this.field = makeNestedField(format(getter.getAsDouble()), 12);
            this.field.onChange(text -> {
                String trimmed = text.trim();
                if (trimmed.isEmpty() || "-".equals(trimmed) || ".".equals(trimmed)) return;
                try {
                    double value = Double.parseDouble(trimmed);
                    if (integer) value = Math.round(value);
                    setter.accept(Math.max(min, Math.min(max, value)));
                } catch (NumberFormatException ignored) {
                }
            });
        }

        private String format(double value) {
            return integer ? String.valueOf(Math.round(value))
                : String.format(Locale.ROOT, "%.1f", value);
        }

        @Override
        String valueText() {
            return format(getter.getAsDouble());
        }

        @Override
        void draw(int x, int y, int w, boolean hovered, int mouseX, int mouseY) {
            if (hovered) {
                Gui.drawRect(0, y, width, y + ROW_H - 2, 0x14FFFFFF);
            }
            int cy = y + (ROW_H - CONTROL_H) / 2;
            int boxX = x + w - RIGHT_EDGE - CONTROL_W;
            fontRenderer.drawStringWithShadow(I18n.format(labelKey), x, y + (ROW_H - 8) / 2,
                COLOR_TEXT);
            // 未聚焦时把输入框内容同步成真实值（限制范围 / 补齐小数）
            if (!field.field.isFocused()) {
                String text = format(getter.getAsDouble());
                if (!text.equals(field.field.getText())) {
                    field.setText(text);
                }
            }
            drawNestedBox(boxX, cy, CONTROL_W, CONTROL_H, field.field.isFocused());
            field.field.x = boxX + 4;
            field.field.y = cy + 6;
            field.field.width = CONTROL_W - 8;
            field.field.drawTextBox();
        }

        @Override
        void click(int mouseX, int rowY, int button) {
            if (!inControlArea(mouseX)) return;
            field.field.mouseClicked(mouseX, clickMouseY, button);
        }

        @Override
        void click(int button) {
        }
    }

    /** 列表底部的留白（与 1.21.1 一致，让内容可以继续滚动）。 */
    private final class SpacerRow extends Row {
        private final int h;

        SpacerRow(int h) {
            this.h = h;
        }

        @Override
        int height() {
            return h;
        }

        @Override
        void draw(int x, int y, int w, boolean hovered, int mouseX, int mouseY) {
        }

        @Override
        void click(int mouseX, int rowY, int button) {
        }
    }

    // ================= 按钮 =================

    private static final class Btn {
        final int x;
        final int y;
        final int w;
        final int h;
        final String label;
        final int color;
        final Supplier<String> dynamicLabel;
        final IntSupplier dynamicColor;
        /** 可选的第二段文字（单独着色），用于「预览：开启/关闭」这类按钮。 */
        final Supplier<String> suffixLabel;
        final IntSupplier suffixColor;
        final Runnable action;

        Btn(int x, int y, int w, int h, String label, int color, Runnable action) {
            this(x, y, w, h, label, color, null, null, null, null, action);
        }

        Btn(int x, int y, int w, int h, String label, int color,
            Supplier<String> dynamicLabel, IntSupplier dynamicColor, Runnable action) {
            this(x, y, w, h, label, color, dynamicLabel, dynamicColor, null, null, action);
        }

        Btn(int x, int y, int w, int h, String label, int color,
            Supplier<String> dynamicLabel, IntSupplier dynamicColor,
            Supplier<String> suffixLabel, IntSupplier suffixColor, Runnable action) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.label = label;
            this.color = color;
            this.dynamicLabel = dynamicLabel;
            this.dynamicColor = dynamicColor;
            this.suffixLabel = suffixLabel;
            this.suffixColor = suffixColor;
            this.action = action;
        }

        String text() {
            return dynamicLabel != null ? dynamicLabel.get() : label;
        }

        int textColor() {
            return dynamicColor != null ? dynamicColor.getAsInt() : color;
        }

        String suffixText() {
            return suffixLabel != null ? suffixLabel.get() : null;
        }

        int suffixTextColor() {
            return suffixColor != null ? suffixColor.getAsInt() : 0xFFFFFFFF;
        }

        boolean hovered(int mouseX, int mouseY) {
            return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        }
    }

    // ================= 初始化 =================

    @Override
    public void initGui() {
        listTop = TOP_BAR_H + 4;
        listBottom = height - 35;

        int searchWidth = Math.min(120, Math.max(80, width / 7));
        searchBox = new GuiTextField(0, fontRenderer, 6, 7, searchWidth, 10);
        searchBox.setEnableBackgroundDrawing(false);
        searchBox.setMaxStringLength(50);
        searchBox.setTextColor(0xFFFFFFFF);
        searchBox.setDisabledTextColour(0xFF808080);
        searchBox.setCanLoseFocus(true);
        searchBox.setText(searchQuery);
        tabsLeft = 6 + searchWidth + 4;
        tabsRight = width - 6;

        for (int i = 0; i < TAB_KEYS.length; i++) {
            tabWidths[i] = Math.max(52, fontRenderer.getStringWidth(I18n.format(TAB_KEYS[i])) + 18);
        }
        tabScroll = Math.max(0f, Math.min(tabScroll, maxTabScroll()));
        tabScrollTarget = Math.max(0f, Math.min(tabScrollTarget, maxTabScroll()));
        // 窗口尺寸变化后下划线重新贴合选中标签
        indicatorX = -1f;

        buildRows();
        buildButtons();
        listScroll = Math.max(0f, Math.min(listScroll, maxScroll()));
        listScrollTarget = Math.max(0f, Math.min(listScrollTarget, maxScroll()));
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        // 重置按钮的确认状态超时自动恢复
        if (resetState != 0) {
            long now = System.currentTimeMillis();
            if ((resetState == 1 && now - resetActionTime > 5000)
                || (resetState == 2 && now - resetActionTime > 3000)) {
                resetState = 0;
            }
        }
        for (NestedField nested : nestedFields) {
            nested.field.updateCursorCounter();
            nested.tick();
        }
        if (searchBox == null) return;
        searchBox.updateCursorCounter();
        String query = searchBox.getText().trim();
        if (!query.equals(searchQuery)) {
            searchQuery = query;
            listScroll = 0f;
            listScrollTarget = 0f;
            buildRows();
        }
    }

    private void buildRows() {
        rows.clear();
        nestedFields.clear();
        if (!searchQuery.isEmpty()) {
            // 全局搜索：命中的分类下面列出匹配项
            for (int tab = 0; tab < TAB_KEYS.length; tab++) {
                List<Row> matched = new ArrayList<>();
                for (Row row : rowsForTab(tab)) {
                    if (row.matches(searchQuery)) {
                        matched.add(row);
                    }
                }
                if (!matched.isEmpty()) {
                    rows.add(new HeaderRow(TAB_KEYS[tab]));
                    rows.addAll(matched);
                }
            }
            return;
        }
        rows.addAll(rowsForTab(selectedTab));
    }

    private List<Row> rowsForTab(int tab) {
        List<Row> list = new ArrayList<>();
        switch (tab) {
            case 0:
                list.add(new BoolRow("option.damage-engine.showDamage",
                    () -> config.showDamage, v -> config.showDamage = v));
                list.add(new BoolRow("option.damage-engine.reduceAnimations",
                    () -> config.reduceAnimations, v -> config.reduceAnimations = v));
                list.add(new NumberRow("option.damage-engine.hudMaxFps",
                    () -> config.hudMaxFps, v -> config.hudMaxFps = (int) v, 0, 480, true));
                list.add(new ActionRow("option.damage-engine.edit_pos_label",
                    "button.damage-engine.adjust", () -> {
                        config.save();
                        mc.displayGuiScreen(new GuiHudEditor(this));
                    }));
                list.add(new ChoiceRow("option.damage-engine.hudInertia",
                    () -> config.hudInertiaMode, v -> config.hudInertiaMode = v,
                    new String[]{"off", "de_only", "all"}));
                list.add(new IntRow("option.damage-engine.hudInertiaStrength",
                    () -> config.hudInertiaStrength, v -> config.hudInertiaStrength = v, 0, 200, 10));
                list.add(new BoolRow("option.damage-engine.numberSeparator",
                    () -> config.numberSeparator, v -> config.numberSeparator = v));
                list.add(new BoolRow("option.damage-engine.abbreviateNumbers",
                    () -> config.abbreviateNumbers, v -> config.abbreviateNumbers = v));
                list.add(new BoolRow("option.damage-engine.showDamageOverflow",
                    () -> config.showDamageOverflow, v -> config.showDamageOverflow = v));
                list.add(new BoolRow("option.damage-engine.hideOnF1",
                    () -> config.hideOnF1, v -> config.hideOnF1 = v));
                list.add(new SpacerRow(40));
                break;
            case 1:
                list.add(new BoolRow("option.damage-engine.showDamageDisplay",
                    () -> config.showDamageDisplay, v -> config.showDamageDisplay = v));
                list.add(new BoolRow("option.damage-engine.showCombo",
                    () -> config.showCombo, v -> config.showCombo = v));
                list.add(new BoolRow("option.damage-engine.showDamageHistory",
                    () -> config.showDamageHistory, v -> config.showDamageHistory = v));
                list.add(new IntRow("option.damage-engine.historyLimit",
                    () -> config.historyLimit, v -> config.historyLimit = v, 1, 50, 1));
                list.add(new IntRow("option.damage-engine.historyDecimalPlaces",
                    () -> config.historyDecimalPlaces, v -> config.historyDecimalPlaces = v, 0, 3, 1));
                list.add(new IntRow("option.damage-engine.decimalPlaces",
                    () -> config.decimalPlaces, v -> config.decimalPlaces = v, 0, 3, 1));
                list.add(new ChoiceRow("option.damage-engine.alignMode",
                    () -> config.alignMode, v -> config.alignMode = v,
                    new String[]{"right", "left"}));
                list.add(new BoolRow("option.damage-engine.resetEnabled",
                    () -> config.resetEnabled, v -> config.resetEnabled = v));
                list.add(new NumberRow("option.damage-engine.resetTime",
                    () -> config.resetTime, v -> config.resetTime = (float) v, 0, 600, false));
                list.add(new BoolRow("option.damage-engine.showProgressBar",
                    () -> config.showProgressBar, v -> config.showProgressBar = v));
                list.add(new NumberRow("option.damage-engine.historyDisappearanceTime",
                    () -> config.historyDisappearanceTime,
                    v -> config.historyDisappearanceTime = (float) v, 0, 600, false));
                list.add(new BoolRow("option.damage-engine.recordOtherPlayers",
                    () -> config.recordOtherPlayers, v -> config.recordOtherPlayers = v));
                list.add(new ColorRow("option.damage-engine.normalColor",
                    () -> config.normalColor, v -> config.normalColor = v));
                list.add(new ColorRow("option.damage-engine.critColor",
                    () -> config.critColor, v -> config.critColor = v));
                // 总伤害显示颜色：按达到伤害分层（可展开分组）
                if (config.damageThresholds != null) {
                    config.damageThresholds.sort((a, b) -> Float.compare(a.threshold, b.threshold));
                }
                list.add(new ExpandRow("option.damage-engine.total_damage_colors",
                    "total_damage_colors"));
                if (isExpanded("total_damage_colors")) {
                    if (config.damageThresholds == null || config.damageThresholds.isEmpty()) {
                        list.add(new InfoRow("text.damage-engine.empty_list"));
                    } else {
                        for (DamageEngineConfig.DamageThreshold threshold
                            : new ArrayList<>(config.damageThresholds)) {
                            list.add(new ThresholdRow(threshold));
                        }
                    }
                    list.add(new ActionRow("button.damage-engine.add", "+", () -> {
                        float next = 0f;
                        if (config.damageThresholds != null && !config.damageThresholds.isEmpty()) {
                            next = config.damageThresholds.get(
                                config.damageThresholds.size() - 1).threshold + 50f;
                        }
                        config.damageThresholds.add(
                            new DamageEngineConfig.DamageThreshold(next, 0xFFFFFFFF));
                        config.save();
                        rebuild();
                    }, true, COLOR_ON));
                }
                list.add(new ColorRow("option.damage-engine.progressBarColor",
                    () -> config.progressBarColor, v -> config.progressBarColor = v));
                list.add(new ColorRow("option.damage-engine.comboColor",
                    () -> config.comboColor, v -> config.comboColor = v));
                list.add(new SpacerRow(80));
                break;
            case 2:
                list.add(new BoolRow("option.damage-engine.showDamageIndicator",
                    () -> config.showDamageIndicator, v -> config.showDamageIndicator = v));
                list.add(new NumberRow("option.damage-engine.indicatorMaxFps",
                    () -> config.indicatorMaxFps, v -> config.indicatorMaxFps = (int) v, 0, 480, true));
                list.add(new NumberRow("option.damage-engine.indicatorMaxCount",
                    () -> config.indicatorMaxCount, v -> config.indicatorMaxCount = (int) v,
                    0, 1000, true));
                list.add(new BoolRow("option.damage-engine.indicator_merge",
                    () -> config.indicatorMerge, v -> config.indicatorMerge = v));
                list.add(new ChoiceRow("option.damage-engine.indicator_track_mode",
                    () -> config.indicatorTrackMode, v -> config.indicatorTrackMode = v,
                    new String[]{"off", "entity", "crosshair"}));
                list.add(new IntRow("option.damage-engine.indicatorDecimalPlaces",
                    () -> config.indicatorDecimalPlaces, v -> config.indicatorDecimalPlaces = v, 0, 3, 1));
                list.add(new ChoiceRow("option.damage-engine.indicatorMode",
                    () -> config.indicatorMode, v -> config.indicatorMode = v,
                    new String[]{"enhanced", "cloud"}));
                list.add(new FloatRow("option.damage-engine.indicatorScale",
                    () -> config.indicatorScale, v -> config.indicatorScale = (float) v, 0.2, 4.0, 0.1));
                list.add(new IntRow("option.damage-engine.indicatorOpacity",
                    () -> config.indicatorOpacity, v -> config.indicatorOpacity = v, 0, 100, 1));
                list.add(new BoolRow("option.damage-engine.indicatorBold",
                    () -> config.indicatorBold, v -> config.indicatorBold = v));
                list.add(new ColorRow("option.damage-engine.damageIndicatorNormalColor",
                    () -> config.damageIndicatorNormalColor, v -> config.damageIndicatorNormalColor = v));
                list.add(new ColorRow("option.damage-engine.damageIndicatorCritColor",
                    () -> config.damageIndicatorCritColor, v -> config.damageIndicatorCritColor = v));
                list.add(new BoolRow("option.damage-engine.showHealIndicator",
                    () -> config.showHealIndicator, v -> config.showHealIndicator = v));
                list.add(new ColorRow("option.damage-engine.healIndicatorColor",
                    () -> config.healIndicatorColor, v -> config.healIndicatorColor = v));

                // 全局伤害数字（展开分组）
                list.add(new ExpandRow("option.damage-engine.global_damage_numbers",
                    "global_damage_numbers"));
                if (isExpanded("global_damage_numbers")) {
                    list.add(new BoolRow("option.damage-engine.showGlobalDamageIndicator",
                        () -> config.showGlobalDamageIndicator,
                        v -> config.showGlobalDamageIndicator = v));
                    list.add(new NumberRow("option.damage-engine.global_player_distance",
                        () -> config.globalIndicatorMaxDistance,
                        v -> config.globalIndicatorMaxDistance = (float) v, 0, 512, false));
                    list.add(new BoolRow("option.damage-engine.global_smart_hide",
                        () -> config.globalIndicatorSmartHide,
                        v -> config.globalIndicatorSmartHide = v));

                    list.add(new ExpandRow("option.damage-engine.non_player_entities",
                        "non_player_entities"));
                    if (isExpanded("non_player_entities")) {
                        list.add(new BoolRow("option.damage-engine.entity_mode",
                            () -> config.globalEntityBlockMode,
                            v -> config.globalEntityBlockMode = v));
                        if (config.globalEntityRules == null || config.globalEntityRules.isEmpty()) {
                            list.add(new InfoRow("text.damage-engine.empty_list"));
                        } else {
                            for (DamageEngineConfig.GlobalEntityRule rule
                                : new ArrayList<>(config.globalEntityRules)) {
                                list.add(new EntityRuleRow(rule));
                            }
                        }
                        list.add(new ActionRow("button.damage-engine.add", "+", () -> {
                            if (config.globalEntityRules == null) {
                                config.globalEntityRules = new ArrayList<>();
                            }
                            config.globalEntityRules.add(
                                new DamageEngineConfig.GlobalEntityRule("", 0f));
                            config.save();
                            rebuild();
                        }, true, COLOR_ON));
                    }
                }

                list.add(new BoolRow("option.damage-engine.indicatorPrefixSign",
                    () -> config.indicatorPrefixSign, v -> config.indicatorPrefixSign = v));
                list.add(new BoolRow("option.damage-engine.showKillIndicator",
                    () -> config.showKillIndicator, v -> config.showKillIndicator = v));
                list.add(new TextRow("option.damage-engine.killText",
                    () -> config.killText, v -> config.killText = v));
                list.add(new ColorRow("option.damage-engine.damageIndicatorKillColor",
                    () -> config.damageIndicatorKillColor, v -> config.damageIndicatorKillColor = v));
                list.add(new ColorRow("option.damage-engine.killTextColor",
                    () -> config.killTextColor, v -> config.killTextColor = v));
                list.add(new SpacerRow(80));
                break;
            case 3:
                list.add(new BoolRow("option.damage-engine.showInfo",
                    () -> config.showInfo, v -> config.showInfo = v));
                list.add(new NumberRow("option.damage-engine.infoTrackTime",
                    () -> config.infoTrackTime, v -> config.infoTrackTime = (float) v, 0, 600, false));
                list.add(new BoolRow("option.damage-engine.entity_render",
                    () -> config.entityRenderEnabled, v -> config.entityRenderEnabled = v));
                list.add(new ChoiceRow("option.damage-engine.entity_render_rotation",
                    () -> config.entityRenderRotation, v -> config.entityRenderRotation = v,
                    new String[]{"follow", "custom"}));
                list.add(new IntRow("option.damage-engine.entity_render_rotation_angle",
                    () -> config.entityRenderRotationAngle,
                    v -> config.entityRenderRotationAngle = v, 0, 360, 15));
                list.add(new BoolRow("option.damage-engine.crosshair_entity_info",
                    () -> config.crosshairEntityInfo, v -> config.crosshairEntityInfo = v));
                list.add(new BoolRow("option.damage-engine.infoNoRoundedBorder",
                    () -> config.infoNoRoundedBorder, v -> config.infoNoRoundedBorder = v));
                list.add(new IntRow("option.damage-engine.infoBackgroundOpacity",
                    () -> config.infoBackgroundOpacity, v -> config.infoBackgroundOpacity = v, 0, 100, 5));
                list.add(new ColorRow("option.damage-engine.infoBackgroundColor",
                    () -> config.infoBackgroundColor, v -> config.infoBackgroundColor = v));
                list.add(new ColorRow("option.damage-engine.infoBarColor",
                    () -> config.infoBarColor, v -> config.infoBarColor = v));
                list.add(new ColorRow("option.damage-engine.infoBarHealColor",
                    () -> config.infoBarHealColor, v -> config.infoBarHealColor = v));
                list.add(new ColorRow("option.damage-engine.infoBarDamageColor",
                    () -> config.infoBarDamageColor, v -> config.infoBarDamageColor = v));
                list.add(new SpacerRow(80));
                break;
            case 4:
                list.add(new BoolRow("option.damage-engine.showRating",
                    () -> config.showRating, v -> config.showRating = v));
                list.add(new BoolRow("option.damage-engine.showRatingScore",
                    () -> config.showRatingScore, v -> config.showRatingScore = v));
                list.add(new BoolRow("option.damage-engine.showRatingScoreSource",
                    () -> config.showRatingScoreSource, v -> config.showRatingScoreSource = v));
                list.add(new IntRow("option.damage-engine.ratingScoreMaxGains",
                    () -> config.ratingScoreMaxGains, v -> config.ratingScoreMaxGains = v, 1, 10, 1));
                list.add(new NumberRow("option.damage-engine.ratingResetTime",
                    () -> config.ratingResetTime, v -> config.ratingResetTime = (float) v,
                    0, 600, false));
                list.add(new NumberRow("option.damage-engine.comboResetTime",
                    () -> config.comboResetTime, v -> config.comboResetTime = (float) v,
                    0, 600, false));
                list.add(new ColorRow("option.damage-engine.ratingScoreColor",
                    () -> config.ratingScoreColor, v -> config.ratingScoreColor = v));
                list.add(new ActionRow("option.damage-engine.rating_points",
                    ">", () -> {
                        config.save();
                        mc.displayGuiScreen(new GuiBonusConfig(this));
                    }, true, COLOR_TEXT));

                // 评分等级（展开分组）
                if (config.ratingGrades != null) {
                    config.ratingGrades.sort((a, b) -> Float.compare(b.minScore, a.minScore));
                }
                list.add(new ExpandRow("option.damage-engine.rating_grades", "rating_grades"));
                if (isExpanded("rating_grades")) {
                    if (config.ratingGrades == null || config.ratingGrades.isEmpty()) {
                        list.add(new InfoRow("text.damage-engine.empty_list"));
                    } else {
                        for (DamageEngineConfig.RatingGrade grade
                            : new ArrayList<>(config.ratingGrades)) {
                            list.add(new GradeRow(grade));
                        }
                    }
                    list.add(new ActionRow("button.damage-engine.add", "+", () -> {
                        if (config.ratingGrades == null) {
                            config.ratingGrades = new ArrayList<>();
                        }
                        config.ratingGrades.add(new DamageEngineConfig.RatingGrade(
                            0f, "S", 0xFFFFFFFF, config.ratingGrades.size() + 1));
                        config.save();
                        rebuild();
                    }, true, COLOR_ON));
                }
                list.add(new SpacerRow(80));
                break;
            case 5:
                // 按键绑定内嵌在本界面，不再跳到原版设置
                if (DamageEngineClient.configKey != null) {
                    list.add(new KeybindRow("key.damage_engine.config", DamageEngineClient.configKey));
                }
                if (DamageEngineClient.toggleHudKey != null) {
                    list.add(new KeybindRow("key.damage_engine.toggle_hud",
                        DamageEngineClient.toggleHudKey));
                }
                if (DamageEngineClient.clearKey != null) {
                    list.add(new KeybindRow("key.damage_engine.clear_damage",
                        DamageEngineClient.clearKey));
                }
                list.add(new SpacerRow(40));
                break;
            default:
                list.add(new BoolRow("option.damage-engine.shareDamage",
                    () -> config.shareDamage, v -> config.shareDamage = v));
                // 调试模式（展开分组）
                list.add(new ExpandRow("option.damage-engine.debugMode", "debugMode"));
                if (isExpanded("debugMode")) {
                    list.add(new BoolRow("option.damage-engine.debugShowDamageInfo",
                        () -> config.debugShowDamageInfo, v -> config.debugShowDamageInfo = v));
                    list.add(new BoolRow("option.damage-engine.debugShowPerformance",
                        () -> config.debugShowPerformance, v -> config.debugShowPerformance = v));
                }
                list.add(new ResetRow());
                list.add(new SpacerRow(80));
                break;
        }
        return list;
    }

    private void buildButtons() {
        buttons.clear();
        int by = height - 30;
        // 与列表内的按钮/输入框/滑动条保持同一长度
        int buttonWidth = CONTROL_W;

        int saveX = width - 10 - buttonWidth;
        int cancelX = saveX - 6 - buttonWidth;
        buttons.add(new Btn(saveX, by, buttonWidth, BTN_H,
            I18n.format("button.damage-engine.save_close"), 0xFFB7F3C8, () -> {
                config.save();
                mc.displayGuiScreen(parent);
            }));
        buttons.add(new Btn(cancelX, by, buttonWidth, BTN_H,
            I18n.format("gui.cancel"), COLOR_OFF, () -> {
                config.load();
                mc.displayGuiScreen(parent);
            }));

        // 左下角：预览开关（与 1.21.1 一致）；「预览：」保持白色，开关状态单独着色
        buttons.add(new Btn(10, by, buttonWidth, BTN_H, null, 0xFFFFFFFF,
            () -> I18n.format("text.damage-engine.preview") + ": ",
            null,
            () -> I18n.format(config.previewEnabled ? "options.on" : "options.off"),
            () -> config.previewEnabled ? COLOR_ON : COLOR_OFF,
            () -> config.previewEnabled = !config.previewEnabled));
    }

    // ================= 绘制 =================

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);

        // 原版泥土背景 + 轻微压暗，保持文字可读
        drawDefaultBackground();
        Gui.drawRect(0, 0, width, height, 0x50000000);

        // 预览开启时在配置界面背后显示 HUD 预览
        if (config.previewEnabled) {
            DamageHudRenderer.renderPreviewModules();
        }

        // 标签栏横向滚动动画（与 1.21.1 一致）
        long now = System.currentTimeMillis();
        float dtSec = lastFrameMs == 0 ? 0.016f
            : Math.max(0.001f, Math.min(0.1f, (now - lastFrameMs) / 1000.0f));
        lastFrameMs = now;
        float smooth = 1.0f - (float) Math.exp(-18.0f * dtSec);
        tabScroll += (tabScrollTarget - tabScroll) * smooth;
        if (Math.abs(tabScroll - tabScrollTarget) < 0.4f) {
            tabScroll = tabScrollTarget;
        }
        // 列表滚动同样做平滑处理
        listScroll += (listScrollTarget - listScroll) * smooth;
        if (Math.abs(listScroll - listScrollTarget) < 0.4f) {
            listScroll = listScrollTarget;
        }
        updateTabIndicator(smooth);
        for (NestedField nested : nestedFields) {
            nested.tick();
        }

        drawSearchBox();
        drawTabs(mouseX, mouseY);
        drawRows(mouseX, mouseY);
        drawScrollbar();

        Gui.drawRect(0, height - 35, width, height - 34, COLOR_BORDER);
        for (Btn button : buttons) {
            drawButton(button, mouseX, mouseY);
        }

        drawHoverTooltip(mouseX, mouseY);
    }

    /** 选中标签的绿色下划线在切换时平滑滑动，而不是瞬间跳过去。 */
    private void updateTabIndicator(float smooth) {
        float targetX = tabsLeft - tabScroll + tabStart(selectedTab);
        float targetW = tabWidths[selectedTab];
        if (indicatorX < 0f) {
            indicatorX = targetX;
            indicatorW = targetW;
            return;
        }
        indicatorX += (targetX - indicatorX) * smooth;
        indicatorW += (targetW - indicatorW) * smooth;
        if (Math.abs(indicatorX - targetX) < 0.3f) indicatorX = targetX;
        if (Math.abs(indicatorW - targetW) < 0.3f) indicatorW = targetW;
    }

    /** 悬停选项时显示与 1.21.1 同款的自定义提示框。 */
    private void drawHoverTooltip(int mouseX, int mouseY) {
        Row hovered = hoveredRow(mouseX, mouseY);
        if (!(hovered instanceof OptionRow)) return;
        String hint = ((OptionRow) hovered).hintText();
        if (hint == null || hint.isEmpty()) return;
        drawTooltip(hint, mouseX, mouseY);
    }

    private Row hoveredRow(int mouseX, int mouseY) {
        if (mouseY < listTop || mouseY > listBottom) return null;
        if (mouseX < 0 || mouseX > width) return null;
        int y = listTop - Math.round(listScroll);
        for (Row row : rows) {
            int h = row.height();
            if (mouseY >= y && mouseY < y + h) return row;
            y += h;
        }
        return null;
    }

    private static final int TIP_PAD = 3;
    private static final int TIP_MAX_W = 200;

    /** 深色半透明底、无边框，按像素宽度换行，贴边时翻到另一侧。 */
    private void drawTooltip(String text, int mouseX, int mouseY) {
        List<String> lines = new ArrayList<>();
        // 1.12.2 的 .lang 里 \n 可能保留为字面量，两种写法都按换行处理
        for (String paragraph : text.split("\\\\n|\\n", -1)) {
            StringBuilder line = new StringBuilder();
            int lastSpace = -1;
            for (int i = 0; i < paragraph.length(); i++) {
                char c = paragraph.charAt(i);
                if (fontRenderer.getStringWidth(line.toString() + c) > TIP_MAX_W
                    && line.length() > 0) {
                    if (lastSpace > 0) {
                        String rest = line.substring(lastSpace + 1);
                        lines.add(line.substring(0, lastSpace));
                        line.setLength(0);
                        line.append(rest);
                    } else {
                        lines.add(line.toString());
                        line.setLength(0);
                    }
                    lastSpace = -1;
                }
                if (c == ' ') lastSpace = line.length();
                line.append(c);
            }
            lines.add(line.toString());
        }

        int boxW = 0;
        for (String line : lines) {
            boxW = Math.max(boxW, fontRenderer.getStringWidth(line));
        }
        boxW += TIP_PAD * 2;
        int lineH = fontRenderer.FONT_HEIGHT;
        int boxH = lines.size() * lineH + TIP_PAD * 2;

        int x = mouseX + 10;
        int y = mouseY + 12;
        if (x + boxW > width) x = mouseX - 10 - boxW;
        if (y + boxH > height) y = mouseY - 12 - boxH;
        x = Math.max(0, Math.min(x, Math.max(0, width - boxW)));
        y = Math.max(0, Math.min(y, Math.max(0, height - boxH)));

        Gui.drawRect(x, y, x + boxW, y + boxH, 0xF0101014);
        for (int i = 0; i < lines.size(); i++) {
            fontRenderer.drawStringWithShadow(lines.get(i), x + TIP_PAD,
                y + TIP_PAD + i * lineH, 0xFFE6E6E6);
        }
    }

    private void drawSearchBox() {
        searchBox.drawTextBox();
        if (searchQuery.isEmpty() && !searchBox.isFocused()) {
            fontRenderer.drawStringWithShadow(I18n.format("text.damage-engine.search"),
                7, 8, 0xFF808080);
        }
    }

    private void drawTabs(int mouseX, int mouseY) {
        // 顶部与列表使用同一层压暗，不再额外加深（否则上下会比中间更暗）
        int factor = new net.minecraft.client.gui.ScaledResolution(mc).getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(tabsLeft * factor, mc.displayHeight - TOP_BAR_H * factor,
            (tabsRight - tabsLeft) * factor, TOP_BAR_H * factor);

        // 选中态：整体底色 + 底部绿条都跟随补间位置滑动
        int ix = Math.round(indicatorX);
        int iw = Math.round(indicatorW);
        if (iw > 0) {
            Gui.drawRect(ix, 0, ix + iw, TOP_BAR_H, 0x20000000);
        }

        int x = tabsLeft - Math.round(tabScroll);
        for (int i = 0; i < TAB_KEYS.length; i++) {
            int w = tabWidths[i];
            boolean hovered = mouseX >= x && mouseX < x + w
                && mouseY >= 0 && mouseY < TOP_BAR_H
                && mouseX >= tabsLeft && mouseX < tabsRight;
            if (i != selectedTab && hovered) {
                Gui.drawRect(x, 0, x + w, TOP_BAR_H, 0x20000000);
            }
            String text = I18n.format(TAB_KEYS[i]);
            int color = i == selectedTab ? 0xFFFFFFFF : (hovered ? 0xFFD8D8D8 : COLOR_MUTED);
            fontRenderer.drawStringWithShadow(text, x + (w - fontRenderer.getStringWidth(text)) / 2,
                7, color);
            x += w;
        }

        if (iw > 0) {
            Gui.drawRect(ix, TOP_BAR_H - 2, ix + iw, TOP_BAR_H, COLOR_ON);
        }

        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        Gui.drawRect(0, TOP_BAR_H, width, TOP_BAR_H + 1, COLOR_BORDER);

        // 可横向滚动时在标签栏两端画箭头提示
        if (tabScroll > 0) {
            fontRenderer.drawStringWithShadow("<", tabsLeft - 8, 7, COLOR_MUTED);
        }
        if (tabScroll < maxTabScroll()) {
            fontRenderer.drawStringWithShadow(">", tabsRight + 1, 7, COLOR_MUTED);
        }
    }

    private void drawRows(int mouseX, int mouseY) {
        int y = listTop - Math.round(listScroll);

        int factor = new net.minecraft.client.gui.ScaledResolution(mc).getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(0, mc.displayHeight - listBottom * factor, width * factor,
            (listBottom - listTop) * factor);

        for (Row row : rows) {
            int h = row.height();
            if (y + h > listTop && y < listBottom) {
                boolean hovered = mouseX >= 0 && mouseX <= width
                    && mouseY >= Math.max(y, listTop) && mouseY <= Math.min(y + h, listBottom);
                row.draw(10, y, width - 20, hovered, mouseX, mouseY);
            }
            y += h;
        }

        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    private void drawScrollbar() {
        int contentHeight = contentHeight();
        int listH = listBottom - listTop;
        if (contentHeight <= listH) return;
        int trackX = width - 3;
        Gui.drawRect(trackX, listTop, width, listBottom, 0x33FFFFFF);
        int thumbH = Math.max(20, listH * listH / contentHeight);
        int thumbY = listTop + (listH - thumbH) * Math.round(listScroll) / Math.max(1, contentHeight - listH);
        Gui.drawRect(trackX, thumbY, width, thumbY + thumbH, 0xFFFFFFFF);
    }

    /** 与原版风格一致：深色底 + 1px 亮边框，悬停变亮。 */
    private void drawButton(Btn button, int mouseX, int mouseY) {
        boolean hovered = button.hovered(mouseX, mouseY);
        Gui.drawRect(button.x, button.y, button.x + button.w, button.y + button.h, hovered ? 0xA0202020 : 0x80000000);
        int border = hovered ? 0xFFFFFFFF : 0xFFA0A0A0;
        Gui.drawRect(button.x, button.y, button.x + button.w, button.y + 1, border);
        Gui.drawRect(button.x, button.y + button.h - 1, button.x + button.w, button.y + button.h, border);
        Gui.drawRect(button.x, button.y, button.x + 1, button.y + button.h, border);
        Gui.drawRect(button.x + button.w - 1, button.y, button.x + button.w, button.y + button.h, border);
        String text = button.text();
        String suffix = button.suffixText();
        float textY = button.y + (button.h - 8) / 2.0f;
        if (suffix != null) {
            int textW = fontRenderer.getStringWidth(text);
            int suffixW = fontRenderer.getStringWidth(suffix);
            float startX = button.x + (button.w - textW - suffixW) / 2.0f;
            fontRenderer.drawStringWithShadow(text, startX, textY, button.textColor());
            fontRenderer.drawStringWithShadow(suffix, startX + textW, textY,
                button.suffixTextColor());
        } else {
            fontRenderer.drawStringWithShadow(text,
                button.x + (button.w - fontRenderer.getStringWidth(text)) / 2.0f,
                textY, button.textColor());
        }
    }

    // ================= 交互 =================

    private int contentHeight() {
        int total = 0;
        for (Row row : rows) {
            total += row.height();
        }
        return total + 8;
    }

    private int maxScroll() {
        return Math.max(0, contentHeight() - (listBottom - listTop));
    }

    private int tabTotalWidth() {
        int total = 0;
        for (int w : tabWidths) {
            total += w;
        }
        return total;
    }

    /** 第 index 个标签在未滚动时的起始偏移。 */
    private int tabStart(int index) {
        int start = 0;
        for (int i = 0; i < index; i++) {
            start += tabWidths[i];
        }
        return start;
    }

    private int maxTabScroll() {
        return Math.max(0, tabTotalWidth() - (tabsRight - tabsLeft));
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        clickMouseY = mouseY;
        // 点击别处时取消正在监听的按键绑定
        if (listeningKeybind != null) {
            listeningKeybind = null;
            return;
        }

        // 折叠组内的文本框：先全部失焦，由命中的行重新聚焦
        for (NestedField nested : nestedFields) {
            nested.field.setFocused(false);
        }

        if (searchBox.mouseClicked(mouseX, mouseY, mouseButton)) {
            return;
        }

        // 拖动右侧滚动条
        if (mouseButton == 0 && mouseX >= width - 5 && mouseY >= listTop
            && mouseY <= listBottom && maxScroll() > 0) {
            draggingScrollbar = true;
            scrollToMouse(mouseY);
            return;
        }

        // 顶部标签
        if (mouseY >= 0 && mouseY < TOP_BAR_H && mouseX >= tabsLeft && mouseX < tabsRight) {
            int x = tabsLeft - Math.round(tabScroll);
            for (int i = 0; i < TAB_KEYS.length; i++) {
                if (mouseX >= x && mouseX < x + tabWidths[i]) {
                    if (selectedTab != i) {
                        selectedTab = i;
                        listScroll = 0f;
                        listScrollTarget = 0f;
                        buildRows();
                    }
                    // 保证选中的标签完整可见
                    int start = tabStart(i);
                    int view = tabsRight - tabsLeft;
                    if (start - tabScrollTarget < 0) {
                        tabScrollTarget = start;
                    } else if (start + tabWidths[i] - tabScrollTarget > view) {
                        tabScrollTarget = start + tabWidths[i] - view;
                    }
                    tabScrollTarget = Math.max(0f, Math.min(tabScrollTarget, maxTabScroll()));
                    return;
                }
                x += tabWidths[i];
            }
            return;
        }

        for (Btn button : buttons) {
            if (button.hovered(mouseX, mouseY)) {
                if (mouseButton == 0) {
                    button.action.run();
                }
                return;
            }
        }

        if (mouseY < listTop || mouseY > listBottom) return;

        int y = listTop - Math.round(listScroll);
        for (Row row : rows) {
            int h = row.height();
            if (mouseY >= y && mouseY < y + h) {
                row.click(mouseX, y, mouseButton);
                return;
            }
            y += h;
        }
    }

    private void scrollToMouse(int mouseY) {
        int listH = listBottom - listTop;
        int content = contentHeight();
        if (content <= listH) return;
        int thumbH = Math.max(20, listH * listH / content);
        float usable = Math.max(1.0f, listH - thumbH);
        float frac = (mouseY - listTop - thumbH / 2.0f) / usable;
        frac = Math.max(0.0f, Math.min(1.0f, frac));
        listScrollTarget = frac * maxScroll();
        listScroll = listScrollTarget;
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
        if (draggingScrollbar) {
            scrollToMouse(mouseY);
        } else if (draggingSlider != null) {
            float fraction = (mouseX - sliderDragX - 4.0f)
                / (float) Math.max(1, sliderDragW - 8);
            draggingSlider.setFromFraction(Math.max(0f, Math.min(1f, fraction)));
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
        draggingScrollbar = false;
        draggingSlider = null;
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;

        int mx = Mouse.getEventX() * width / mc.displayWidth;
        int my = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        if (my >= 0 && my < TOP_BAR_H && mx >= tabsLeft && mx < tabsRight) {
            tabScrollTarget -= Integer.signum(wheel) * 20;
            tabScrollTarget = Math.max(0f, Math.min(tabScrollTarget, maxTabScroll()));
        } else {
            listScrollTarget -= Integer.signum(wheel) * 40;
            listScrollTarget = Math.max(0f, Math.min(listScrollTarget, maxScroll()));
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        // 按键绑定监听：ESC / Delete / Backspace 都设为「未指定」，点击别处取消
        if (listeningKeybind != null) {
            int newCode = (keyCode == Keyboard.KEY_ESCAPE
                || keyCode == Keyboard.KEY_DELETE || keyCode == Keyboard.KEY_BACK)
                ? Keyboard.KEY_NONE : keyCode;
            listeningKeybind.binding.setKeyCode(newCode);
            KeyBinding.resetKeyBindingArrayAndHash();
            mc.gameSettings.saveOptions();
            listeningKeybind = null;
            return;
        }
        // 折叠组内的文本框优先接收输入
        for (NestedField nested : nestedFields) {
            if (nested.field.isFocused()
                && nested.field.textboxKeyTyped(typedChar, keyCode)) {
                nested.tick();
                return;
            }
        }
        // ESC 返回打开配置前的界面
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(parent);
            return;
        }
        if (searchBox.textboxKeyTyped(typedChar, keyCode)) {
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
