package damage.engine;

import damage.engine.hud.format.DamageNumberFormat;
import damage.engine.hud.info.InfoPanelRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** 1.12.2 版 HUD 编辑器：拖拽移动、方块缩放、撤回/重做/重置与键盘微调。 */
public class GuiHudEditor extends GuiScreen {
    private static final int BTN_W = 40;
    private static final int BTN_H = 20;
    private static final int HANDLE = 5;

    private final GuiScreen parent;
    private final DamageEngineConfig config = DamageEngineConfig.getInstance();
    private final List<EditorModule> modules = new ArrayList<>();

    private EditorModule selectedModule = null;
    private boolean dragging = false;
    private boolean resizing = false;
    private int lastDragX;
    private int lastDragY;

    private final Deque<Runnable> undoStack = new ArrayDeque<>();
    private final Deque<Runnable> redoStack = new ArrayDeque<>();
    private final List<EditorButton> buttons = new ArrayList<>();

    private int resetState = 0;
    private long resetActionTime = 0;
    private long lastKeyboardAdjustTime = 0;

    /** 自绘按钮，样式与配置界面一致。 */
    private static final class EditorButton {
        final int x;
        final int y;
        final int w;
        final int h;
        final Runnable action;
        String text;
        boolean enabled = true;

        EditorButton(int x, int y, int w, int h, String text, Runnable action) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.text = text;
            this.action = action;
        }

        boolean hovered(int mouseX, int mouseY) {
            return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        }
    }

    public GuiHudEditor(GuiScreen parent) {
        this.parent = parent;
        modules.add(new EditorModule(config.totalDamageConfig, ModuleType.TOTAL));
        modules.add(new EditorModule(config.ratingConfig, ModuleType.RATING));
        modules.add(new EditorModule(config.ratingScoreConfig, ModuleType.RATING_SCORE));
        modules.add(new EditorModule(config.infoConfig, ModuleType.INFO));
    }

    @Override
    public void initGui() {
        buttons.clear();
        buttons.add(new EditorButton(10, 10, BTN_W, BTN_H,
            I18n.format("hud.editor.undo"), this::undo));
        buttons.add(new EditorButton(55, 10, BTN_W, BTN_H,
            I18n.format("hud.editor.redo"), this::redo));
        buttons.add(new EditorButton(100, 10, BTN_W, BTN_H,
            I18n.format("hud.editor.reset"), this::handleResetClick));
        updateButtons();
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        updateButtons();
    }

    private void updateButtons() {
        if (buttons.size() < 3) return;
        buttons.get(0).enabled = !undoStack.isEmpty();
        buttons.get(1).enabled = !redoStack.isEmpty();
        EditorButton reset = buttons.get(2);

        if (resetState == 2 && System.currentTimeMillis() - resetActionTime > 3000) {
            resetState = 0;
            reset.text = I18n.format("hud.editor.reset");
        } else if (resetState == 1 && System.currentTimeMillis() - resetActionTime > 5000) {
            resetState = 0;
            reset.text = I18n.format("hud.editor.reset");
        }
    }

    private void handleResetClick() {
        if (resetState == 0) {
            resetState = 1;
            resetActionTime = System.currentTimeMillis();
            if (buttons.size() > 2) buttons.get(2).text = I18n.format("gui.confirm") + "?";
        } else if (resetState == 1) {
            resetState = 2;
            resetActionTime = System.currentTimeMillis();
            resetAllModules();
            if (buttons.size() > 2) {
                buttons.get(2).text = I18n.format("text.damage-engine.reset_done");
            }
        }
    }

    private void resetAllModules() {
        saveStateForUndo();
        // 重置后以当前窗口作为布局基准，避免旧基准让整套 HUD 被缩放
        config.hudLayoutBaseWidth = this.width;
        config.hudLayoutBaseHeight = this.height;

        config.totalDamageConfig.x = 0.8160156f;
        config.totalDamageConfig.y = 0.37278107f;
        config.totalDamageConfig.scale = 0.95652175f;

        config.ratingConfig.x = 0.7867187f;
        config.ratingConfig.y = 0.31508327f;
        config.ratingConfig.scale = 1.4066461f;

        config.ratingScoreConfig.x = 0.7867187f;
        config.ratingScoreConfig.y = 0.365f;
        config.ratingScoreConfig.scale = 1.0f;

        config.infoConfig.x = 0.61875f;
        config.infoConfig.y = 0.59909815f;
        config.infoConfig.scale = 0.5539445f;

        config.save();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();

        for (EditorModule module : modules) {
            renderModule(module);
        }
        if (selectedModule != null) {
            drawSelection(selectedModule);
        }

        for (EditorButton button : buttons) {
            drawEditorButton(button, mouseX, mouseY);
        }
        fontRenderer.drawStringWithShadow(I18n.format("hud.editor.guide"), 148, 16, 0xFFA0A0A0);
    }

    private void drawEditorButton(EditorButton button, int mouseX, int mouseY) {
        boolean hovered = button.hovered(mouseX, mouseY);
        Gui.drawRect(button.x, button.y, button.x + button.w, button.y + button.h,
            hovered && button.enabled ? 0xA0202020 : 0x80000000);
        int border = !button.enabled ? 0xFF808080 : (hovered ? 0xFFFFFFFF : 0xFFA0A0A0);
        Gui.drawRect(button.x, button.y, button.x + button.w, button.y + 1, border);
        Gui.drawRect(button.x, button.y + button.h - 1, button.x + button.w, button.y + button.h, border);
        Gui.drawRect(button.x, button.y, button.x + 1, button.y + button.h, border);
        Gui.drawRect(button.x + button.w - 1, button.y, button.x + button.w, button.y + button.h, border);
        int color = button.enabled ? 0xFFFFFFFF : 0xFF808080;
        fontRenderer.drawStringWithShadow(button.text,
            button.x + (button.w - fontRenderer.getStringWidth(button.text)) / 2.0f,
            button.y + (button.h - 8) / 2.0f, color);
    }

    private void renderModule(EditorModule module) {
        int x = config.resolveHudX(module.config, width, height);
        int y = config.resolveHudY(module.config, height);
        float scale = config.resolveHudScale(module.config, height);

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0.0f);
        GlStateManager.scale(scale, scale, 1.0f);
        try {
            switch (module.type) {
                case TOTAL:
                    renderTotalPreview();
                    break;
                case RATING:
                    String gradeText = "A";
                    int gradeColor = 0xFFD700;
                    if (config.ratingGrades != null && !config.ratingGrades.isEmpty()) {
                        DamageEngineConfig.RatingGrade grade = config.ratingGrades.get(
                            (int) ((System.currentTimeMillis() / 1500) % config.ratingGrades.size()));
                        gradeText = grade.text;
                        gradeColor = grade.color;
                    }
                    damage.engine.hud.rating.RatingRenderer.renderGrade(true, 1.0f, gradeText, gradeColor);
                    break;
                case RATING_SCORE:
                    damage.engine.hud.rating.RatingRenderer.renderScore(true, 1.0f);
                    break;
                case INFO:
                    InfoPanelRenderer.renderPreview();
                    break;
            }
        } finally {
            GlStateManager.popMatrix();
        }
    }

    private void renderTotalPreview() {
        DamageHudRenderer.renderPreviewTotal();
    }

    private void drawCentered(String text, int y, int rgb) {
        fontRenderer.drawStringWithShadow(text, -fontRenderer.getStringWidth(text) / 2.0f, y, rgb);
    }

    private void drawSelection(EditorModule module) {
        int[] b = getBounds(module);
        int color = 0xFFB5F0C6;
        Gui.drawRect(b[0], b[1], b[0] + b[2], b[1] + 1, color);
        Gui.drawRect(b[0], b[1] + b[3] - 1, b[0] + b[2], b[1] + b[3], color);
        Gui.drawRect(b[0], b[1] + 1, b[0] + 1, b[1] + b[3] - 1, color);
        Gui.drawRect(b[0] + b[2] - 1, b[1] + 1, b[0] + b[2], b[1] + b[3] - 1, color);
        int hx = b[0] + b[2] - HANDLE;
        int hy = b[1] + b[3] - HANDLE;
        Gui.drawRect(hx, hy, hx + HANDLE, hy + HANDLE, color);
    }

    /** @return {x, y, w, h}（屏幕像素） */
    private int[] getBounds(EditorModule module) {
        int cx = config.resolveHudX(module.config, width, height);
        int cy = config.resolveHudY(module.config, height);
        float s = config.resolveHudScale(module.config, height);

        // 直接采用各渲染器最近一帧的实际内容包围盒，保证选框贴合模块
        float[] local;
        switch (module.type) {
            case TOTAL:
                local = DamageHudRenderer.getLastTotalBounds();
                if (local == null) local = new float[]{-34f, -14f, 68f, 60f};
                break;
            case RATING:
                local = damage.engine.hud.rating.RatingRenderer.getLastGradeBounds();
                if (local == null) local = new float[]{-8f, -7f, 16f, 14f};
                break;
            case RATING_SCORE:
                local = damage.engine.hud.rating.RatingRenderer.getLastScoreBounds();
                if (local == null) local = new float[]{-30f, 0f, 60f, 34f};
                break;
            case INFO:
            default:
                // InfoPanelRenderer 面板：113 宽、30 高，背景再外扩 2px
                local = new float[]{-73f, -17f, 117f, 34f};
                break;
        }

        int x = cx + Math.round(local[0] * s) - 2;
        int y = cy + Math.round(local[1] * s) - 2;
        int w = Math.max(6, Math.round(local[2] * s)) + 4;
        int h = Math.max(6, Math.round(local[3] * s)) + 4;
        return new int[]{x, y, w, h};
    }

    private boolean isOverHandle(int mx, int my, EditorModule module) {
        int[] b = getBounds(module);
        int hx = b[0] + b[2] - HANDLE;
        int hy = b[1] + b[3] - HANDLE;
        return mx >= hx && mx <= hx + HANDLE && my >= hy && my <= hy + HANDLE;
    }

    private boolean isOverModule(int mx, int my, EditorModule module) {
        int[] b = getBounds(module);
        return mx >= b[0] && mx <= b[0] + b[2] && my >= b[1] && my <= b[1] + b[3];
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton == 0) {
            for (EditorButton button : buttons) {
                if (button.hovered(mouseX, mouseY)) {
                    if (button.enabled) {
                        button.action.run();
                    }
                    return;
                }
            }
        }
        if (mouseButton != 0) return;

        if (selectedModule != null && isOverHandle(mouseX, mouseY, selectedModule)) {
            saveStateForUndo();
            resizing = true;
            lastDragX = mouseX;
            lastDragY = mouseY;
            return;
        }

        // 从后往前命中检测：后绘制的模块在上层，先响应点击
        for (int i = modules.size() - 1; i >= 0; i--) {
            EditorModule module = modules.get(i);
            if (isOverModule(mouseX, mouseY, module)) {
                selectedModule = module;
                saveStateForUndo();
                dragging = true;

                int cx = config.resolveHudX(module.config, width, height);
                int cy = config.resolveHudY(module.config, height);
                // 记录鼠标与模块中心的偏移，拖动时用「鼠标位置 - 偏移」得到新的中心
                lastDragX = mouseX - cx;
                lastDragY = mouseY - cy;
                return;
            }
        }

        selectedModule = null;
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
        if (selectedModule == null) return;

        if (resizing) {
            int dx = mouseX - lastDragX;
            int dy = mouseY - lastDragY;
            lastDragX = mouseX;
            lastDragY = mouseY;
            selectedModule.config.scale += (dx + dy) * 0.01f;
            if (selectedModule.config.scale < 0.1f) selectedModule.config.scale = 0.1f;
            if (selectedModule.config.scale > 5.0f) selectedModule.config.scale = 5.0f;
            return;
        }

        if (dragging) {
            // 位置按「布局基准坐标」保存：渲染时再乘布局缩放，窗口变化不会破坏相对位置
            float baseW = config.hudBaseWidth(width);
            float layoutScale = config.hudLayoutScale(height);
            selectedModule.config.x = (mouseX - lastDragX) / (baseW * layoutScale);
            selectedModule.config.y = (mouseY - lastDragY) / (float) height;
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
        if (dragging || resizing) {
            dragging = false;
            resizing = false;
            config.save();
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(parent);
            return;
        }

        if (selectedModule != null) {
            if (keyCode == Keyboard.KEY_EQUALS || keyCode == Keyboard.KEY_ADD) {
                adjustModuleScale(0.01f);
                return;
            }
            if (keyCode == Keyboard.KEY_MINUS || keyCode == Keyboard.KEY_SUBTRACT) {
                adjustModuleScale(-0.01f);
                return;
            }
            if (keyCode == Keyboard.KEY_UP || keyCode == Keyboard.KEY_DOWN
                || keyCode == Keyboard.KEY_LEFT || keyCode == Keyboard.KEY_RIGHT) {
                int dx = 0;
                int dy = 0;
                if (keyCode == Keyboard.KEY_UP) dy = -1;
                else if (keyCode == Keyboard.KEY_DOWN) dy = 1;
                else if (keyCode == Keyboard.KEY_LEFT) dx = -1;
                else dx = 1;
                nudgeModule(dx, dy);
                return;
            }
        }

        super.keyTyped(typedChar, keyCode);
    }

    /** 连续键盘微调合并为一次撤销记录，避免撤销栈被快速刷屏 */
    private void saveKeyboardUndo() {
        long now = System.currentTimeMillis();
        if (now - lastKeyboardAdjustTime > 1000) {
            saveStateForUndo();
        }
        lastKeyboardAdjustTime = now;
    }

    private void adjustModuleScale(float delta) {
        saveKeyboardUndo();
        selectedModule.config.scale += delta;
        if (selectedModule.config.scale < 0.1f) selectedModule.config.scale = 0.1f;
        if (selectedModule.config.scale > 5.0f) selectedModule.config.scale = 5.0f;
    }

    private void nudgeModule(int dx, int dy) {
        saveKeyboardUndo();
        if (selectedModule.config.x == -1.0f) selectedModule.config.x = 0.5f;
        if (selectedModule.config.y == -1.0f) selectedModule.config.y = 0.5f;
        float baseW = config.hudBaseWidth(width);
        float layoutScale = config.hudLayoutScale(height);
        selectedModule.config.x += dx / (baseW * layoutScale);
        selectedModule.config.y += dy / (float) height;
    }

    private void saveStateForUndo() {
        final List<ModuleSnapshot> snapshot = captureState();
        undoStack.push(() -> restoreState(snapshot, true));
        redoStack.clear();
        updateButtons();
    }

    private void restoreState(List<ModuleSnapshot> stateToRestore, boolean isUndo) {
        final List<ModuleSnapshot> currentState = captureState();
        if (isUndo) {
            redoStack.push(() -> restoreState(currentState, false));
        } else {
            undoStack.push(() -> restoreState(currentState, true));
        }

        for (int i = 0; i < modules.size() && i < stateToRestore.size(); i++) {
            stateToRestore.get(i).apply(modules.get(i).config);
        }
        updateButtons();
    }

    private void undo() {
        if (!undoStack.isEmpty()) undoStack.pop().run();
    }

    private void redo() {
        if (!redoStack.isEmpty()) redoStack.pop().run();
    }

    private List<ModuleSnapshot> captureState() {
        List<ModuleSnapshot> list = new ArrayList<>();
        for (EditorModule module : modules) {
            list.add(new ModuleSnapshot(module.config));
        }
        return list;
    }

    @Override
    public void onGuiClosed() {
        // 以关闭编辑器时的窗口尺寸作为新的布局基准，之后缩放窗口整套 HUD 等比缩放
        config.hudLayoutBaseWidth = this.width;
        config.hudLayoutBaseHeight = this.height;
        config.save();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private static final class EditorModule {
        final DamageEngineConfig.ModuleConfig config;
        final ModuleType type;

        EditorModule(DamageEngineConfig.ModuleConfig config, ModuleType type) {
            this.config = config;
            this.type = type;
        }
    }

    private enum ModuleType {TOTAL, RATING, RATING_SCORE, INFO}

    private static final class ModuleSnapshot {
        final float x;
        final float y;
        final float scale;
        final boolean enabled;

        ModuleSnapshot(DamageEngineConfig.ModuleConfig config) {
            this.x = config.x;
            this.y = config.y;
            this.scale = config.scale;
            this.enabled = config.enabled;
        }

        void apply(DamageEngineConfig.ModuleConfig config) {
            config.x = x;
            config.y = y;
            config.scale = scale;
            config.enabled = enabled;
        }
    }
}
