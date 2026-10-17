package damage.engine.client.gui;

import damage.engine.DamageEngineConfig;
import damage.engine.client.gui.config.OptionEntry;
import damage.engine.client.gui.config.OptionListWidget;
import damage.engine.client.gui.config.PlainTextButton;
import damage.engine.client.gui.config.StyledButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 独立的“加分”配置页面：管理内置与自定义加分项的名称、表达式与开关。
 */
public class BonusConfigScreen extends Screen {
    private static final int ROW_HEIGHT = 26;
    private static final int TOP_BAR_HEIGHT = 22;
    private static final int SEARCH_HEIGHT = 13;
    /** 行尾按钮宽度与按钮之间的间距。 */
    private static final int BUTTON_W = 38;
    private static final int BUTTON_GAP = 42;
    /** 名称输入框占用的宽度，其余空间留给表达式。 */
    private static final int NAME_BOX_W = 88;
    private static final int EXPR_X = 92;

    private final DamageConfigScreen parent;
    private final DamageEngineConfig config;
    private OptionListWidget optionList;
    private EditBox searchBox;
    private String searchQuery = "";
    private boolean helpExpanded = false;

    public BonusConfigScreen(DamageConfigScreen parent, DamageEngineConfig config) {
        super(Component.translatable("title.damage-engine.bonus"));
        this.parent = parent;
        this.config = config;
    }

    @Override
    protected void init() {
        this.clearWidgets();
        int topY = TOP_BAR_HEIGHT + 4;
        int footerY = this.height - 35;
        optionList = new OptionListWidget(this.minecraft, this.width, footerY - topY, topY, ROW_HEIGHT);
        this.addWidget(optionList);

        // 标题在左，搜索框紧跟标题之后；高度与设置界面保持一致
        int searchWidth = Math.min(110, Math.max(80, this.width / 8));
        int searchX = 10 + this.font.width(this.title) + 10;
        searchBox = new EditBox(this.font, searchX, 8, searchWidth, SEARCH_HEIGHT,
            Component.translatable("text.damage-engine.search"));
        searchBox.setBordered(false);
        searchBox.setMaxLength(50);
        searchBox.setHint(Component.translatable("text.damage-engine.search").withColor(0xFF808080));
        searchBox.setValue(searchQuery);
        searchBox.setResponder(text -> {
            searchQuery = text == null ? "" : text.trim();
            buildEntries();
        });
        this.addRenderableWidget(searchBox);

        buildEntries();

        this.addRenderableWidget(new StyledButton(this.width - 90, footerY + 5, 80, 20,
            Component.translatable("gui.done").withColor(0xFFB7F3C8), this::close));
    }

    private void buildEntries() {
        optionList.clearEntriesPublic();
        optionList.addEntryPublic(new BonusHelpEntry(helpExpanded, () -> {
            helpExpanded = !helpExpanded;
            buildEntries();
        }));
        if (helpExpanded) {
            for (String line : Component.translatable("hint.damage-engine.bonus_syntax")
                .getString().split("\n")) {
                optionList.addEntryPublic(new BonusInfoEntry(Component.literal(line)));
            }
        }
        String query = searchQuery.toLowerCase(Locale.ROOT);
        for (DamageEngineConfig.BonusRule rule : new ArrayList<>(config.bonusRules)) {
            String searchText = (displayName(rule) + " " + rule.expression).toLowerCase(Locale.ROOT);
            if (!query.isEmpty() && !searchText.contains(query)) continue;
            optionList.addEntryPublic(new BonusRuleEntry(rule, () -> resetRule(rule), () -> {
                config.bonusRules.remove(rule);
                markChanged();
                buildEntries();
            }));
        }
        optionList.addEntryPublic(new AddBonusEntry(() -> {
            DamageEngineConfig.BonusRule rule = new DamageEngineConfig.BonusRule();
            rule.id = "custom_" + System.nanoTime();
            rule.name = Component.translatable("text.damage-engine.custom_bonus").getString();
            rule.expression = "0";
            rule.builtin = false;
            rule.enabled = true;
            config.bonusRules.add(rule);
            markChanged();
            buildEntries();
        }));
    }

    private static String displayName(DamageEngineConfig.BonusRule rule) {
        if (rule.name != null && !rule.name.isBlank()) return rule.name;
        return Component.translatable("rating.bonus." + rule.id).getString();
    }

    private void resetRule(DamageEngineConfig.BonusRule rule) {
        DamageEngineConfig.BonusRule preset = null;
        for (DamageEngineConfig.BonusRule candidate : DamageEngineConfig.defaultBonusRules()) {
            if (candidate.id.equals(rule.id)) {
                preset = candidate;
                break;
            }
        }
        if (preset != null) {
            rule.name = preset.name;
            rule.expression = preset.expression;
            rule.enabled = preset.enabled;
            rule.continuous = preset.continuous;
        } else {
            rule.name = Component.translatable("text.damage-engine.custom_bonus").getString();
            rule.expression = "0";
            rule.enabled = true;
        }
        markChanged();
    }

    private void markChanged() {
        if (parent != null) parent.onBonusConfigChanged();
    }

    private void close() {
        if (parent != null) {
            parent.onBonusConfigChanged();
            this.minecraft.setScreen(parent);
        } else {
            this.minecraft.setScreen(null);
        }
    }

    @Override
    public void onClose() {
        close();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean overSearch = searchBox != null && searchBox.isMouseOver(mouseX, mouseY);
        if (!overSearch && searchBox != null) {
            searchBox.setFocused(false);
        }
        if (overSearch && optionList != null) {
            // 只清掉当前真正处于聚焦状态的输入框，避免所有边框同时闪一下
            for (GuiEventListener child : optionList.children()) {
                if (child instanceof OptionEntry entry) {
                    for (GuiEventListener inner : entry.children()) {
                        if (inner instanceof EditBox box && box.isFocused()) box.setFocused(false);
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        renderBackground(guiGraphics, mouseX, mouseY, delta);
        // 标题左上角，搜索框紧跟其后；布局与设置界面一致
        guiGraphics.drawString(this.font, this.title, 10, 7, 0xFFFFFFFF, false);
        guiGraphics.fill(0, TOP_BAR_HEIGHT, this.width, TOP_BAR_HEIGHT + 1, 0xFF555555);

        if (optionList != null) {
            optionList.render(guiGraphics, mouseX, mouseY, delta);
            optionList.updateSmoothScroll(delta);
            net.minecraft.network.chat.Component tip = optionList.getTooltipAt(mouseX, mouseY);
            if (tip != null) {
                guiGraphics.pose().pushPose();
                guiGraphics.pose().translate(0f, 0f, 400f);
                damage.engine.client.gui.config.ConfigTooltips.draw(
                    guiGraphics, this.font, tip, mouseX, mouseY, this.width, this.height);
                guiGraphics.pose().popPose();
            }
        }

        int footerY = this.height - 35;
        guiGraphics.fill(0, footerY - 1, this.width, footerY, 0xFF555555);
        for (GuiEventListener child : this.children()) {
            if (child != optionList && child instanceof net.minecraft.client.gui.components.AbstractWidget widget) {
                widget.render(guiGraphics, mouseX, mouseY, delta);
            }
        }
    }

    /** 列表顶部说明项：可展开收起，悬停仍显示完整用法。 */
    private static class BonusHelpEntry extends OptionEntry {
        private final Component label = Component.translatable("text.damage-engine.bonus_help");
        private final PlainTextButton toggle;
        private final Runnable onToggle;

        BonusHelpEntry(boolean expanded, Runnable onToggle) {
            this.onToggle = onToggle;
            this.toggle = new PlainTextButton(0, 0, 20, 20,
                Component.literal(expanded ? "-" : "+"), onToggle, 0xFFFFFFFF, 0xFFFBFB54);
            setSearchText(label);
            setHoverTooltip(Component.translatable("hint.damage-engine.bonus_syntax"));
        }

        @Override
        public boolean mouseClicked(double mx, double my, int btn) {
            if (super.mouseClicked(mx, my, btn)) return true;
            if (btn == 0 && isMouseOverRow(mx, my)) {
                Minecraft.getInstance().getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.0F));
                onToggle.run();
                return true;
            }
            return false;
        }

        @Override
        public void renderContent(GuiGraphics g, int idx, int y, int x, int ew, int eh,
                                  int mx, int my, boolean hovered, float dt) {
            g.drawString(Minecraft.getInstance().font, label, x, y + 8, 0xFFFFFFFF);
            toggle.setX(x + ew - 25);
            toggle.setY(y + 2);
            toggle.setForceHover(hovered);
            toggle.render(g, mx, my, dt);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return Collections.singletonList(toggle);
        }
    }

    /** 展开后的说明行。 */
    private static class BonusInfoEntry extends OptionEntry {
        private final Component text;

        BonusInfoEntry(Component text) {
            this.text = text;
        }

        @Override
        protected boolean shouldHighlight() {
            return false;
        }

        @Override
        public void renderContent(GuiGraphics g, int idx, int y, int x, int ew, int eh,
                                  int mx, int my, boolean hovered, float dt) {
            g.drawString(Minecraft.getInstance().font, text, x + 10, y + 8, 0xFFA0A0A0);
        }
    }

    /** 添加按钮：与重置按钮右对齐、同宽。 */
    private static class AddBonusEntry extends OptionEntry {
        private final StyledButton button;
        private final Component label = Component.translatable("text.damage-engine.add_bonus");

        AddBonusEntry(Runnable onAdd) {
            this.button = new StyledButton(0, 0, BUTTON_W, 20, Component.literal("+"), onAdd);
            setSearchText(label);
        }

        @Override
        public void renderContent(GuiGraphics g, int idx, int y, int x, int ew, int eh,
                                  int mx, int my, boolean hovered, float dt) {
            g.drawString(Minecraft.getInstance().font, label, x, y + 8, 0xFFFFFFFF);
            button.setX(x + ew - (BUTTON_W + 2));
            button.setY(y + 2);
            button.setFocused(false);
            button.render(g, mx, my, dt);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return Collections.singletonList(button);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return Collections.emptyList();
        }
    }

    /** 一行：名称 + 表达式 + 开关 / 重置（自定义项还有删除）。 */
    private static class BonusRuleEntry extends OptionEntry {
        private final DamageEngineConfig.BonusRule rule;
        private final EditBox nameField;
        private final EditBox expressionField;
        private final StyledButton enabledButton;
        private final StyledButton resetButton;
        private final StyledButton deleteButton;
        private int resetState = 0;
        private long resetActionTime = 0;

        BonusRuleEntry(DamageEngineConfig.BonusRule rule, Runnable onReset, Runnable onDelete) {
            this.rule = rule;
            this.nameField = createField(displayName(rule), 64, value -> rule.name = value);
            this.nameField.setHint(Component.translatable("text.damage-engine.bonus_name").withColor(0xFF808080));
            this.expressionField = createField(rule.expression == null ? "" : rule.expression, 200,
                value -> rule.expression = value);
            this.expressionField.setHint(Component.translatable("text.damage-engine.bonus_expression").withColor(0xFF808080));

            final StyledButton[] ref = new StyledButton[1];
            ref[0] = new StyledButton(0, 0, BUTTON_W, 20, enabledLabel(rule.enabled), () -> {
                rule.enabled = !rule.enabled;
                ref[0].setMessage(enabledLabel(rule.enabled));
            });
            this.enabledButton = ref[0];

            // 重置：红字，第一次点击确认，第二次执行并显示“已重置”
            final StyledButton[] resetRef = new StyledButton[1];
            resetRef[0] = new StyledButton(0, 0, BUTTON_W, 20, resetLabel(), () -> {
                if (resetState == 0) {
                    resetState = 1;
                    resetActionTime = System.currentTimeMillis();
                    resetRef[0].setMessage(confirmLabel());
                } else if (resetState == 1) {
                    onReset.run();
                    syncFromRule();
                    resetState = 2;
                    resetActionTime = System.currentTimeMillis();
                    resetRef[0].setMessage(doneLabel());
                }
            });
            this.resetButton = resetRef[0];
            this.deleteButton = rule.builtin ? null
                : new StyledButton(0, 0, BUTTON_W, 20,
                    Component.translatable("button.damage-engine.delete").withColor(0xFFFC887E), onDelete);
        }

        private static EditBox createField(String value, int maxLength, java.util.function.Consumer<String> responder) {
            // 先用足够宽的宽度构造，让 setValue 不会把文本滚动到末尾；
            // 实际宽度每帧由 renderContent 计算，否则会残留 displayPos 导致文字看起来被滚走。
            EditBox field = new EditBox(Minecraft.getInstance().font, -1000, -1000, 1024, 18, Component.empty());
            field.setBordered(false);
            field.setMaxLength(maxLength);
            field.setValue(value);
            field.setResponder(responder);
            return field;
        }
        private static Component enabledLabel(boolean enabled) {
            return Component.translatable(enabled ? "options.on" : "options.off")
                .withColor(enabled ? 0xFFB5F0C6 : 0xFFFC887E);
        }

        private static Component resetLabel() {
            return Component.translatable("gui.reset").withColor(0xFFFC887E);
        }

        private static Component confirmLabel() {
            return Component.translatable("gui.confirm").append("?").withColor(0xFFFC887E);
        }

        private static Component doneLabel() {
            return Component.translatable("text.damage-engine.reset_done").withColor(0xFFB5F0C6);
        }

        private void syncFromRule() {
            String resetName = rule.name;
            nameField.setValue(displayName(rule));
            rule.name = resetName;
            expressionField.setValue(rule.expression == null ? "" : rule.expression);
            enabledButton.setMessage(enabledLabel(rule.enabled));
        }

        @Override
        public void renderContent(GuiGraphics g, int idx, int y, int x, int ew, int eh,
                                  int mx, int my, boolean hovered, float dt) {
            long now = System.currentTimeMillis();
            if (resetState == 2 && now - resetActionTime > 3000) {
                resetState = 0;
                resetButton.setMessage(resetLabel());
            } else if (resetState == 1 && now - resetActionTime > 5000) {
                resetState = 0;
                resetButton.setMessage(resetLabel());
            }
            boolean custom = deleteButton != null;
            int rightEdge = x + ew - (BUTTON_W + 2);
            int middleX = rightEdge - BUTTON_GAP;
            int leftX = rightEdge - BUTTON_GAP * 2;
            int leftmost = custom ? leftX : middleX;
            int exprWidth = Math.max(40, leftmost - 6 - (x + EXPR_X));

            drawFieldBox(g, nameField, x, y + 2, NAME_BOX_W, 20, mx, my);
            nameField.setX(x + 4);
            nameField.setY(y + 7);
            nameField.setWidth(NAME_BOX_W - 8);
            nameField.setHeight(12);
            nameField.render(g, mx, my, dt);

            int exprX = x + EXPR_X;
            drawFieldBox(g, expressionField, exprX, y + 2, exprWidth, 20, mx, my);
            expressionField.setX(exprX + 4);
            expressionField.setY(y + 7);
            expressionField.setWidth(exprWidth - 8);
            expressionField.setHeight(12);
            expressionField.render(g, mx, my, dt);

            enabledButton.setX(custom ? leftX : middleX);
            enabledButton.setY(y + 2);
            enabledButton.setFocused(false);
            enabledButton.render(g, mx, my, dt);

            resetButton.setX(custom ? middleX : rightEdge);
            resetButton.setY(y + 2);
            resetButton.setFocused(false);
            resetButton.render(g, mx, my, dt);

            if (deleteButton != null) {
                deleteButton.setX(rightEdge);
                deleteButton.setY(y + 2);
                deleteButton.setFocused(false);
                deleteButton.render(g, mx, my, dt);
            }
        }

        private static void drawFieldBox(GuiGraphics g, EditBox field, int x, int y, int w, int h,
                                         int mx, int my) {
            g.fill(x, y, x + w, y + h, 0x20000000);
            int color = field.isFocused() ? 0xFFFFFFFF : 0xFFA0A0A0;
            g.fill(x, y, x + w, y + 1, color);
            g.fill(x, y + h - 1, x + w, y + h, color);
            g.fill(x, y, x + 1, y + h, color);
            g.fill(x + w - 1, y, x + w, y + h, color);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            List<GuiEventListener> children = new ArrayList<>();
            children.add(nameField);
            children.add(expressionField);
            children.add(enabledButton);
            children.add(resetButton);
            if (deleteButton != null) children.add(deleteButton);
            return children;
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return Collections.emptyList();
        }
    }
}
