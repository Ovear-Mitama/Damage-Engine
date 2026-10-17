package damage.engine.client.gui.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** 只读提示行。 */
public class InfoEntry extends OptionEntry {
    private final Component text;

    public InfoEntry(String key) {
        this(Component.translatable(key));
    }

    public InfoEntry(Component text) {
        this.text = text;
        setSearchText(text);
    }

    @Override protected boolean shouldHighlight() { return false; }

    @Override
    public void renderContent(GuiGraphics g, int idx, int y, int x, int ew, int eh,
                              int mx, int my, boolean hovered, float dt) {
        g.drawString(Minecraft.getInstance().font, text, x + 10, y + 8, 0xFFFFFFFF);
    }
}
