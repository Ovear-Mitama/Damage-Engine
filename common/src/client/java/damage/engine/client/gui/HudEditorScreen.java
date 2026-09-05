package damage.engine.client.gui;

import damage.engine.DamageEngineConfig;
import damage.engine.hud.DamageHud;
import damage.engine.hud.DamageSessionManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class HudEditorScreen extends Screen {
    private final Screen parent;
    private final DamageEngineConfig config;
    private final DamageHud damageHud;
    
    private EditorModule selectedModule = null;
    private boolean dragging = false;
    private boolean resizing = false;
    private float dragOffsetX, dragOffsetY;
    
    private final List<EditorModule> modules = new ArrayList<>();
    
    private final Deque<Runnable> undoStack = new ArrayDeque<>();
    private final Deque<Runnable> redoStack = new ArrayDeque<>();
    
    private int resetButtonState = 0;
    private long resetButtonActionTime = 0;

    private long lastKeyboardAdjustTime = 0;

    private DamageConfigScreen.StyledButton undoBtn;
    private DamageConfigScreen.StyledButton redoBtn;
    private DamageConfigScreen.StyledButton resetBtn;


    public HudEditorScreen(Screen parent) {
        super(Component.translatable("title.damage-engine.hud_editor"));
        this.parent = parent;
        this.config = DamageEngineConfig.getInstance();
        this.damageHud = new DamageHud();
        
        modules.add(new EditorModule(config.totalDamageConfig, ModuleType.TOTAL));
        modules.add(new EditorModule(config.ratingConfig, ModuleType.RATING));
        modules.add(new EditorModule(config.infoConfig, ModuleType.INFO));
    }
    
    public void playClickSound() {
        try {
             net.minecraft.client.resources.sounds.SimpleSoundInstance sound = net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.0F);
             
             Minecraft.getInstance().getSoundManager().play(sound);
        } catch (Exception e) {
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
    
    @Override
    protected void init() {

        int btnY = 10;
        int btnX = 10;
        int spacing = 5;
        
        undoBtn = new DamageConfigScreen.StyledButton(btnX, btnY, 40, 20, Component.translatable("hud.editor.undo"), this::undo);
        
        redoBtn = new DamageConfigScreen.StyledButton(btnX + 40 + spacing, btnY, 40, 20, Component.translatable("hud.editor.redo"), this::redo);
            
        resetBtn = new DamageConfigScreen.StyledButton(btnX + 40 + spacing + 40 + spacing, btnY, 40, 20, Component.translatable("hud.editor.reset").withColor(0xFFFC887E), this::handleResetClick);
            
        this.addRenderableWidget(undoBtn);
        this.addRenderableWidget(redoBtn);
        this.addRenderableWidget(resetBtn);
        
        updateButtons();
    }

    private void handleResetClick() {
        if (resetButtonState == 0) {
            resetButtonState = 1;
            resetBtn.setMessage(Component.translatable("gui.confirm").append("?").withColor(0xFFFC887E));
            resetButtonActionTime = System.currentTimeMillis();
        } else if (resetButtonState == 1) {
            resetButtonState = 2;
            resetButtonActionTime = System.currentTimeMillis();
            resetAllModules();
            resetBtn.setMessage(Component.translatable("text.damage-engine.reset_done").withColor(0xFFB5F0C6));
        }
    }
    
    private void updateButtons() {
        undoBtn.active = !undoStack.isEmpty();
        redoBtn.active = !redoStack.isEmpty();
        
        if (resetButtonState == 2) {
            if (System.currentTimeMillis() - resetButtonActionTime > 3000) {
                resetButtonState = 0;
                resetBtn.setMessage(Component.translatable("hud.editor.reset").withColor(0xFFFC887E));
            }
        } else if (resetButtonState == 1) {
             if (System.currentTimeMillis() - resetButtonActionTime > 5000) {
                 resetButtonState = 0;
                 resetBtn.setMessage(Component.translatable("hud.editor.reset").withColor(0xFFFC887E));
             }
        }
    }
    
    @Override
    public void tick() {
        super.tick();
        updateButtons();
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
        
        for (int i = 0; i < modules.size(); i++) {
             if (i < stateToRestore.size()) {
                 stateToRestore.get(i).apply(modules.get(i).config);
             }
        }
        updateButtons();
    }
    
    private void undo() {
        if (undoStack.isEmpty()) return;
        undoStack.pop().run();
    }
    
    private void redo() {
        if (redoStack.isEmpty()) return;
        redoStack.pop().run();
    }
    
    private List<ModuleSnapshot> captureState() {
        List<ModuleSnapshot> list = new ArrayList<>();
        for (EditorModule m : modules) list.add(new ModuleSnapshot(m.config));
        return list;
    }

    private void resetAllModules() {
        saveStateForUndo();
        config.totalDamageConfig.x = 0.8160156f;
        config.totalDamageConfig.y = 0.37278107f;
        config.totalDamageConfig.scale = 0.95652175f;
        
        config.infoConfig.x = 0.61875f;
        config.infoConfig.y = 0.59909815f;
        config.infoConfig.scale = 0.5539445f;
        
        config.ratingConfig.x = 0.7867187f;
        config.ratingConfig.y = 0.31508327f;
        config.ratingConfig.scale = 1.4066461f;
        config.save();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float delta) {
        // 1.21.11: in-game background blur is applied once by the render pipeline;
        // calling renderBackground() here would blur twice and crash.
        if (this.minecraft != null && this.minecraft.level == null) {
            this.extractPanorama(guiGraphics, delta);
        }

        // 1.21.11 cursor styles:
        // resize handle -> diagonal resize; selected module -> move; unselected module hover -> select
        if (selectedModule != null && isOverHandle(mouseX, mouseY, selectedModule)) {
            guiGraphics.requestCursor(DamageConfigScreen.CURSOR_NWSE);
        } else if (selectedModule != null && isOverModule(mouseX, mouseY, selectedModule)) {
            guiGraphics.requestCursor(DamageConfigScreen.CURSOR_MOVE);
        } else {
            for (EditorModule m : modules) {
                if (isOverModule(mouseX, mouseY, m)) {
                    guiGraphics.requestCursor(DamageConfigScreen.CURSOR_HAND);
                    break;
                }
            }
        }
        
        for (EditorModule m : modules) {
            renderModule(guiGraphics, m);
        }
        
        if (selectedModule != null) {
            drawSelection(guiGraphics, selectedModule);
        }
        
        for (net.minecraft.client.gui.components.events.GuiEventListener element : this.children()) {
            if (element instanceof net.minecraft.client.gui.components.Renderable) {
                ((net.minecraft.client.gui.components.Renderable) element).extractRenderState(guiGraphics, mouseX, mouseY, delta);
            }
        }

        // 左上角三个按钮(撤回/重做/重置)右侧的操作引导
        // 按钮终点 x=140,y=10..30;引导文字垂直居中对齐到按钮中心
        guiGraphics.text(this.font, Component.translatable("hud.editor.guide").getString(), 148, 16, 0xFFA0A0A0);
    }
    


    
    private void renderModule(GuiGraphicsExtractor guiGraphics, EditorModule m) {
        damageHud.renderModule(guiGraphics, m.config, this.minecraft, 1.0f, () -> {
            switch (m.type) {
                case TOTAL:
                    int previewLimit = DamageEngineConfig.getInstance().historyLimit;
                    List<DamageSessionManager.DamageEntry> history = new ArrayList<>();
                    float previewTotal = 0;
                    for (int j = 0; j < previewLimit; j++) {
                        boolean isCrit = j >= previewLimit - 3;
                        float dmg = 1.5f + j * 0.3f;
                        previewTotal += dmg;
                        history.add(new DamageSessionManager.DamageEntry(dmg, isCrit, 0L, 0));
                    }
                    damageHud.renderTotalDamage(guiGraphics, previewTotal, 0.7f, true, 1.0f, previewLimit, this.minecraft);
                    damageHud.renderHistory(guiGraphics, history, true, 1.0f, this.minecraft);
                    break;
                case RATING:
                    damageHud.cyclePreviewGrades();
                    damageHud.renderRating(guiGraphics, true, 1.0f, this.minecraft);
                    break;
                case INFO:
                    damageHud.renderInfo(guiGraphics, null, true, 1.0f, this.minecraft);
                    break;
            }
        });
    }
    
    private void drawSelection(GuiGraphicsExtractor guiGraphics, EditorModule m) {
        int[] b = getBounds(m);
        int greenColor = 0xFFB5F0C6;
        
        drawBorder(guiGraphics, b[0], b[1], b[2], b[3], greenColor);
        
        int handleSize = 5;
        int hx = b[0] + b[2] - handleSize;
        int hy = b[1] + b[3] - handleSize;
        guiGraphics.fill(hx, hy, hx + handleSize, hy + handleSize, greenColor);
    }
    
    private void drawBorder(GuiGraphicsExtractor guiGraphics, int x, int y, int width, int height, int color) {
        guiGraphics.fill(x, y, x + width, y + 1, color);
        guiGraphics.fill(x, y + height - 1, x + width, y + height, color);
        guiGraphics.fill(x, y + 1, x + 1, y + height - 1, color);
        guiGraphics.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }
    
    private int[] getBounds(EditorModule m) {
        int cx = m.config.x == -1.0f ? width / 2 : (int)(m.config.x * width);
        int cy = m.config.y == -1.0f ? height / 2 : (int)(m.config.y * height);
        float s = m.config.scale;
        
        int w = 0, h = 0;
        int ox = 0, oy = 0;
        
        switch (m.type) {
            case TOTAL:
                w = (int)(80 * s); h = (int)(55 * s);
                
                ox = (int)(-w/2 + 2 * s);
                oy = (int)(-h/2 + 12 * s);
                break;
            case RATING:
                w = (int)(30 * s); h = (int)(18 * s);
                ox = -w/2; oy = -h/2 - (int)(2 * s);
                break;
            case INFO:
                w = (int)(106 * s); h = (int)(34 * s);
                ox = (int)(-65 * s); oy = (int)(-17 * s);
                break;
        }
        return new int[]{cx + ox - 2, cy + oy - 2, w + 4, h + 4};
    }
    
    private boolean isOverHandle(double mx, double my, EditorModule m) {
        int[] b = getBounds(m);
        int handleSize = 5;
        int hx = b[0] + b[2] - handleSize;
        int hy = b[1] + b[3] - handleSize;
        return mx >= hx && mx <= hx + handleSize && my >= hy && my <= hy + handleSize;
    }
    
    private boolean isOverModule(double mx, double my, EditorModule m) {
        int[] b = getBounds(m);
        return mx >= b[0] && mx <= b[0] + b[2] && my >= b[1] && my <= b[1] + b[3];
    }
    
    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean bl) {
        if (super.mouseClicked(event, bl)) return true;
        
        this.setFocused(null);
        
        if (DamageConfigScreen.isPrimaryClick(event.button())) {
            double mx = event.x();
            double my = event.y();
            
            if (selectedModule != null && isOverHandle(mx, my, selectedModule)) {
                saveStateForUndo();
                resizing = true;
                dragOffsetX = (float)mx;
                dragOffsetY = (float)my;
                return true;
            }
            
            for (EditorModule m : modules) {
                if (isOverModule(mx, my, m)) {
                    if (selectedModule != m) {
                         selectedModule = m;
                         updateButtons();
                    }
                    saveStateForUndo();
                    dragging = true;
                    
                    int cx = m.config.x == -1.0f ? this.width / 2 : (int)(m.config.x * this.width);
                    int cy = m.config.y == -1.0f ? this.height / 2 : (int)(m.config.y * this.height);
                    
                    dragOffsetX = (float)mx - cx;
                    dragOffsetY = (float)my - cy;
                    return true;
                }
            }
            
            if (selectedModule != null) {
                selectedModule = null;
                updateButtons();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        if (super.mouseReleased(event)) return true;
        
        dragging = false;
        resizing = false;
        config.save();
        return false;
    }
    
    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double deltaX, double deltaY) {
        if (resizing && selectedModule != null) {
            float sensitivity = 0.01f;
            selectedModule.config.scale += (float)(deltaX + deltaY) * sensitivity; 
            if (selectedModule.config.scale < 0.1f) selectedModule.config.scale = 0.1f;
            if (selectedModule.config.scale > 5.0f) selectedModule.config.scale = 5.0f;
            return true;
        }
        
        if (dragging && selectedModule != null) {
            float newX = ((float)event.x() - dragOffsetX) / (float)width;
            float newY = ((float)event.y() - dragOffsetY) / (float)height;
            
            selectedModule.config.x = newX;
            selectedModule.config.y = newY;
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY); 
    }

    /**
     * 键盘微调:选中模块后,用 + / - 细微缩放,用方向键微调位置。
     */
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (selectedModule != null) {
            int keyCode = event.key();
            if (keyCode == InputConstants.KEY_EQUALS || keyCode == InputConstants.KEY_ADD) {
                adjustModuleScale(0.01f);
                return true;
            }
            if (keyCode == InputConstants.KEY_MINUS) {
                adjustModuleScale(-0.01f);
                return true;
            }
            if (keyCode == InputConstants.KEY_UP || keyCode == InputConstants.KEY_DOWN
                || keyCode == InputConstants.KEY_LEFT || keyCode == InputConstants.KEY_RIGHT) {
                int dx = 0, dy = 0;
                if (keyCode == InputConstants.KEY_UP) dy = -1;
                else if (keyCode == InputConstants.KEY_DOWN) dy = 1;
                else if (keyCode == InputConstants.KEY_LEFT) dx = -1;
                else dx = 1;
                nudgeModule(dx, dy);
                return true;
            }
        }
        return super.keyPressed(event);
    }

    /** 连续键盘微调合并为一次撤销记录,避免撤销栈被快速刷屏 */
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
        // -1 表示居中,先落到屏幕中心对应的实际坐标再微调
        if (selectedModule.config.x == -1.0f) selectedModule.config.x = 0.5f;
        if (selectedModule.config.y == -1.0f) selectedModule.config.y = 0.5f;
        selectedModule.config.x += dx / (float)width;
        selectedModule.config.y += dy / (float)height;
    }

    @Override
    public void onClose() {
        config.save();
        minecraft.gui.setScreen(parent);
    }
    
    private static class EditorModule {
        DamageEngineConfig.ModuleConfig config;
        ModuleType type;
        public EditorModule(DamageEngineConfig.ModuleConfig c, ModuleType t) { this.config = c; this.type = t; }
    }
    
    private enum ModuleType { TOTAL, RATING, INFO }
    
    private record ModuleSnapshot(float x, float y, float scale, boolean enabled) {
        public ModuleSnapshot(DamageEngineConfig.ModuleConfig c) { this(c.x, c.y, c.scale, c.enabled); }
        public void apply(DamageEngineConfig.ModuleConfig c) { c.x = x; c.y = y; c.scale = scale; c.enabled = enabled; }
    }
}
