package damage.engine.client.gui;

import damage.engine.DamageEngineConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Custom-styled confirmation dialog for unsaved changes.
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

        // Cancel button (left) - returns to settings
        this.addRenderableWidget(new DamageConfigScreen.StyledButton(centerX - 105, buttonY, 100, 20,
            Component.translatable("gui.cancel").withColor(0xFFFC887E),
            () -> mcl.setScreen(settingsScreen)));

        // Confirm button (right) - discards changes and exits
        this.addRenderableWidget(new DamageConfigScreen.StyledButton(centerX + 5, buttonY, 100, 20,
            Component.translatable("gui.confirm").withColor(0xFFB7F3C8),
            () -> {
                config.load();
                mcl.setScreen(parent);
            }));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float delta) {
        // 1.21.11: in-game background blur is applied once by the render pipeline;
        // calling renderBackground() here would blur twice and crash.
        if (this.minecraft != null && this.minecraft.level == null) {
            this.extractPanorama(guiGraphics, delta);
        }

        // Warning message
        String msg = Component.translatable("text.damage-engine.unsaved_changes").getString();
        guiGraphics.centeredText(this.font, msg, this.width / 2, this.height / 2 - 25, 0xFFFFFFFF);

        // Render widgets manually (avoid super.render() blur)
        for (var child : this.children()) {
            if (child instanceof net.minecraft.client.gui.components.Renderable r) {
                r.extractRenderState(guiGraphics, mouseX, mouseY, delta);
            }
        }
    }

    @Override
    public void onClose() {
        mcl.setScreen(settingsScreen);
    }
}
