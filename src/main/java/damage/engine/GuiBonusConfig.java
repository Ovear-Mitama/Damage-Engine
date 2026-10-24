package damage.engine;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 加分设置：与 1.21.1 相同的行布局（名称输入框 + 表达式输入框 + 开/关、重置、删除）。
 */
public class GuiBonusConfig extends GuiScreen {
    private static final int ROW_H = 26;
    private static final int CONTROL_H = 20;
    private static final int BUTTON_W = 38;
    private static final int BUTTON_GAP = 42;
    private static final int NAME_BOX_W = 88;
    private static final int EXPR_X = 92;
    private static final int HELP_H = 24;

    private final GuiScreen parent;
    private final DamageEngineConfig config = DamageEngineConfig.getInstance();
    private final List<DamageEngineConfig.BonusRule> rules = new ArrayList<>();
    private final List<GuiTextField> nameFields = new ArrayList<>();
    private final List<GuiTextField> exprFields = new ArrayList<>();

    private int listTop = 32;
    private int listBottom;
    private float scrollPx;
    private float scrollTarget;
    private boolean helpExpanded;

    public GuiBonusConfig(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        listBottom = height - 35;
        rules.clear();
        rules.addAll(config.bonusRules);
        nameFields.clear();
        exprFields.clear();
        for (DamageEngineConfig.BonusRule rule : rules) {
            nameFields.add(makeField(displayName(rule), 64));
            exprFields.add(makeField(rule.expression == null ? "" : rule.expression, 200));
        }
        clampScroll();
    }

    private GuiTextField makeField(String value, int maxLength) {
        GuiTextField field = new GuiTextField(0, fontRenderer, -1000, -1000, 1024, 14);
        field.setEnableBackgroundDrawing(false);
        field.setTextColor(0xFFFFFFFF);
        field.setDisabledTextColour(0xFF808080);
        field.setMaxStringLength(maxLength);
        field.setText(value);
        return field;
    }

    /** 1.12.2 的 .lang 里 \n 可能保留为字面量，两种写法都按换行处理。 */
    private String[] syntaxLines() {
        return I18n.format("hint.damage-engine.bonus_syntax").split("\\\\n|\\n");
    }

    /** 说明行（含展开后的正文）占用的高度。 */
    private int helpHeight() {
        int extra = helpExpanded ? syntaxLines().length * 11 + 6 : 0;
        return HELP_H + extra;
    }

    private void clampScroll() {
        scrollPx = Math.max(0f, Math.min(scrollPx, maxScroll()));
        scrollTarget = Math.max(0f, Math.min(scrollTarget, maxScroll()));
    }

    private int contentHeight() {
        return helpHeight() + rules.size() * ROW_H + 8;
    }

    private int maxScroll() {
        return Math.max(0, contentHeight() - (listBottom - listTop));
    }

    private int rightEdge() {
        return width - 12 - BUTTON_W;
    }

    private int exprWidth() {
        boolean custom = false;
        int leftmost = rightEdge() - BUTTON_GAP;
        for (int i = 0; i < rules.size(); i++) {
            if (!rules.get(i).builtin) {
                custom = true;
                break;
            }
        }
        if (custom) leftmost -= BUTTON_GAP;
        // 减去表达式输入框的真实起点（EXPR_X + 10），否则右边会顶到按钮上
        return Math.max(60, leftmost - 6 - (EXPR_X + 10));
    }

    private int buttonY(int rowY) {
        return rowY + (ROW_H - CONTROL_H) / 2;
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        for (GuiTextField field : nameFields) field.updateCursorCounter();
        for (GuiTextField field : exprFields) field.updateCursorCounter();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        drawDefaultBackground();
        Gui.drawRect(0, 0, width, height, 0x50000000);

        drawCenteredString(fontRenderer, I18n.format("title.damage-engine.bonus"),
            width / 2, 10, 0xFFFFFFFF);
        Gui.drawRect(0, 24, width, 25, 0xFF555555);

        long now = System.currentTimeMillis();
        float dt = lastFrame == 0 ? 0.016f : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        scrollPx += (scrollTarget - scrollPx) * (1.0f - (float) Math.exp(-16.0f * dt));
        if (Math.abs(scrollTarget - scrollPx) < 0.3f) scrollPx = scrollTarget;

        int factor = new net.minecraft.client.gui.ScaledResolution(mc).getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(0, mc.displayHeight - listBottom * factor,
            width * factor, (listBottom - listTop) * factor);

        // 说明行也跟随列表滚动（与 1.21.1 一致，不再固定在顶部）
        int contentY = listTop - Math.round(scrollPx);
        drawHelpRow(contentY, mouseX, mouseY);

        int y = contentY + helpHeight();
        for (int i = 0; i < rules.size(); i++) {
            if (y + ROW_H > listTop && y < listBottom) {
                drawRuleRow(i, y, mouseX, mouseY);
            } else {
                // 不可见的行把输入框移出屏幕，避免遗留的旧坐标参与命中检测
                parkField(nameFields.get(i));
                parkField(exprFields.get(i));
            }
            y += ROW_H;
        }
        GL11.glDisable(GL11.GL_SCISSOR_TEST);

        drawScrollbar();
        drawFooter(mouseX, mouseY);
    }

    private void drawHelpRow(int y, int mouseX, int mouseY) {
        boolean hovered = mouseY >= y && mouseY < y + HELP_H
            && mouseY >= listTop && mouseY < listBottom;
        if (hovered) {
            Gui.drawRect(0, y, width, y + HELP_H - 1, 0x14FFFFFF);
        }
        // 整行可点，右侧只画 +/- 文字，不使用按钮样式
        fontRenderer.drawStringWithShadow(I18n.format("text.damage-engine.bonus_help"),
            10, y + 8, hovered ? 0xFFFBFB54 : 0xFFFFFFFF);
        String mark = helpExpanded ? "-" : "+";
        fontRenderer.drawStringWithShadow(mark,
            width - 12 - fontRenderer.getStringWidth(mark), y + 8,
            hovered ? 0xFFFBFB54 : 0xFFA0A0A0);
        Gui.drawRect(10, y + HELP_H - 1, width - 10, y + HELP_H, 0x33555555);

        if (helpExpanded) {
            String[] lines = syntaxLines();
            int lineY = y + HELP_H + 2;
            for (String line : lines) {
                fontRenderer.drawStringWithShadow(line, 18, lineY, 0xFFA0A0A0);
                lineY += 11;
            }
        }
    }

    private long lastFrame;

    private void parkField(GuiTextField field) {
        field.x = -1000;
        field.y = -1000;
        field.setFocused(false);
    }

    /** 鼠标是否落在输入框矩形内（用绘制时更新的坐标）。 */
    private boolean isOverField(GuiTextField field, int mouseX, int mouseY) {
        return field.getVisible() && mouseX >= field.x && mouseX < field.x + field.width
            && mouseY >= field.y && mouseY < field.y + field.height;
    }

    private void drawRuleRow(int index, int y, int mouseX, int mouseY) {
        DamageEngineConfig.BonusRule rule = rules.get(index);
        boolean hovered = mouseX >= 0 && mouseX <= width && mouseY >= y && mouseY < y + ROW_H;
        if (hovered) {
            Gui.drawRect(0, y, width, y + ROW_H - 1, 0x14FFFFFF);
        }

        int cy = buttonY(y);

        // 名称输入框
        GuiTextField nameField = nameFields.get(index);
        drawFieldBox(10, cy, NAME_BOX_W, CONTROL_H, nameField.isFocused());
        nameField.x = 14;
        nameField.y = cy + 6;
        nameField.width = NAME_BOX_W - 8;
        nameField.drawTextBox();

        // 表达式输入框
        int ex = EXPR_X + 10;
        int ew = exprWidth();
        GuiTextField exprField = exprFields.get(index);
        drawFieldBox(ex, cy, ew, CONTROL_H, exprField.isFocused());
        exprField.x = ex + 4;
        exprField.y = cy + 6;
        exprField.width = ew - 8;
        exprField.drawTextBox();

        // 右侧按钮
        boolean custom = !rule.builtin;
        int right = rightEdge();
        int middleX = right - BUTTON_GAP;
        int leftX = right - BUTTON_GAP * 2;
        int enabledX = custom ? leftX : middleX;
        int resetX = custom ? middleX : right;

        drawButton(enabledX, cy, BUTTON_W, CONTROL_H,
            I18n.format(rule.enabled ? "options.on" : "options.off"),
            rule.enabled ? 0xFFB5F0C6 : 0xFFFC887E, mouseX, mouseY);
        drawButton(resetX, cy, BUTTON_W, CONTROL_H,
            I18n.format("gui.reset"), 0xFFFC887E, mouseX, mouseY);
        if (custom) {
            drawButton(right, cy, BUTTON_W, CONTROL_H,
                I18n.format("button.damage-engine.delete"), 0xFFFC887E, mouseX, mouseY);
        }
    }

    private void drawFieldBox(int x, int y, int w, int h, boolean focused) {
        Gui.drawRect(x, y, x + w, y + h, 0x30000000);
        int border = focused ? 0xFFFFFFFF : 0xFFA0A0A0;
        Gui.drawRect(x, y, x + w, y + 1, border);
        Gui.drawRect(x, y + h - 1, x + w, y + h, border);
        Gui.drawRect(x, y, x + 1, y + h, border);
        Gui.drawRect(x + w - 1, y, x + w, y + h, border);
    }

    private void drawFooter(int mouseX, int mouseY) {
        int footerY = height - 35;
        Gui.drawRect(0, footerY, width, footerY + 1, 0xFF555555);

        drawButton(rightEdge(), footerY + 7, BUTTON_W, CONTROL_H, "+",
            0xFFFFFFFF, mouseX, mouseY);
        drawButton(width - 100, footerY + 7, 90, CONTROL_H,
            I18n.format("gui.done"), 0xFFB7F3C8, mouseX, mouseY);
    }

    private void drawScrollbar() {
        int listH = listBottom - listTop;
        if (contentHeight() <= listH) return;
        int trackX = width - 3;
        Gui.drawRect(trackX, listTop, width, listBottom, 0x33FFFFFF);
        int thumbH = Math.max(20, listH * listH / contentHeight());
        int thumbY = listTop + (listH - thumbH) * Math.round(scrollPx)
            / Math.max(1, contentHeight() - listH);
        Gui.drawRect(trackX, thumbY, width, thumbY + thumbH, 0xFFFFFFFF);
    }

    private void drawButton(int x, int y, int w, int h, String text, int color,
                            int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        Gui.drawRect(x, y, x + w, y + h, hovered ? 0xA0202020 : 0x80000000);
        int border = hovered ? 0xFFFFFFFF : 0xFFA0A0A0;
        Gui.drawRect(x, y, x + w, y + 1, border);
        Gui.drawRect(x, y + h - 1, x + w, y + h, border);
        Gui.drawRect(x, y, x + 1, y + h, border);
        Gui.drawRect(x + w - 1, y, x + w, y + h, border);
        fontRenderer.drawStringWithShadow(text,
            x + (w - fontRenderer.getStringWidth(text)) / 2.0f,
            y + (h - 8) / 2.0f, color);
    }

    private String displayName(DamageEngineConfig.BonusRule rule) {
        if (rule.name != null && !rule.name.trim().isEmpty()) return rule.name;
        return I18n.format("rating.bonus." + rule.id);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton != 0) return;

        // 任何点击先让所有输入框失焦，下面只重新聚焦真正命中的那一个
        for (GuiTextField field : nameFields) field.setFocused(false);
        for (GuiTextField field : exprFields) field.setFocused(false);

        int contentY = listTop - Math.round(scrollPx);

        // 说明行（跟随列表滚动，整行可点）
        if (mouseY >= listTop && mouseY < listBottom
            && mouseY >= contentY && mouseY < contentY + HELP_H) {
            helpExpanded = !helpExpanded;
            clampScroll();
            return;
        }

        // 底部按钮
        int footerY = height - 35;
        if (mouseX >= rightEdge() && mouseX <= rightEdge() + BUTTON_W
            && mouseY >= footerY && mouseY <= footerY + 27) {
            addRule();
            return;
        }
        if (mouseX >= width - 100 && mouseX <= width - 10
            && mouseY >= footerY && mouseY <= footerY + 27) {
            applyChanges();
            mc.displayGuiScreen(parent);
            return;
        }

        int rulesTop = contentY + helpHeight();
        if (mouseY < rulesTop || mouseY > listBottom) return;
        int index = (mouseY - rulesTop) / ROW_H;
        if (index < 0 || index >= rules.size()) return;
        int cy = buttonY(rulesTop + index * ROW_H);

        DamageEngineConfig.BonusRule rule = rules.get(index);
        boolean custom = !rule.builtin;
        int right = rightEdge();
        int middleX = right - BUTTON_GAP;
        int leftX = right - BUTTON_GAP * 2;
        int enabledX = custom ? leftX : middleX;
        int resetX = custom ? middleX : right;

        if (mouseX >= enabledX && mouseX <= enabledX + BUTTON_W
            && mouseY >= cy && mouseY <= cy + CONTROL_H) {
            rule.enabled = !rule.enabled;
            return;
        }
        if (mouseX >= resetX && mouseX <= resetX + BUTTON_W
            && mouseY >= cy && mouseY <= cy + CONTROL_H) {
            resetRule(index);
            return;
        }
        if (custom && mouseX >= right && mouseX <= right + BUTTON_W
            && mouseY >= cy && mouseY <= cy + CONTROL_H) {
            config.bonusRules.remove(rule);
            initGui();
            return;
        }

        GuiTextField nameField = nameFields.get(index);
        GuiTextField exprField = exprFields.get(index);
        if (isOverField(nameField, mouseX, mouseY)) {
            nameField.mouseClicked(mouseX, mouseY, mouseButton);
        } else if (isOverField(exprField, mouseX, mouseY)) {
            exprField.mouseClicked(mouseX, mouseY, mouseButton);
        }
    }

    private void addRule() {
        DamageEngineConfig.BonusRule rule = new DamageEngineConfig.BonusRule();
        rule.id = "custom_" + System.nanoTime();
        rule.name = I18n.format("text.damage-engine.custom_bonus");
        rule.expression = "0";
        rule.builtin = false;
        rule.enabled = true;
        config.bonusRules.add(rule);
        initGui();
    }

    private void resetRule(int index) {
        DamageEngineConfig.BonusRule rule = rules.get(index);
        for (DamageEngineConfig.BonusRule preset : DamageEngineConfig.defaultBonusRules()) {
            if (preset.id.equals(rule.id)) {
                rule.expression = preset.expression;
                rule.name = preset.name;
                exprFields.get(index).setText(preset.expression == null ? "" : preset.expression);
                nameFields.get(index).setText(displayName(preset));
                return;
            }
        }
        rule.expression = "0";
        exprFields.get(index).setText("0");
    }

    private void applyChanges() {
        for (int i = 0; i < rules.size(); i++) {
            DamageEngineConfig.BonusRule rule = rules.get(i);
            rule.name = nameFields.get(i).getText().trim();
            rule.expression = exprFields.get(i).getText().trim();
        }
        config.save();
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            scrollTarget -= Integer.signum(wheel) * 26;
            clampScroll();
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            applyChanges();
            mc.displayGuiScreen(parent);
            return;
        }
        if (keyCode == Keyboard.KEY_RETURN) {
            applyChanges();
            return;
        }
        for (GuiTextField field : nameFields) {
            if (field.textboxKeyTyped(typedChar, keyCode)) return;
        }
        for (GuiTextField field : exprFields) {
            if (field.textboxKeyTyped(typedChar, keyCode)) return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
