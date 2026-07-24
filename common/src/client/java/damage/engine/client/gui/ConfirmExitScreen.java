package damage.engine.client.gui;

import damage.engine.DamageEngineConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Vanilla-style confirmation dialog for unsaved changes.
 */
public class ConfirmExitScreen extends Screen {
    private final Screen settingsScreen;
    private final Screen parent;
    private final DamageEngineConfig config;
    private final Minecraft mcl;

    public ConfirmExitScreen(Screen settingsScreen, Screen parent, DamageEngineConfig config) {
        super(Component.translatable("text.damage-engine.unsaved_changes_title"));
        this.settingsScreen = settingsScreen;
        this.parent = parent;
        this.config = config;
        this.mcl = Minecraft.getInstance();
    }

    @Override
    protected void init() {
        this.clearWidgets();
        int centerX = this.width / 2;
        int buttonY = this.height / 2 + 20;

        // Cancel button (left)
        this.addRenderableWidget(Button.builder(
            CommonComponents.GUI_CANCEL,
            btn -> mcl.setScreen(settingsScreen)
        ).pos(centerX - 105, buttonY).width(100).build());

        // Confirm button (right)
        this.addRenderableWidget(Button.builder(
            Component.translatable("gui.confirm"),
            btn -> {
                config.load();
                mcl.setScreen(parent);
            }
        ).pos(centerX + 5, buttonY).width(100).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        this.renderBackground(guiGraphics, mouseX, mouseY, delta);

        // Warning message
        String msg = Component.translatable("text.damage-engine.unsaved_changes").getString();
        guiGraphics.drawCenteredString(this.font, msg, this.width / 2, this.height / 2 - 25, 0xFFFFFFFF);

        // Render widgets manually (avoid super.render() blur)
        for (var child : this.children()) {
            if (child instanceof net.minecraft.client.gui.components.Renderable r) {
                r.render(guiGraphics, mouseX, mouseY, delta);
            }
        }
    }

    @Override
    public void onClose() {
        mcl.setScreen(settingsScreen);
    }
}
