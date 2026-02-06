package damage.engine.client.gui;

import damage.engine.DamageEngineConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class HudEditorScreen extends Screen {
    private final Screen parent;
    private final DamageEngineConfig config;
    private boolean dragging = false;
    private int dragOffsetX, dragOffsetY;

    public HudEditorScreen(Screen parent) {
        super(Text.translatable("title.damage-engine.hud_editor"));
        this.parent = parent;
        this.config = DamageEngineConfig.getInstance();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Render parent screen first to show config screen behind
        this.parent.render(context, -1, -1, delta);
        
        // Ensure this screen draws ON TOP of the parent screen's footer buttons (Z=200)
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 300); // Bump Z-index above parent
        
        // Draw dark transparent background for visibility of white text
        context.fill(0, 0, this.width, this.height, 0xAA000000);
        
        int x = config.hudX == -1.0f ? this.width / 2 : (int)(config.hudX * this.width);
        int y = config.hudY == -1.0f ? this.height / 2 + 20 : (int)(config.hudY * this.height);
        
        // Clamp to screen bounds
        if (x < 0) x = 0;
        if (y < 0) y = 0;
        if (x > this.width) x = this.width - 50;
        if (y > this.height) y = this.height - 50;
        
        // Preview HUD content
        new damage.engine.hud.DamageHud().renderPreview(context, x, y);
        
        // Draw Box around HUD (Approximate based on preview size)
        
        int w = (int)(180 * config.hudScale); 
        int h = (int)(100 * config.hudScale);
        // Shift more to right and up as requested (+60, -70)
        // Previous logic was x - w/2 + 70, y - 10 - 80.
        // User update: "shifted too much, should go left and down".
        // Let's try halfway: +35, -40?
        // User said: "shifted too much, should move left, then move down".
        // Let's revert closer to center but keep some offset.
        // Try +20, -20.
        int boxX = x - w / 2 + 20; 
        int boxY = y - 10 - 20; 
        
        context.drawBorder(boxX, boxY, w, h, 0xFFFFFFFF);
        // White text on dark background
        Text dragText = Text.translatable("text.damage-engine.drag_to_move");
        context.drawText(this.textRenderer, dragText, boxX + w/2 - this.textRenderer.getWidth(dragText)/2, boxY - 15, 0xFFFFFFFF, false);
        
        Text saveText = Text.translatable("text.damage-engine.save_exit");
        context.drawText(this.textRenderer, saveText, this.width / 2 - this.textRenderer.getWidth(saveText)/2, 20, 0xFFFFFFFF, false);
        
        context.getMatrices().pop();
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int x = config.hudX == -1.0f ? this.width / 2 : (int)(config.hudX * this.width);
            int y = config.hudY == -1.0f ? this.height / 2 + 20 : (int)(config.hudY * this.height);
            
            int w = (int)(180 * config.hudScale);
            int h = (int)(100 * config.hudScale);
            // Match render logic
            int boxX = x - w / 2 + 20;
            int boxY = y - 10 - 20;
            
            if (mouseX >= boxX && mouseX <= boxX + w && mouseY >= boxY && mouseY <= boxY + h) {
                dragging = true;
                dragOffsetX = (int)mouseX - x;
                dragOffsetY = (int)mouseY - y;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (dragging) {
            int newX = (int)mouseX - dragOffsetX;
            int newY = (int)mouseY - dragOffsetY;
            
            // Save as relative
            config.hudX = (float)newX / (float)this.width;
            config.hudY = (float)newY / (float)this.height;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }
    
    @Override
    public void close() {
        config.save();
        this.client.setScreen(parent);
    }
}
