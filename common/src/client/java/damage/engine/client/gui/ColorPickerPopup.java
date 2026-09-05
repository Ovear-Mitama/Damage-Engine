package damage.engine.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import java.util.function.Consumer;

/**
 * 悬浮式颜色选择器(叠加在配置界面上,不打开新窗口)。
 *
 * <p>顶部左侧为可拖动的饱和度/亮度大方块(右上角为当前色相纯色,越往左下越黑),
 * 右侧为可上下拖动的色相条。下方为 Hex / R / G / B 输入框(带范围限制)。
 * 点击「确定」后通过 {@code onChange} 回调应用颜色,「取消」或点击面板外关闭。</p>
 */
public class ColorPickerPopup {
    private final DamageConfigScreen screen;
    private final Consumer<Integer> onChange;
    private final Runnable onClose;

    private int color;          // ARGB, alpha 恒为 0xFF
    private float hue = 0f;     // 0..1
    private float sat = 1f;     // 0..1
    private float val = 1f;     // 0..1

    // 布局
    private final int panelX, panelY, panelW, panelH;
    private final int squareX, squareY, squareSize;
    private final int barX, barY, barW, barH;
    private final int fieldsX, fieldsY, rowH;
    private final int doneBtnX, cancelBtnX, btnY, btnW, btnH;

    // 输入框
    private EditBox hexField;
    private EditBox rField;
    private EditBox gField;
    private EditBox bField;
    private boolean updatingFields = false;

    // 拖动状态
    private boolean draggingSquare = false;
    private boolean draggingBar = false;

    // 方块纹理(色相变化时重建)
    private NativeImage squareImage;
    private DynamicTexture squareTexture;
    private Identifier squareTexId;
    private int squareHueCache = -1;

    private static final int FIELD_W = 56;
    private static final int FIELD_H = 18;
    private static final int FIELD_LABEL_W = 22;

    public ColorPickerPopup(DamageConfigScreen screen, int anchorX, int anchorY, int initialColor,
                            Consumer<Integer> onChange, Runnable onClose) {
        this.screen = screen;
        this.onChange = onChange;
        this.onClose = onClose;
        setColor(initialColor | 0xFF000000);

        // 紧凑布局:左侧方块+色相条,右侧 Hex/R/G/B 输入列,底部靠右按钮
        squareSize = 88;
        barW = 10;
        btnW = 72;
        btnH = 20;
        int pad = 8;
        int fieldColW = FIELD_LABEL_W + 4 + FIELD_W;
        panelW = pad + squareSize + 4 + barW + 8 + fieldColW + pad;
        rowH = FIELD_H + 2;
        int previewH = 24; // B 下方颜色预览区高度
        panelH = pad + squareSize + 8 + btnH + pad + previewH;

        // 位置:面板整体落在点击处左下方,并 clamp 到屏幕内
        int sw = screen.width;
        int sh = screen.height;
        int x = anchorX - panelW - 12;
        int y = anchorY + 10;
        x = Mth.clamp(x, 4, Math.max(4, sw - panelW - 4));
        y = Mth.clamp(y, 4, Math.max(4, sh - panelH - 4));
        panelX = x;
        panelY = y;

        squareX = panelX + pad;
        squareY = panelY + pad;
        barX = squareX + squareSize + 4;
        barY = squareY;
        barH = squareSize;

        fieldsX = barX + barW + 8;
        fieldsY = squareY;

        // 按钮靠右下角排列
        btnY = panelY + panelH - btnH - pad;
        doneBtnX = panelX + panelW - pad - btnW;
        cancelBtnX = doneBtnX - btnW - 6;

        createFields();
    }

    /** 该屏幕坐标是否位于悬浮窗内(用于遮挡背后列表的悬停) */
    public boolean contains(double x, double y) {
        return x >= panelX && x <= panelX + panelW && y >= panelY && y <= panelY + panelH;
    }

    private void createFields() {
        hexField = new EditBox(Minecraft.getInstance().font, 0, 0, FIELD_W, FIELD_H, Component.empty());
        hexField.setBordered(false);
        hexField.setMaxLength(7);
        hexField.setValue(String.format("%06X", color & 0xFFFFFF));
        hexField.setResponder(this::onHexChanged);

        rField = new EditBox(Minecraft.getInstance().font, 0, 0, FIELD_W, FIELD_H, Component.empty());
        rField.setBordered(false);
        rField.setMaxLength(3);
        rField.setValue(String.valueOf((color >> 16) & 0xFF));
        rField.setResponder(s -> onRgbFieldChanged(rField, s));

        gField = new EditBox(Minecraft.getInstance().font, 0, 0, FIELD_W, FIELD_H, Component.empty());
        gField.setBordered(false);
        gField.setMaxLength(3);
        gField.setValue(String.valueOf((color >> 8) & 0xFF));
        gField.setResponder(s -> onRgbFieldChanged(gField, s));

        bField = new EditBox(Minecraft.getInstance().font, 0, 0, FIELD_W, FIELD_H, Component.empty());
        bField.setBordered(false);
        bField.setMaxLength(3);
        bField.setValue(String.valueOf(color & 0xFF));
        bField.setResponder(s -> onRgbFieldChanged(bField, s));
    }

    // ===== 颜色与输入框 =====

    private void setColor(int argb) {
        this.color = argb | 0xFF000000;
        float[] hsv = rgbToHsv((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF);
        this.hue = hsv[0];
        this.sat = hsv[1];
        this.val = hsv[2];
    }

    private void onHexChanged(String s) {
        if (updatingFields) return;
        String filtered = s.replaceAll("[^0-9a-fA-F#]", "");
        if (filtered.indexOf('#') > 0) filtered = filtered.replace("#", ""); // # 仅允许位于开头
        if (!filtered.equals(s)) {
            hexField.setValue(filtered);
            return;
        }
        try {
            // 仅完整 6 位十六进制才应用,避免 "8"/"10" 等短输入被存成近似黑色(000008 等)
            String hex = s.startsWith("#") ? s.substring(1) : s;
            if (hex.length() == 6) {
                int rgb = (int) Long.parseLong(hex, 16);
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
        if (s.isEmpty()) {
            return; // 允许清空重输,不立即回填
        }
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

    public int getColor() {
        return color;
    }

    // ===== 事件(由 DamageConfigScreen 转发) =====

    private boolean inside(double x, double y) {
        return x >= panelX && x <= panelX + panelW && y >= panelY && y <= panelY + panelH;
    }

    /**
     * @return true 表示面板内已处理;false 表示点击在面板外(调用方应关闭 popup)
     */
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        double x = event.x();
        double y = event.y();
        if (!inside(x, y)) {
            return false;
        }
        if (event.button() != 0) {
            return true;
        }
        // 拖动区
        if (x >= squareX && x <= squareX + squareSize && y >= squareY && y <= squareY + squareSize) {
            draggingSquare = true;
            updateFromSquare(x, y);
            return true;
        }
        if (x >= barX && x <= barX + barW && y >= barY && y <= barY + barH) {
            draggingBar = true;
            updateFromBar(y);
            return true;
        }
        // 按钮
        if (x >= doneBtnX && x <= doneBtnX + btnW && y >= btnY && y <= btnY + btnH) {
            screen.playClickSound();
            onChange.accept(color);
            onClose.run();
            return true;
        }
        if (x >= cancelBtnX && x <= cancelBtnX + btnW && y >= btnY && y <= btnY + btnH) {
            screen.playClickSound();
            onClose.run();
            return true;
        }
        // 输入框焦点
        boolean hitField = false;
        for (EditBox f : new EditBox[]{hexField, rField, gField, bField}) {
            if (f.isMouseOver(x, y)) {
                f.setFocused(true);
                f.mouseClicked(event, bl);
                hitField = true;
            } else {
                f.setFocused(false);
            }
        }
        return hitField || true;
    }

    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (event.button() != 0) return false;
        if (draggingSquare) {
            updateFromSquare(event.x(), event.y());
            return true;
        }
        if (draggingBar) {
            updateFromBar(event.y());
            return true;
        }
        return false;
    }

    public void mouseReleased(MouseButtonEvent event) {
        draggingSquare = false;
        draggingBar = false;
    }

    public boolean keyPressed(KeyEvent event) {
        for (EditBox f : new EditBox[]{hexField, rField, gField, bField}) {
            if (f.isFocused()) {
                if (f.keyPressed(event)) return true;
            }
        }
        return false;
    }

    public boolean charTyped(CharacterEvent event) {
        for (EditBox f : new EditBox[]{hexField, rField, gField, bField}) {
            if (f.isFocused()) {
                if (f.charTyped(event)) return true;
            }
        }
        return false;
    }

    public boolean keyReleased(KeyEvent event) {
        for (EditBox f : new EditBox[]{hexField, rField, gField, bField}) {
            if (f.isFocused()) {
                if (f.keyReleased(event)) return true;
            }
        }
        return false;
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

    // ===== 渲染 =====

    public void render(GuiGraphicsExtractor g, int mx, int my, float dt) {
        // 窗体统一背景:深色不透明(无挖空、无缝隙)
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xE0000000);
        // 四周边框
        g.fill(panelX, panelY, panelX + panelW, panelY + 1, 0xFFA0A0A0);
        g.fill(panelX, panelY + panelH - 1, panelX + panelW, panelY + panelH, 0xFFA0A0A0);
        g.fill(panelX, panelY, panelX + 1, panelY + panelH, 0xFFA0A0A0);
        g.fill(panelX + panelW - 1, panelY, panelX + panelW, panelY + panelH, 0xFFA0A0A0);

        // 鼠标样式:方块内移动光标,色相条内上下缩放光标
        boolean overSquare = mx >= squareX && mx <= squareX + squareSize && my >= squareY && my <= squareY + squareSize;
        boolean overBar = mx >= barX && mx <= barX + barW && my >= barY && my <= barY + barH;
        if (draggingSquare || overSquare) {
            g.requestCursor(DamageConfigScreen.CURSOR_MOVE);
        } else if (draggingBar || overBar) {
            g.requestCursor(DamageConfigScreen.CURSOR_NS);
        }

        // 色相条(右侧,上下拖动;内部半透明,通透感)
        for (int i = 0; i < barH; i++) {
            int c = (hsvToRgb(i / (float) barH, 1f, 1f) & 0xFFFFFF) | 0x88000000;
            g.fill(barX, barY + i, barX + barW, barY + i + 1, c);
        }
        // 色相条指示器(白色)
        int indicatorY = barY + (int) (hue * barH);
        g.fill(barX - 2, indicatorY - 2, barX + barW + 2, indicatorY + 3, 0xFFFFFFFF);

        // 饱和度/亮度大方块(纹理;内部半透明,通透感)
        ensureSquareTexture();
        if (squareTexId != null) {
            g.blit(RenderPipelines.GUI_TEXTURED, squareTexId, squareX, squareY, 0.0f, 0.0f,
                squareSize, squareSize, squareSize, squareSize, squareSize, squareSize, 0x88FFFFFF);
            g.fill(squareX, squareY, squareX + squareSize, squareY + 1, 0xFF000000);
            g.fill(squareX, squareY + squareSize - 1, squareX + squareSize, squareY + squareSize, 0xFF000000);
            g.fill(squareX, squareY, squareX + 1, squareY + squareSize, 0xFF000000);
            g.fill(squareX + squareSize - 1, squareY, squareX + squareSize, squareY + squareSize, 0xFF000000);
            // 当前选择点指示
            int cx = squareX + (int) (sat * squareSize);
            int cy = squareY + (int) ((1f - val) * squareSize);
            g.fill(cx - 3, cy - 3, cx + 4, cy + 4, 0xFF000000);
            g.fill(cx - 2, cy - 2, cx + 3, cy + 3, 0xFFFFFFFF);
        }

        // 输入区
        drawField(g, fieldsX, fieldsY, "Hex", hexField, mx, my);
        drawField(g, fieldsX, fieldsY + rowH, "R", rField, mx, my);
        drawField(g, fieldsX, fieldsY + rowH * 2, "G", gField, mx, my);
        drawField(g, fieldsX, fieldsY + rowH * 3, "B", bField, mx, my);
        // B 下方:颜色预览色块(无文本,无边框,高度延伸到按钮上方)
        int pvX = fieldsX + FIELD_LABEL_W;
        int pvY = fieldsY + rowH * 4 + 2;
        int pvW = FIELD_W;
        int pvH = btnY - 6 - pvY;
        if (pvH > 0) {
            g.fill(pvX, pvY, pvX + pvW, pvY + pvH, 0xFF000000 | (color & 0xFFFFFF));
        }

        // 按钮
        drawButton(g, cancelBtnX, btnY, btnW, btnH, Component.translatable("gui.cancel"), 0xFFFC887E, mx, my);
        drawButton(g, doneBtnX, btnY, btnW, btnH, Component.translatable("gui.done"), 0xFFB7F3C8, mx, my);
    }

    private void drawField(GuiGraphicsExtractor g, int x, int y, String label, EditBox field, int mx, int my) {
        Minecraft client = Minecraft.getInstance();
        g.text(client.font, label, x, y + 5, 0xFFA0A0A0);
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
        field.setHeight(FIELD_H - 4);
        field.extractRenderState(g, mx, my, 0f);
    }

    private void drawButton(GuiGraphicsExtractor g, int x, int y, int w, int h, Component text, int accent, int mx, int my) {
        Minecraft client = Minecraft.getInstance();
        boolean over = mx >= x && mx <= x + w && my >= y && my <= y + h;
        if (over) g.requestCursor(DamageConfigScreen.CURSOR_HAND);
        g.fill(x, y, x + w, y + h, 0x20000000);
        int bc = over ? 0xFFFFFFFF : 0xFFA0A0A0;
        g.fill(x, y, x + w, y + 1, bc);
        g.fill(x, y + h - 1, x + w, y + h, bc);
        g.fill(x, y, x + 1, y + h, bc);
        g.fill(x + w - 1, y, x + w, y + h, bc);
        // 文本常驻 accent 色(无需悬停才变色)
        g.centeredText(client.font, text, x + w / 2, y + (h - 8) / 2, accent);
    }

    private void ensureSquareTexture() {
        int h = (int) (hue * 255);
        Minecraft client = Minecraft.getInstance();
        if (squareTexture == null) {
            squareImage = new NativeImage(256, 256, true);
            squareTexId = Identifier.fromNamespaceAndPath("damage-engine", "color_square_" + System.nanoTime());
            squareTexture = new DynamicTexture(() -> "damage-engine/color_square", squareImage);
            client.getTextureManager().register(squareTexId, squareTexture);
            squareHueCache = -1;
        }
        if (h != squareHueCache) {
            squareHueCache = h;
            for (int y = 0; y < 256; y++) {
                float v = 1f - y / 255f;
                for (int x = 0; x < 256; x++) {
                    float s = x / 255f;
                    int argb = 0xFF000000 | hsvToRgb(hue, s, v);
                    // NativeImage 存储为 ABGR,需字节交换
                    int abgr = (argb & 0xFF00FF00) | ((argb & 0xFF) << 16) | ((argb >> 16) & 0xFF);
                    squareImage.setPixelABGR(x, y, abgr);
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
