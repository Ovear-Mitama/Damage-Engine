package damage.engine;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.function.IntConsumer;

/**
 * 1.12.2 版颜色选择器：HSV 方块 + 色相条 + Hex/R/G/B 输入框 + 颜色预览。
 */
public class GuiColorPicker extends GuiScreen {
    private static final int FIELD_W = 56;
    private static final int FIELD_H = 10;
    private static final int FIELD_LABEL_W = 22;
    private static final int BTN_W = 72;
    private static final int BTN_H = 20;

    private final GuiScreen parent;
    private final IntConsumer onChange;

    private int color;
    private float hue;
    private float sat;
    private float val;

    private int panelX, panelY, panelW, panelH;
    private int squareX, squareY, squareSize;
    private int barX, barY, barW, barH;
    private int doneBtnX, cancelBtnX, btnY;

    private GuiTextField hexField, rField, gField, bField;
    private boolean updatingFields = false;
    private boolean draggingSquare = false;
    private boolean draggingBar = false;

    public GuiColorPicker(GuiScreen parent, int initialColor, IntConsumer onChange) {
        this.parent = parent;
        this.onChange = onChange;
        setColor(initialColor | 0xFF000000);
    }

    private void setColor(int argb) {
        this.color = argb | 0xFF000000;
        float[] hsv = rgbToHsv((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF);
        this.hue = hsv[0];
        this.sat = hsv[1];
        this.val = hsv[2];
    }

    @Override
    public void initGui() {
        squareSize = 100;
        barW = 12;
        int pad = 10;
        int fieldColW = FIELD_LABEL_W + 4 + FIELD_W;
        panelW = pad + squareSize + 4 + barW + 8 + fieldColW + pad;
        panelH = pad + squareSize + 8 + BTN_H + pad + 26;

        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        squareX = panelX + pad;
        squareY = panelY + pad;
        barX = squareX + squareSize + 4;
        barY = squareY;
        barH = squareSize;

        int fieldsX = barX + barW + 8;
        int fieldsY = squareY;
        int rowH = FIELD_H + 8;

        hexField = makeField(fieldsX + FIELD_LABEL_W, fieldsY, 6);
        rField = makeField(fieldsX + FIELD_LABEL_W, fieldsY + rowH, 3);
        gField = makeField(fieldsX + FIELD_LABEL_W, fieldsY + rowH * 2, 3);
        bField = makeField(fieldsX + FIELD_LABEL_W, fieldsY + rowH * 3, 3);

        btnY = panelY + panelH - BTN_H - pad;
        doneBtnX = panelX + panelW - pad - BTN_W;
        cancelBtnX = doneBtnX - BTN_W - 6;

        syncFields();
    }

    private GuiTextField makeField(int x, int y, int maxLength) {
        GuiTextField field = new GuiTextField(0, fontRenderer, x + 3, y + 1, FIELD_W - 6, FIELD_H);
        field.setEnableBackgroundDrawing(false);
        field.setTextColor(0xFFFFFFFF);
        field.setDisabledTextColour(0xFF808080);
        field.setMaxStringLength(maxLength);
        field.setCanLoseFocus(true);
        return field;
    }

    private void syncFields() {
        updatingFields = true;
        hexField.setText(String.format(java.util.Locale.ROOT, "%06X", color & 0xFFFFFF));
        rField.setText(String.valueOf((color >> 16) & 0xFF));
        gField.setText(String.valueOf((color >> 8) & 0xFF));
        bField.setText(String.valueOf(color & 0xFF));
        updatingFields = false;
    }

    private void syncFromPicker() {
        color = 0xFF000000 | hsvToRgb(hue, sat, val);
        syncFields();
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        hexField.updateCursorCounter();
        rField.updateCursorCounter();
        gField.updateCursorCounter();
        bField.updateCursorCounter();
        if (updatingFields) return;

        String hex = hexField.getText().replaceAll("[^0-9a-fA-F]", "");
        if (!hex.equals(hexField.getText())) {
            hexField.setText(hex);
        }
        String rgb = rField.getText() + gField.getText() + bField.getText();
        boolean rgbChanged =
            !rField.getText().equals(String.valueOf((color >> 16) & 0xFF))
                || !gField.getText().equals(String.valueOf((color >> 8) & 0xFF))
                || !bField.getText().equals(String.valueOf(color & 0xFF));

        if (hexField.isFocused() && !hex.isEmpty() && !hex.equals(
            String.format(java.util.Locale.ROOT, "%06X", color & 0xFFFFFF))) {
            try {
                int value = (int) Long.parseLong(hex, 16);
                setColor(0xFF000000 | value);
                syncFields();
            } catch (NumberFormatException ignored) {
            }
        } else if (rgbChanged && !rField.getText().isEmpty()
            && !gField.getText().isEmpty() && !bField.getText().isEmpty()) {
            int r = clampInt(parseInt(rField.getText(), (color >> 16) & 0xFF), 0, 255);
            int g = clampInt(parseInt(gField.getText(), (color >> 8) & 0xFF), 0, 255);
            int b = clampInt(parseInt(bField.getText(), color & 0xFF), 0, 255);
            setColor(0xFF000000 | (r << 16) | (g << 8) | b);
            syncFields();
        }
    }

    private static int parseInt(String text, int fallback) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int clampInt(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }

    private void updateFromSquare(int mouseX, int mouseY) {
        sat = clamp01((mouseX - squareX) / (float) squareSize);
        val = clamp01(1.0f - (mouseY - squareY) / (float) squareSize);
        syncFromPicker();
    }

    private void updateFromBar(int mouseY) {
        hue = clamp01((mouseY - barY) / (float) barH);
        syncFromPicker();
    }

    private static float clamp01(float value) {
        return value < 0f ? 0f : (value > 1f ? 1f : value);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        drawDefaultBackground();
        Gui.drawRect(0, 0, width, height, 0x50000000);

        // 面板
        Gui.drawRect(panelX, panelY, panelX + panelW, panelY + panelH, 0xE0202020);
        Gui.drawRect(panelX, panelY, panelX + panelW, panelY + 1, 0xFFA0A0A0);
        Gui.drawRect(panelX, panelY + panelH - 1, panelX + panelW, panelY + panelH, 0xFFA0A0A0);
        Gui.drawRect(panelX, panelY, panelX + 1, panelY + panelH, 0xFFA0A0A0);
        Gui.drawRect(panelX + panelW - 1, panelY, panelX + panelW, panelY + panelH, 0xFFA0A0A0);

        // 色相条
        for (int i = 0; i < barH; i++) {
            int c = 0xFF000000 | hsvToRgb(i / (float) barH, 1f, 1f);
            Gui.drawRect(barX, barY + i, barX + barW, barY + i + 1, c);
        }
        int indicatorY = barY + (int) (hue * barH);
        Gui.drawRect(barX - 2, indicatorY - 2, barX + barW + 2, indicatorY + 3, 0xFFFFFFFF);

        // 饱和度/亮度方块：白→色相 横向渐变 + 自上而下的黑色透明渐变
        int base = hsvToRgb(hue, 1f, 1f);
        int baseR = (base >> 16) & 0xFF;
        int baseG = (base >> 8) & 0xFF;
        int baseB = base & 0xFF;
        for (int x = 0; x < squareSize; x++) {
            float s = x / (float) (squareSize - 1);
            int r = Math.round(255 + (baseR - 255) * s);
            int g = Math.round(255 + (baseG - 255) * s);
            int b = Math.round(255 + (baseB - 255) * s);
            Gui.drawRect(squareX + x, squareY, squareX + x + 1, squareY + squareSize,
                0xFF000000 | (r << 16) | (g << 8) | b);
        }
        for (int y = 0; y < squareSize; y++) {
            int a = Math.round(255 * (y / (float) (squareSize - 1)));
            Gui.drawRect(squareX, squareY + y, squareX + squareSize, squareY + y + 1, a << 24);
        }
        int cx = squareX + (int) (sat * squareSize);
        int cy = squareY + (int) ((1f - val) * squareSize);
        Gui.drawRect(cx - 3, cy - 3, cx + 4, cy + 4, 0xFF000000);
        Gui.drawRect(cx - 2, cy - 2, cx + 3, cy + 3, 0xFFFFFFFF);

        // 输入框
        int fieldsX = barX + barW + 8;
        int fieldsY = squareY;
        int rowH = FIELD_H + 8;
        drawField(fieldsX, fieldsY, "Hex", hexField, mouseX, mouseY);
        drawField(fieldsX, fieldsY + rowH, "R", rField, mouseX, mouseY);
        drawField(fieldsX, fieldsY + rowH * 2, "G", gField, mouseX, mouseY);
        drawField(fieldsX, fieldsY + rowH * 3, "B", bField, mouseX, mouseY);

        int pvX = fieldsX + FIELD_LABEL_W;
        int pvY = fieldsY + rowH * 4;
        int pvW = FIELD_W;
        int pvH = btnY - 6 - pvY;
        if (pvH > 0) {
            Gui.drawRect(pvX, pvY, pvX + pvW, pvY + pvH, 0xFF000000 | (color & 0xFFFFFF));
        }

        drawButton(cancelBtnX, btnY, BTN_W, BTN_H, I18n.format("gui.cancel"), 0xFFFC887E, mouseX, mouseY);
        drawButton(doneBtnX, btnY, BTN_W, BTN_H, I18n.format("gui.done"), 0xFFB7F3C8, mouseX, mouseY);
    }

    private void drawField(int x, int y, String label, GuiTextField field, int mouseX, int mouseY) {
        fontRenderer.drawStringWithShadow(label, x, y + 1, 0xFFA0A0A0);
        int bx = x + FIELD_LABEL_W;
        Gui.drawRect(bx, y, bx + FIELD_W, y + FIELD_H, 0x20000000);
        int border = (field.isFocused() || (mouseX >= bx && mouseX <= bx + FIELD_W
            && mouseY >= y && mouseY <= y + FIELD_H)) ? 0xFFFFFFFF : 0xFFA0A0A0;
        Gui.drawRect(bx, y, bx + FIELD_W, y + 1, border);
        Gui.drawRect(bx, y + FIELD_H - 1, bx + FIELD_W, y + FIELD_H, border);
        Gui.drawRect(bx, y, bx + 1, y + FIELD_H, border);
        Gui.drawRect(bx + FIELD_W - 1, y, bx + FIELD_W, y + FIELD_H, border);
        field.drawTextBox();
    }

    private void drawButton(int x, int y, int w, int h, String text, int accent,
                            int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        Gui.drawRect(x, y, x + w, y + h, hovered ? 0x40000000 : 0x20000000);
        int border = hovered ? 0xFFFFFFFF : 0xFFA0A0A0;
        Gui.drawRect(x, y, x + w, y + 1, border);
        Gui.drawRect(x, y + h - 1, x + w, y + h, border);
        Gui.drawRect(x, y, x + 1, y + h, border);
        Gui.drawRect(x + w - 1, y, x + w, y + h, border);
        fontRenderer.drawStringWithShadow(text,
            x + (w - fontRenderer.getStringWidth(text)) / 2.0f,
            y + (h - 8) / 2.0f, accent);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton != 0) return;

        if (mouseX >= squareX && mouseX <= squareX + squareSize
            && mouseY >= squareY && mouseY <= squareY + squareSize) {
            draggingSquare = true;
            updateFromSquare(mouseX, mouseY);
            return;
        }
        if (mouseX >= barX && mouseX <= barX + barW && mouseY >= barY && mouseY <= barY + barH) {
            draggingBar = true;
            updateFromBar(mouseY);
            return;
        }
        if (mouseX >= doneBtnX && mouseX <= doneBtnX + BTN_W
            && mouseY >= btnY && mouseY <= btnY + BTN_H) {
            onChange.accept(color);
            mc.displayGuiScreen(parent);
            return;
        }
        if (mouseX >= cancelBtnX && mouseX <= cancelBtnX + BTN_W
            && mouseY >= btnY && mouseY <= btnY + BTN_H) {
            mc.displayGuiScreen(parent);
            return;
        }

        hexField.mouseClicked(mouseX, mouseY, mouseButton);
        rField.mouseClicked(mouseX, mouseY, mouseButton);
        gField.mouseClicked(mouseX, mouseY, mouseButton);
        bField.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
        if (draggingSquare) {
            updateFromSquare(mouseX, mouseY);
        } else if (draggingBar) {
            updateFromBar(mouseY);
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
        draggingSquare = false;
        draggingBar = false;
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(parent);
            return;
        }
        if (hexField.textboxKeyTyped(typedChar, keyCode)) return;
        if (rField.textboxKeyTyped(typedChar, keyCode)) return;
        if (gField.textboxKeyTyped(typedChar, keyCode)) return;
        if (bField.textboxKeyTyped(typedChar, keyCode)) return;
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    // ===== HSV 转换 =====

    static int hsvToRgb(float hue, float sat, float val) {
        int r;
        int g;
        int b;
        if (sat <= 0f) {
            int v = Math.round(val * 255f);
            r = g = b = v;
        } else {
            float h = (hue - (float) Math.floor(hue)) * 6f;
            int i = (int) Math.floor(h);
            float f = h - i;
            float p = val * (1f - sat);
            float q = val * (1f - sat * f);
            float t = val * (1f - sat * (1f - f));
            float rr = 0, gg = 0, bb = 0;
            switch (i % 6) {
                case 0:
                    rr = val; gg = t; bb = p;
                    break;
                case 1:
                    rr = q; gg = val; bb = p;
                    break;
                case 2:
                    rr = p; gg = val; bb = t;
                    break;
                case 3:
                    rr = p; gg = q; bb = val;
                    break;
                case 4:
                    rr = t; gg = p; bb = val;
                    break;
                default:
                    rr = val; gg = p; bb = q;
                    break;
            }
            r = Math.round(rr * 255f);
            g = Math.round(gg * 255f);
            b = Math.round(bb * 255f);
        }
        return (r << 16) | (g << 8) | b;
    }

    static float[] rgbToHsv(int r, int g, int b) {
        float rf = r / 255f, gf = g / 255f, bf = b / 255f;
        float max = Math.max(rf, Math.max(gf, bf));
        float min = Math.min(rf, Math.min(gf, bf));
        float delta = max - min;
        float h = 0f;
        if (delta > 1.0E-6f) {
            if (max == rf) {
                h = ((gf - bf) / delta) % 6f;
            } else if (max == gf) {
                h = (bf - rf) / delta + 2f;
            } else {
                h = (rf - gf) / delta + 4f;
            }
            h *= 60f;
            if (h < 0) h += 360f;
            h /= 360f;
        }
        float s = max <= 1.0E-6f ? 0f : delta / max;
        return new float[]{h, s, max};
    }
}
