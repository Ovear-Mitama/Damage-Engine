package damage.engine.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;

public class TextDrawable implements Drawable {
    private final String text;
    private final int x, y;
    private final int color;
    private final net.minecraft.client.font.TextRenderer textRenderer;

    public TextDrawable(net.minecraft.client.font.TextRenderer textRenderer, String text, int x, int y, int color) {
        this.textRenderer = textRenderer;
        this.text = text;
        this.x = x;
        this.y = y;
        this.color = color;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.drawTextWithShadow(textRenderer, text, x, y, color);
    }
}
