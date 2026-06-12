package damage.engine.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;

public class TextDrawable implements Renderable {
    private final String text;
    private final int x, y;
    private final int color;
    private final net.minecraft.client.gui.Font textRenderer;

    public TextDrawable(net.minecraft.client.gui.Font textRenderer, String text, int x, int y, int color) {
        this.textRenderer = textRenderer;
        this.text = text;
        this.x = x;
        this.y = y;
        this.color = color;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        context.drawString(textRenderer, text, x, y, color, true);
    }
}
