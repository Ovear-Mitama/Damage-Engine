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
        // 先渲染父屏幕以在后面显示配置屏幕
        this.parent.render(context, -1, -1, delta);
        
        // 确保此屏幕绘制在父屏幕页脚按钮之上 (Z=200)
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 300); // 将 Z 索引提高到父级之上
        
        // 绘制深色透明背景以显示白色文本
        context.fill(0, 0, this.width, this.height, 0xAA000000);
        
        int x = config.hudX == -1.0f ? this.width / 2 : (int)(config.hudX * this.width);
        int y = config.hudY == -1.0f ? this.height / 2 + 20 : (int)(config.hudY * this.height);
        
        // 限制在屏幕范围内
        if (x < 0) x = 0;
        if (y < 0) y = 0;
        if (x > this.width) x = this.width - 50;
        if (y > this.height) y = this.height - 50;
        
        // 预览 HUD 内容
        new damage.engine.hud.DamageHud().renderPreview(context, x, y);
        
        // 围绕 HUD 绘制框（基于预览大小的近似值）
        
        int w = (int)(180 * config.hudScale); 
        int h = (int)(100 * config.hudScale);
        // 根据请求向右和向上移动 (+60, -70)
        // 以前的逻辑是 x - w/2 + 70, y - 10 - 80.
        // 用户更新："偏移太多，应该向左走，再向下走"。
        // 让我们尝试中间值：+35, -40?
        // 用户说："偏移太多，应该向左移动，然后向下移动"。
        // 让我们回到中心附近但保持一些偏移。
        // 尝试 +20, -20.
        int boxX = x - w / 2 + 20; 
        int boxY = y - 10 - 20; 
        
        context.drawBorder(boxX, boxY, w, h, 0xFFFFFFFF);
        // 深色背景上的白色文本
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
            // 匹配渲染逻辑
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
            
            // 保存为相对坐标
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
