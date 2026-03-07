package damage.engine.client.gui;

import damage.engine.DamageEngineConfig;
import damage.engine.hud.DamageHud;
import damage.engine.hud.DamageSessionManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

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
    
    private DamageConfigScreen.StyledButton undoBtn;
    private DamageConfigScreen.StyledButton redoBtn;
    private DamageConfigScreen.StyledButton resetBtn;

    private static long handCursor = 0;
    private static long arrowCursor = 0;
    private static long moveCursor = 0;
    private static long resizeCursor = 0;
    private static long diagResizeCursor = 0;

    public HudEditorScreen(Screen parent) {
        super(Text.translatable("title.damage-engine.hud_editor"));
        this.parent = parent;
        this.config = DamageEngineConfig.getInstance();
        this.damageHud = new DamageHud();
        
        modules.add(new EditorModule(config.totalDamageConfig, ModuleType.TOTAL));
        modules.add(new EditorModule(config.historyConfig, ModuleType.HISTORY));
    }
    
    public void playClickSound() {
        try {
             net.minecraft.client.sound.PositionedSoundInstance sound = net.minecraft.client.sound.PositionedSoundInstance.ambient(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK.value());
             
             try {
                 java.lang.reflect.Field volField = net.minecraft.client.sound.AbstractSoundInstance.class.getDeclaredField("volume");
                 volField.setAccessible(true);
                 volField.setFloat(sound, 0.25F);
             } catch (Exception ignored) {}
             
             MinecraftClient.getInstance().getSoundManager().play(sound);
        } catch (Exception e) {
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
    
    @Override
    protected void init() {
        if (handCursor == 0) handCursor = GLFW.glfwCreateStandardCursor(GLFW.GLFW_HAND_CURSOR);
        if (arrowCursor == 0) arrowCursor = GLFW.glfwCreateStandardCursor(GLFW.GLFW_ARROW_CURSOR);
        
        if (moveCursor == 0) {
             moveCursor = GLFW.glfwCreateStandardCursor(0x00036009); 
             if (moveCursor == 0) moveCursor = GLFW.glfwCreateStandardCursor(GLFW.GLFW_CROSSHAIR_CURSOR);
        }
        
        if (resizeCursor == 0) resizeCursor = GLFW.glfwCreateStandardCursor(GLFW.GLFW_HRESIZE_CURSOR); 
        
        if (diagResizeCursor == 0) {
            diagResizeCursor = GLFW.glfwCreateStandardCursor(0x00036007); 
            if (diagResizeCursor == 0) diagResizeCursor = GLFW.glfwCreateStandardCursor(GLFW.GLFW_HAND_CURSOR);
        }

        int btnY = 10;
        int btnX = 10;
        int spacing = 5;
        
        undoBtn = new DamageConfigScreen.StyledButton(btnX, btnY, 40, 20, Text.translatable("hud.editor.undo"), this::undo);
        
        redoBtn = new DamageConfigScreen.StyledButton(btnX + 40 + spacing, btnY, 40, 20, Text.translatable("hud.editor.redo"), this::redo);
            
        resetBtn = new DamageConfigScreen.StyledButton(btnX + 40 + spacing + 40 + spacing, btnY, 40, 20, Text.translatable("hud.editor.reset").withColor(0xFFFC887E), this::handleResetClick);
            
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
        
        modules.get(0).config.x = 0.8203125f;
        modules.get(0).config.y = 0.3602415f;
        modules.get(0).config.scale = 0.95652175f;
        
        config.comboConfig.x = 0.8828125f;
        config.comboConfig.y = 0.2611276f;
        config.comboConfig.scale = 0.95652175f;
        
        modules.get(1).config.x = 0.9359375f;
        modules.get(1).config.y = 0.3305973f;
        modules.get(1).config.scale = 0.95652175f;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (this.client.world == null) {
            context.fill(0, 0, this.width, this.height, 0xC0101010);
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
        
        updateCursor(mouseX, mouseY);
    }
    
    private boolean isMouseOverButton(net.minecraft.client.gui.widget.ClickableWidget button, double mouseX, double mouseY) {
        return button.visible && mouseX >= button.getX() && mouseY >= button.getY() && mouseX < button.getX() + button.getWidth() && mouseY < button.getHeight() + button.getY();
    }
    
    private void updateCursor(double mouseX, double mouseY) {
        if (client == null) return;
        long windowHandle = client.getWindow().getHandle();
        long cursor = arrowCursor;
        
        if (isMouseOverButton(undoBtn, mouseX, mouseY) || isMouseOverButton(redoBtn, mouseX, mouseY) || isMouseOverButton(resetBtn, mouseX, mouseY)) {
            cursor = handCursor;
        } else {
            if (selectedModule != null && isOverHandle(mouseX, mouseY, selectedModule)) {
                cursor = diagResizeCursor;
            } else {
                boolean foundModule = false;
                for (EditorModule m : modules) {
                    if (isOverModule(mouseX, mouseY, m)) {
                        if (m == selectedModule) {
                            cursor = moveCursor;
                        } else {
                            cursor = handCursor;
                        }
                        foundModule = true;
                        break;
                    }
                }
            }
        }
        
        GLFW.glfwSetCursor(windowHandle, cursor);
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
                
                ox = (int)(-w/2 + 2 * s);
                oy = (int)(-h/2 + 10 * s);
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
    public boolean mouseClicked(Click click, boolean modifiers) {
        if (super.mouseClicked(click, modifiers)) return true;
        
        double mouseX = client.mouse.getX() * (double)client.getWindow().getScaledWidth() / (double)client.getWindow().getWidth();
        double mouseY = client.mouse.getY() * (double)client.getWindow().getScaledHeight() / (double)client.getWindow().getHeight();
        int button = 0; 
        
        this.setFocused(null);
        
        if (button == 0) {
            double mx = client.mouse.getX() * (double)client.getWindow().getScaledWidth() / (double)client.getWindow().getWidth();
            double my = client.mouse.getY() * (double)client.getWindow().getScaledHeight() / (double)client.getWindow().getHeight();
            
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
    public boolean mouseReleased(Click click) {
        if (super.mouseReleased(click)) return true;
        
        dragging = false;
        resizing = false;
        return false;
    }
    
    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (resizing && selectedModule != null) {
            float sensitivity = 0.01f;
            selectedModule.config.scale += (float)(deltaX + deltaY) * sensitivity; 
            if (selectedModule.config.scale < 0.1f) selectedModule.config.scale = 0.1f;
            if (selectedModule.config.scale > 5.0f) selectedModule.config.scale = 5.0f;
            return true;
        }
        
        if (dragging && selectedModule != null) {
            double mouseX = client.mouse.getX() * (double)client.getWindow().getScaledWidth() / (double)client.getWindow().getWidth();
            double mouseY = client.mouse.getY() * (double)client.getWindow().getScaledHeight() / (double)client.getWindow().getHeight();
            
            float newX = ((float)mouseX - dragOffsetX) / (float)width;
            float newY = ((float)mouseY - dragOffsetY) / (float)height;
            
            selectedModule.config.x = newX;
            selectedModule.config.y = newY;
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY); 
    }
    
    @Override
    public void close() {
        GLFW.glfwSetCursor(client.getWindow().getHandle(), arrowCursor);
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
