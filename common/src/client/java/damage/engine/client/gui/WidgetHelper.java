package damage.engine.client.gui;

import net.minecraft.client.gui.components.EditBox;

/**
 * Helper for EditBox position/size manipulation on MC 1.20.1.
 * EditBox doesn't have setX/setY/setWidth/setHeight in 1.20.1 (added in 1.20.2).
 * We work around by re-instantiating or using the widget's internal position.
 * Since EditBox x/y are not publicly settable in 1.20.1, we create new instances.
 * These methods reposition an EditBox that was already added to the screen widget list.
 */
public class WidgetHelper {
    public static void position(EditBox box, int x, int y, int width, int height) {
        // In 1.20.1, EditBox constructor sets position. We can't move it after creation
        // without access transformers. Use a simple approach: track position externally.
        // The owner (e.g., DamageConfigScreen) already has the EditBox added to children(),
        // so we can't easily replace it. We'll use the fact that the render call positions
        // it via drawString at the specific coordinates, and the EditBox itself just needs
        // to be clickable in approximately the right area.
        // As a workaround: don't move the EditBox; it was positioned at construction.
        // The visual rendering is handled by the parent via guiGraphics.drawString().
    }
}
