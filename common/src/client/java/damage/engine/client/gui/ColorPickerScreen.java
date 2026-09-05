package damage.engine.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

import java.util.function.Consumer;

/**
 * 独立窗口(居中)的颜色选择器。
 *
 * <p>左侧为可拖动的饱和度/亮度大方块,右侧为可上下拖动的色相条;
 * 下方为 Hex / R / G / B 输入框与颜色预览。「确定」通过 {@code onChange}
 * 应用颜色并返回配置界面,「取消」或 ESC 直接返回。</p>
 */
public class ColorPickerScreen extends Screen {
    private final Screen parent;
    private final Consumer<Integer> onChange;

    private int color;          // ARGB, alpha 恒为 0xFF
    private float hue = 0f;
    private float sat = 1f;
    private float val = 1f;

    // 布局
    private int panelX, panelY, panelW, panelH;
    private int squareX, squareY, squareSize;
    private int barX, barY, barW, barH;
    private int fieldsX, fieldsY, rowH;
    private int doneBtnX, cancelBtnX, btnY, btnW, btnH;

    private EditBox hexField, rField, gField, bField;
    private boolean updatingFields = false;
    private boolean draggingSquare = false;
    private boolean draggingBar = false;

    private NativeImage squareImage;
    private DynamicTexture squareTexture;
    private ResourceLocation squareTexId;
    private float squareHueCache = -1f;

    private static final int FIELD_W = 56;
    private static final int FIELD_H = 18;
    private static final int FIELD_LABEL_W = 22;

    public ColorPickerScreen(Screen parent, int initialColor, Consumer<Integer> onChange) {
        super(Component.translatable("title.damage-engine.color_picker"));
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
    protected void init() {
        this.clearWidgets();

        squareSize = 100;
        barW = 12;
        btnW = 72;
        btnH = 20;
        int pad = 10;
        int fieldColW = FIELD_LABEL_W + 4 + FIELD_W;
        panelW = pad + squareSize + 4 + barW + 8 + fieldColW + pad;
        rowH = FIELD_H + 2;
        int previewH = 26;
        panelH = pad + squareSize + 8 + btnH + pad + previewH;

        // 居中
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;

        squareX = panelX + pad;
        squareY = panelY + pad;
        barX = squareX + squareSize + 4;
        barY = squareY;
        barH = squareSize;

        fieldsX = barX + barW + 8;
        fieldsY = squareY;

        btnY = panelY + panelH - btnH - pad;
        doneBtnX = panelX + panelW - pad - btnW;
        cancelBtnX = doneBtnX - btnW - 6;

        createFields();
    }

    private void createFields() {
        hexField = new EditBox(this.font, 0, 0, FIELD_W, FIELD_H, Component.empty());
        hexField.setBordered(false);
        hexField.setMaxLength(6);
        hexField.setValue(String.format("%06X", color & 0xFFFFFF));
        hexField.setResponder(this::onHexChanged);

        rField = new EditBox(this.font, 0, 0, FIELD_W, FIELD_H, Component.empty());
        rField.setBordered(false);
        rField.setMaxLength(3);
        rField.setValue(String.valueOf((color >> 16) & 0xFF));
        rField.setResponder(s -> onRgbFieldChanged(rField, s));

        gField = new EditBox(this.font, 0, 0, FIELD_W, FIELD_H, Component.empty());
        gField.setBordered(false);
        gField.setMaxLength(3);
        gField.setValue(String.valueOf((color >> 8) & 0xFF));
        gField.setResponder(s -> onRgbFieldChanged(gField, s));

        bField = new EditBox(this.font, 0, 0, FIELD_W, FIELD_H, Component.empty());
        bField.setBordered(false);
        bField.setMaxLength(3);
        bField.setValue(String.valueOf(color & 0xFF));
        bField.setResponder(s -> onRgbFieldChanged(bField, s));
    }

    private void onHexChanged(String s) {
        if (updatingFields) return;
        String filtered = s.replaceAll("[^0-9a-fA-F]", "");
        if (!filtered.equals(s)) {
            hexField.setValue(filtered);
            return;
        }
        try {
            if (!s.isEmpty() && s.length() <= 6) {
                int rgb = (int) Long.parseLong(s, 16);
                updatingFields = true;
                setColor(0xFF000000 | rgb);
                syncFields();
                updatingFields = false;
            }
        } catch (NumberFormatException ignored) {}
    }

    private void onRgbFieldChanged(EditBox src, String s) {
        if (updatingFields) return;
        String filtered = s.replaceAll("[^0-9]", "");
        if (!filtered.equals(s)) {
            src.setValue(filtered);
            return;
        }
        if (s.isEmpty()) return;
        try {
            int r = parseClamped(rField.getValue(), 0, 255, (color >> 16) & 0xFF);
            int g = parseClamped(gField.getValue(), 0, 255, (color >> 8) & 0xFF);
            int b = parseClamped(bField.getValue(), 0, 255, color & 0xFF);
            updatingFields = true;
            setColor(0xFF000000 | (r << 16) | (g << 8) | b);
            syncFields();
            updatingFields = false;
        } catch (NumberFormatException ignored) {}
    }

    private static int parseClamped(String s, int min, int max, int fallback) {
        if (s == null || s.isEmpty()) return fallback;
        try {
            int v = Integer.parseInt(s);
            return Mth.clamp(v, min, max);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void syncFields() {
        hexField.setValue(String.format("%06X", color & 0xFFFFFF));
        rField.setValue(String.valueOf((color >> 16) & 0xFF));
        gField.setValue(String.valueOf((color >> 8) & 0xFF));
        bField.setValue(String.valueOf(color & 0xFF));
    }

    private void syncFromPicker() {
        color = 0xFF000000 | hsvToRgb(hue, sat, val);
        updatingFields = true;
        syncFields();
        updatingFields = false;
    }

    private void updateFromSquare(double x, double y) {
        sat = Mth.clamp((float) ((x - squareX) / (double) squareSize), 0f, 1f);
        val = Mth.clamp(1f - (float) ((y - squareY) / (double) squareSize), 0f, 1f);
        syncFromPicker();
    }

    private void updateFromBar(double y) {
        hue = Mth.clamp((float) ((y - barY) / (double) barH), 0f, 1f);
        syncFromPicker();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn != 0) return super.mouseClicked(mx, my, btn);
        if (mx >= squareX && mx <= squareX + squareSize && my >= squareY && my <= squareY + squareSize) {
            draggingSquare = true;
            updateFromSquare(mx, my);
            return true;
        }
        if (mx >= barX && mx <= barX + barW && my >= barY && my <= barY + barH) {
            draggingBar = true;
            updateFromBar(my);
            return true;
        }
        if (mx >= doneBtnX && mx <= doneBtnX + btnW && my >= btnY && my <= btnY + btnH) {
            this.playClickSound();
            onChange.accept(color);
            this.minecraft.setScreen(parent);
            return true;
        }
        if (mx >= cancelBtnX && mx <= cancelBtnX + btnW && my >= btnY && my <= btnY + btnH) {
            this.playClickSound();
            this.minecraft.setScreen(parent);
            return true;
        }
        for (EditBox f : new EditBox[]{hexField, rField, gField, bField}) {
            f.setFocused(f.isMouseOver(mx, my));
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (btn != 0) return super.mouseDragged(mx, my, btn, dx, dy);
        if (draggingSquare) { updateFromSquare(mx, my); return true; }
        if (draggingBar) { updateFromBar(my); return true; }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        draggingSquare = false;
        draggingBar = false;
        return super.mouseReleased(mx, my, btn);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (EditBox f : new EditBox[]{hexField, rField, gField, bField}) {
            if (f.isFocused() && f.keyPressed(keyCode, scanCode, modifiers)) return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            this.minecraft.setScreen(parent);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char ch, int modifiers) {
        for (EditBox f : new EditBox[]{hexField, rField, gField, bField}) {
            if (f.isFocused() && f.charTyped(ch, modifiers)) return true;
        }
        return super.charTyped(ch, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        for (EditBox f : new EditBox[]{hexField, rField, gField, bField}) {
            if (f.isFocused() && f.keyReleased(keyCode, scanCode, modifiers)) return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    private void playClickSound() {
        if (this.minecraft != null && this.minecraft.getSoundManager() != null) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        this.renderBackground(g);

        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xFF202020);
        g.fill(panelX, panelY, panelX + panelW, panelY + 1, 0xFFA0A0A0);
        g.fill(panelX, panelY + panelH - 1, panelX + panelW, panelY + panelH, 0xFFA0A0A0);
        g.fill(panelX, panelY, panelX + 1, panelY + panelH, 0xFFA0A0A0);
        g.fill(panelX + panelW - 1, panelY, panelX + panelW, panelY + panelH, 0xFFA0A0A0);

        for (int i = 0; i < barH; i++) {
            int c = hsvToRgb(i / (float) barH, 1f, 1f) | 0xFF000000;
            g.fill(barX, barY + i, barX + barW, barY + i + 1, c);
        }
        int indicatorY = barY + (int) (hue * barH);
        g.fill(barX - 2, indicatorY - 2, barX + barW + 2, indicatorY + 3, 0xFFFFFFFF);

        ensureSquareTexture();
        if (squareTexId != null) {
            g.blit(squareTexId, squareX, squareY, squareSize, squareSize, 0.0f, 0.0f, 256, 256, 256, 256);
            g.fill(squareX, squareY, squareX + squareSize, squareY + 1, 0xFF000000);
            g.fill(squareX, squareY + squareSize - 1, squareX + squareSize, squareY + squareSize, 0xFF000000);
            g.fill(squareX, squareY, squareX + 1, squareY + squareSize, 0xFF000000);
            g.fill(squareX + squareSize - 1, squareY, squareX + squareSize, squareY + squareSize, 0xFF000000);
            int cx = squareX + (int) (sat * squareSize);
            int cy = squareY + (int) ((1f - val) * squareSize);
            g.fill(cx - 3, cy - 3, cx + 4, cy + 4, 0xFF000000);
            g.fill(cx - 2, cy - 2, cx + 3, cy + 3, 0xFFFFFFFF);
        }

        drawField(g, fieldsX, fieldsY, "Hex", hexField, mouseX, mouseY);
        drawField(g, fieldsX, fieldsY + rowH, "R", rField, mouseX, mouseY);
        drawField(g, fieldsX, fieldsY + rowH * 2, "G", gField, mouseX, mouseY);
        drawField(g, fieldsX, fieldsY + rowH * 3, "B", bField, mouseX, mouseY);
        int pvX = fieldsX + FIELD_LABEL_W;
        int pvY = fieldsY + rowH * 4 + 2;
        int pvW = FIELD_W;
        int pvH = btnY - 6 - pvY;
        if (pvH > 0) {
            g.fill(pvX, pvY, pvX + pvW, pvY + pvH, 0xFF000000 | (color & 0xFFFFFF));
        }

        drawButton(g, cancelBtnX, btnY, btnW, btnH, Component.translatable("gui.cancel"), 0xFFFC887E);
        drawButton(g, doneBtnX, btnY, btnW, btnH, Component.translatable("gui.done"), 0xFFB7F3C8);
    }

    private void drawField(GuiGraphics g, int x, int y, String label, EditBox field, int mx, int my) {
        g.drawString(this.font, label, x, y + 5, 0xFFA0A0A0);
        int bx = x + FIELD_LABEL_W;
        g.fill(bx, y, bx + FIELD_W, y + FIELD_H, 0x20000000);
        int bc = (field.isFocused() || field.isMouseOver(mx, my)) ? 0xFFFFFFFF : 0xFFA0A0A0;
        g.fill(bx, y, bx + FIELD_W, y + 1, bc);
        g.fill(bx, y + FIELD_H - 1, bx + FIELD_W, y + FIELD_H, bc);
        g.fill(bx, y, bx + 1, y + FIELD_H, bc);
        g.fill(bx + FIELD_W - 1, y, bx + FIELD_W, y + FIELD_H, bc);
        field.setX(bx + 3);
        field.setY(y + 5);
        field.setWidth(FIELD_W - 6);
        field.render(g, mx, my, 0f);
    }

    private void drawButton(GuiGraphics g, int x, int y, int w, int h, Component text, int accent) {
        g.fill(x, y, x + w, y + h, 0x20000000);
        int bc = 0xFFA0A0A0;
        g.fill(x, y, x + w, y + 1, bc);
        g.fill(x, y + h - 1, x + w, y + h, bc);
        g.fill(x, y, x + 1, y + h, bc);
        g.fill(x + w - 1, y, x + w, y + h, bc);
        g.drawCenteredString(this.font, text, x + w / 2, y + (h - 8) / 2, accent);
    }

    private void ensureSquareTexture() {
        Minecraft client = Minecraft.getInstance();
        if (squareTexture == null) {
            squareImage = new NativeImage(256, 256, true);
            squareTexId = new ResourceLocation("damage-engine", "color_square_" + System.nanoTime());
            squareTexture = new DynamicTexture(squareImage);
            client.getTextureManager().register(squareTexId, squareTexture);
            squareHueCache = -1f;
        }
        if (Math.abs(hue - squareHueCache) > 0.0005f) {
            squareHueCache = hue;
            for (int y = 0; y < 256; y++) {
                float v = 1f - y / 255f;
                for (int x = 0; x < 256; x++) {
                    float s = x / 255f;
                    int argb = 0xFF000000 | hsvToRgb(hue, s, v);
                    // setPixelRGBA 按 ABGR 字节序存储,需交换 R/B
                    int abgr = (argb & 0xFF00FF00) | ((argb & 0xFF) << 16) | ((argb >>> 16) & 0xFF);
                    squareImage.setPixelRGBA(x, y, abgr);
                }
            }
            squareTexture.upload();
        }
    }

    // ===== HSV 转换 =====

    static int hsvToRgb(float hue, float sat, float val) {
        int r, g, b;
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
                case 0 -> { rr = val; gg = t; bb = p; }
                case 1 -> { rr = q; gg = val; bb = p; }
                case 2 -> { rr = p; gg = val; bb = t; }
                case 3 -> { rr = p; gg = q; bb = val; }
                case 4 -> { rr = t; gg = p; bb = val; }
                case 5 -> { rr = val; gg = p; bb = q; }
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