package damage.engine.client.gui;

import damage.engine.DamageEngineConfig;
import damage.engine.hud.DamageHud;
import damage.engine.hud.DamageSessionManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

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
    
    private ButtonWidget undoBtn;
    private ButtonWidget redoBtn;
    private ButtonWidget resetBtn;

    public HudEditorScreen(Screen parent) {
        super(Text.translatable("title.damage-engine.hud_editor"));
        this.parent = parent;
        this.config = DamageEngineConfig.getInstance();
        this.damageHud = new DamageHud();
        
        modules.add(new EditorModule(config.totalDamageConfig, ModuleType.TOTAL));
        modules.add(new EditorModule(config.historyConfig, ModuleType.HISTORY));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
    
    @Override
    protected void init() {
        int btnY = 10;
        int btnX = 10;
        int spacing = 5;
        
        undoBtn = ButtonWidget.builder(Text.translatable("hud.editor.undo"), b -> undo())
            .dimensions(btnX, btnY, 40, 20).build();
        
        redoBtn = ButtonWidget.builder(Text.translatable("hud.editor.redo"), b -> redo())
            .dimensions(btnX + 40 + spacing, btnY, 40, 20).build();
            
        resetBtn = ButtonWidget.builder(Text.translatable("hud.editor.reset").withColor(0xFFFC887E), b -> handleResetClick())
            .dimensions(btnX + 40 + spacing + 40 + spacing, btnY, 40, 20).build();
            
        this.addDrawableChild(undoBtn);
        this.addDrawableChild(redoBtn);
        this.addDrawableChild(resetBtn);
        
        updateButtons();
    }

    private void handleResetClick() {
        if (resetButtonState == 0) {
            resetButtonState = 1;
            resetBtn.setMessage(Text.translatable("gui.confirm").append("?").withColor(0xFFFC887E));
            resetButtonActionTime = System.currentTimeMillis();
        } else if (resetButtonState == 1) {
            resetButtonState = 2;
            resetButtonActionTime = System.currentTimeMillis();
            resetAllModules();
            resetBtn.setMessage(Text.translatable("text.damage-engine.reset_done").withColor(0xFFB5F0C6));
        }
    }
    
    private void updateButtons() {
        undoBtn.active = !undoStack.isEmpty();
        redoBtn.active = !redoStack.isEmpty();
        
        if (resetButtonState == 2) {
            if (System.currentTimeMillis() - resetButtonActionTime > 3000) {
                resetButtonState = 0;
                resetBtn.setMessage(Text.translatable("hud.editor.reset").withColor(0xFFFC887E));
            }
        } else if (resetButtonState == 1) {
             if (System.currentTimeMillis() - resetButtonActionTime > 5000) {
                 resetButtonState = 0;
                 resetBtn.setMessage(Text.translatable("hud.editor.reset").withColor(0xFFFC887E));
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
        
        modules.get(0).config.x = 0.8177083f;
        modules.get(0).config.y = 0.26013651f;
        modules.get(0).config.scale = 0.95652175f;
        
        config.comboConfig.x = 0.8828125f;
        config.comboConfig.y = 0.2611276f;
        config.comboConfig.scale = 0.95652175f;
        
        modules.get(1).config.x = 0.9364584f;
        modules.get(1).config.y = 0.2611276f;
        modules.get(1).config.scale = 0.95652175f;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (this.client.world == null) {
            this.renderBackground(context, mouseX, mouseY, delta);
        }
        
        for (EditorModule m : modules) {
            renderModule(context, m);
        }
        
        if (selectedModule != null) {
            drawSelection(context, selectedModule);
        }
        
        for (net.minecraft.client.gui.Element element : this.children()) {
            if (element instanceof net.minecraft.client.gui.Drawable) {
                ((net.minecraft.client.gui.Drawable) element).render(context, mouseX, mouseY, delta);
            }
        }
    }
    
    private void renderModule(DrawContext context, EditorModule m) {
        damageHud.renderModule(context, m.config, this.client, 1.0f, () -> {
            switch (m.type) {
                case TOTAL:
                    damageHud.renderTotalDamage(context, 12.5f, 0.7f, true, 1.0f, 5, this.client);
                    break;
                case COMBO:
                    break;
                case HISTORY:
                    List<DamageSessionManager.DamageEntry> history = List.of(
                        new DamageSessionManager.DamageEntry(2.5f, false, 0),
                        new DamageSessionManager.DamageEntry(2.5f, false, 0),
                        new DamageSessionManager.DamageEntry(2.5f, false, 0),
                        new DamageSessionManager.DamageEntry(2.5f, false, 0),
                        new DamageSessionManager.DamageEntry(2.5f, false, 0)
                    );
                    damageHud.renderHistory(context, history, true, 1.0f, this.client);
                    break;
            }
        });
    }
    
    private void drawSelection(DrawContext context, EditorModule m) {
        int[] b = getBounds(m);
        int greenColor = 0xFFB5F0C6;
        
        drawBorder(context, b[0], b[1], b[2], b[3], greenColor);
        
        int handleSize = 8;
        int hx = b[0] + b[2] - handleSize;
        int hy = b[1] + b[3] - handleSize;
        context.fill(hx, hy, hx + handleSize, hy + handleSize, greenColor);
    }
    
    private void drawBorder(DrawContext context, int x, int y, int width, int height, int color) {
        context.fill(x, y, x + width, y + 1, color);
        context.fill(x, y + height - 1, x + width, y + height, color);
        context.fill(x, y + 1, x + 1, y + height - 1, color);
        context.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }
    
    private int[] getBounds(EditorModule m) {
        int cx = m.config.x == -1.0f ? width / 2 : (int)(m.config.x * width);
        int cy = m.config.y == -1.0f ? height / 2 : (int)(m.config.y * height);
        float s = m.config.scale;
        
        int w = 0, h = 0;
        int ox = 0, oy = 0;
        
        switch (m.type) {
            case TOTAL:
                w = (int)(100 * s); h = (int)(40 * s);
                
                ox = -w/2 + 2;
                oy = -h/2 + 10;
                break;
            case COMBO:
                w = (int)(50 * s); h = (int)(20 * s);
                ox = -w/2; oy = 0;
                break;
            case HISTORY:
                w = (int)(60 * s); h = (int)(10 * config.historyLimit * s);
                ox = -w/2; oy = 0;
                break;
        }
        return new int[]{cx + ox - 2, cy + oy - 2, w + 4, h + 4};
    }
    
    private boolean isOverHandle(double mx, double my, EditorModule m) {
        int[] b = getBounds(m);
        int handleSize = 8;
        int hx = b[0] + b[2] - handleSize;
        int hy = b[1] + b[3] - handleSize;
        return mx >= hx && mx <= hx + handleSize && my >= hy && my <= hy + handleSize;
    }
    
    private boolean isOverModule(double mx, double my, EditorModule m) {
        int[] b = getBounds(m);
        return mx >= b[0] && mx <= b[0] + b[2] && my >= b[1] && my <= b[1] + b[3];
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            this.setFocused(null);
            return true;
        }
        
        this.setFocused(null);
        
        if (button == 0) {
            if (selectedModule != null && isOverHandle(mouseX, mouseY, selectedModule)) {
                saveStateForUndo();
                resizing = true;
                dragOffsetX = (float)mouseX;
                dragOffsetY = (float)mouseY;
                return true;
            }
            
            for (EditorModule m : modules) {
                if (isOverModule(mouseX, mouseY, m)) {
                    if (selectedModule != m) {
                         selectedModule = m;
                         updateButtons();
                    }
                    saveStateForUndo();
                    dragging = true;
                    int cx = m.config.x == -1.0f ? width / 2 : (int)(m.config.x * width);
                    int cy = m.config.y == -1.0f ? height / 2 : (int)(m.config.y * height);
                    dragOffsetX = (float)mouseX - cx;
                    dragOffsetY = (float)mouseY - cy;
                    return true;
                }
            }
            
            selectedModule = null;
            updateButtons();
        }
        return false;
    }
    
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        resizing = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (resizing && selectedModule != null) {
            float sensitivity = 0.01f;
            selectedModule.config.scale += (deltaX + deltaY) * sensitivity;
            if (selectedModule.config.scale < 0.1f) selectedModule.config.scale = 0.1f;
            if (selectedModule.config.scale > 5.0f) selectedModule.config.scale = 5.0f;
            return true;
        }
        
        if (dragging && selectedModule != null) {
            float newX = ((float)mouseX - dragOffsetX) / width;
            float newY = ((float)mouseY - dragOffsetY) / height;
            selectedModule.config.x = newX;
            selectedModule.config.y = newY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }
    
    @Override
    public void close() {
        config.save();
        client.setScreen(parent);
    }
    
    private static class EditorModule {
        DamageEngineConfig.ModuleConfig config;
        ModuleType type;
        public EditorModule(DamageEngineConfig.ModuleConfig c, ModuleType t) { this.config = c; this.type = t; }
    }
    
    private enum ModuleType { TOTAL, COMBO, HISTORY }
    
    private record ModuleSnapshot(float x, float y, float scale, boolean enabled) {
        public ModuleSnapshot(DamageEngineConfig.ModuleConfig c) { this(c.x, c.y, c.scale, c.enabled); }
        public void apply(DamageEngineConfig.ModuleConfig c) { c.x = x; c.y = y; c.scale = scale; c.enabled = enabled; }
    }
}
