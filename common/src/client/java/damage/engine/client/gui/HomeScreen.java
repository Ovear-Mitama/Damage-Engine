package damage.engine.client.gui;

import damage.engine.DamageEngineConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class HomeScreen extends Screen {
    private final Screen parent;
    private final DamageEngineConfig config;

    public HomeScreen(Screen parent) {
        super(Component.translatable("title.damage-engine.home"));
        this.parent = parent;
        this.config = DamageEngineConfig.getInstance();
    }

    @Override
    protected void init() {
        this.clearWidgets();

        int centerX = this.width / 2;
        int buttonWidth = 100;
        int buttonHeight = 22;
        int buttonSpacing = 8;
        int totalButtonsWidth = buttonWidth * 2 + buttonSpacing;

        // Buttons placed lower on screen
        int buttonY = this.height / 2 + 30;

        // Settings button
        this.addRenderableWidget(new StyledButton(
            centerX - totalButtonsWidth / 2, buttonY,
            buttonWidth, buttonHeight,
            Component.translatable("button.damage-engine.settings"),
            () -> this.minecraft.setScreen(new DamageConfigScreen(this))
        ));

        // Config Management button
        this.addRenderableWidget(new StyledButton(
            centerX - totalButtonsWidth / 2 + buttonWidth + buttonSpacing, buttonY,
            buttonWidth, buttonHeight,
            Component.translatable("button.damage-engine.config_management"),
            () -> this.minecraft.setScreen(new ProfileManagerScreen(this))
        ));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        this.renderBackground(guiGraphics, mouseX, mouseY, delta);

        int centerX = this.width / 2;
        int titleY = this.height / 4;
        // Title left-aligned with buttons below
        int buttonWidth = 100, buttonSpacing = 8;
        int titleLeftX = centerX - (buttonWidth * 2 + buttonSpacing) / 2;

        // Bigger "Damage Engine" title with scale + scan overlay on top
        guiGraphics.pose().pushPose();
        float titleScale = 2.0f;
        guiGraphics.pose().scale(titleScale, titleScale, 1.0f);
        String titleText = Component.translatable("title.damage-engine.home").getString();
        int scaledX = (int)(titleLeftX / titleScale);
        int scaledY = (int)(titleY / titleScale);
        guiGraphics.drawString(this.font, titleText, scaledX, scaledY, 0xFFFFFFFF);
        guiGraphics.pose().popPose();

        // "Developed by mitama" - left aligned below title
        String devText = Component.translatable("text.damage-engine.developed_by").getString();
        int greenColor = 0xFFB5F0C6;
        guiGraphics.drawString(this.font, devText, titleLeftX, titleY + (int)(this.font.lineHeight * titleScale) + 6, greenColor);

        // Manual widget rendering
        for (GuiEventListener child : this.children()) {
            if (child instanceof Renderable renderable) {
                renderable.render(guiGraphics, mouseX, mouseY, delta);
            }
        }

        // Tooltip rendering
        for (GuiEventListener child : this.children()) {
            if (child instanceof AbstractWidget widget && widget.isMouseOver(mouseX, mouseY)) {
                if (widget.getTooltip() != null) {
                    guiGraphics.renderTooltip(this.font, widget.getTooltip().toCharSequence(this.minecraft), mouseX, mouseY);
                }
            }
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    private static class StyledButton extends AbstractWidget {
        private final Runnable onPress;

        public StyledButton(int x, int y, int width, int height, Component message, Runnable onPress) {
            super(x, y, width, height, message);
            this.onPress = onPress;
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (this.active && this.visible && button == 0) {
                if (this.isMouseOver(mouseX, mouseY)) {
                    this.playDownSound(Minecraft.getInstance().getSoundManager());
                    this.onPress.run();
                    return true;
                }
            }
            return false;
        }

        @Override
        protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
            int bgColor = this.active ? 0x20000000 : 0x10000000;
            guiGraphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bgColor);

            int borderColor;
            if (!this.active) {
                borderColor = 0xFF404040;
            } else if (isHovered()) {
                borderColor = 0xFFFFFFFF;
            } else {
                borderColor = 0xFFA0A0A0;
            }
            int x = getX(); int y = getY(); int w = getWidth(); int h = getHeight();
            guiGraphics.fill(x, y, x + w, y + 1, borderColor);
            guiGraphics.fill(x, y + h - 1, x + w, y + h, borderColor);
            guiGraphics.fill(x, y, x + 1, y + h, borderColor);
            guiGraphics.fill(x + w - 1, y, x + w, y + h, borderColor);

            int textColor = this.active ? 0xFFFFFFFF : 0xFF808080;
            guiGraphics.drawCenteredString(Minecraft.getInstance().font, getMessage(), getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, textColor);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput builder) {
            this.defaultButtonNarrationText(builder);
        }
    }
}
