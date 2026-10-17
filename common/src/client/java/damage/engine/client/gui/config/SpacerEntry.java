package damage.engine.client.gui.config;

import net.minecraft.client.gui.GuiGraphics;

/** 空白占位行。 */
public class SpacerEntry extends OptionEntry {
    public SpacerEntry(int h) {}

    @Override protected boolean shouldHighlight() { return false; }

    @Override
    public void renderContent(GuiGraphics g, int idx, int y, int x, int ew, int eh,
                              int mx, int my, boolean hovered, float dt) {}
}
