package damage.engine.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import damage.engine.compat.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;

public class TextDrawable implements Renderable {
    private final String text;
    private final int x, y;
    private final int color;
    private final net.minecraft.client.gui.Font font;

    public TextDrawable(net.minecraft.client.gui.Font font, String text, int x, int y, int color) {
        this.font = font;
        this.text = text;
        this.x = x;
        this.y = y;
        this.color = color;
    }

    @Override
    public void render(PoseStack pose, int mouseX, int mouseY, float delta) {
        GuiGraphics guiGraphics = GuiGraphics.of(pose);
        guiGraphics.drawString(font, text, x, y, color);
    }
}
