package damage.engine.client.gui.config;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** 空白占位行。 */
public class SpacerEntry extends OptionEntry {
    public SpacerEntry(int h) {}

    @Override protected boolean shouldHighlight() { return false; }

    @Override
    public void renderContent(GuiGraphicsExtractor g, int idx, int y, int x, int ew, int eh,
                              int mx, int my, boolean hovered, float dt) {}
}
