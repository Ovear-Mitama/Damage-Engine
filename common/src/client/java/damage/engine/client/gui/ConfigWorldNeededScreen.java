package damage.engine.client.gui;

import anima.api.AnimaApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 「动画编辑器需要专用配置世界」提示页。
 * <p>
 * 编辑器里的世界预览（真 3D 文字、粒子、坐标系）只在 Anima 的专用配置世界中渲染，而那个世界
 * 只能从主菜单进（{@code ConfigWorldLauncher.launch} 在已有世界内是空操作）。所以门槛由 DE 自己写：
 * 确认后调 {@link AnimaApi#enterConfigWorld(Screen)} 进去，进去之后再打开一次配置界面就能用上世界预览。
 */
public class ConfigWorldNeededScreen extends Screen {
    private final Screen settingsScreen;
    private final Minecraft mcl = Minecraft.getInstance();

    public ConfigWorldNeededScreen(Screen settingsScreen) {
        super(Component.translatable("text.damage-engine.config_world_title"));
        this.settingsScreen = settingsScreen;
    }

    @Override
    protected void init() {
        this.clearWidgets();
        int centerX = this.width / 2;
        int buttonY = this.height / 2 + 20;

        // Cancel (left) — back to the settings screen
        this.addRenderableWidget(new DamageConfigScreen.StyledButton(centerX - 105, buttonY, 100, 20,
            Component.translatable("gui.cancel").withColor(0xFFFC887E),
            () -> mcl.setScreen(settingsScreen)));

        // Confirm (right) — enter the dedicated preview world
        this.addRenderableWidget(new DamageConfigScreen.StyledButton(centerX + 5, buttonY, 100, 20,
            Component.translatable("button.damage-engine.enter_config_world").withColor(0xFFB7F3C8),
            () -> AnimaApi.enterConfigWorld(settingsScreen)));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float delta) {
        // 1.21.11: in-game background blur is applied once by the render pipeline;
        // calling renderBackground() here would blur twice and crash.
        if (this.minecraft != null && this.minecraft.level == null) {
            this.extractPanorama(guiGraphics, delta);
        }

        // Hint text, wrapped to 300px and centred
        var lines = this.font.split(Component.translatable("text.damage-engine.config_world_needed"), 300);
        int y = this.height / 2 - 45;
        for (var line : lines) {
            guiGraphics.text(this.font, line, this.width / 2 - this.font.width(line) / 2, y, 0xFFFFFFFF);
            y += 12;
        }

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
