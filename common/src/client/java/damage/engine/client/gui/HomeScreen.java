package damage.engine.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import damage.engine.compat.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;

public class HomeScreen extends Screen {
    private final Screen parent;

    private DamageConfigScreen.StyledButton btn1, btn2;

    public HomeScreen(Screen parent) {
        super(new TranslatableComponent("title.damage-engine.home"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.clearWidgets();

        int buttonWidth = 100;
        int buttonHeight = 22;
        int buttonSpacing = 8;

        int totalButtonsWidth = buttonWidth * 2 + buttonSpacing;
        int leftX = this.width / 2 - totalButtonsWidth / 2;
        int buttonY = this.height / 2 + 30;

        btn1 = new DamageConfigScreen.StyledButton(leftX, buttonY, buttonWidth, buttonHeight,
            new TranslatableComponent("button.damage-engine.settings"),
            () -> this.minecraft.setScreen(new DamageConfigScreen(this)));

        btn2 = new DamageConfigScreen.StyledButton(leftX + buttonWidth + buttonSpacing, buttonY, buttonWidth, buttonHeight,
            new TranslatableComponent("button.damage-engine.config_management"),
            () -> this.minecraft.setScreen(new ProfileManagerScreen(this)));

        this.addRenderableWidget(btn1);
        this.addRenderableWidget(btn2);
    }

    @Override
    public void render(PoseStack pose, int mouseX, int mouseY, float delta) {
        GuiGraphics guiGraphics = GuiGraphics.of(pose);
        this.renderBackground(pose);

        int titleY = this.height / 4;

        int buttonWidth = 100;
        int buttonSpacing = 8;
        int totalButtonsWidth = buttonWidth * 2 + buttonSpacing;
        int titleLeftX = this.width / 2 - totalButtonsWidth / 2;

        guiGraphics.pose().pushPose();
        float titleScale = 2.0f;
        guiGraphics.pose().scale(titleScale, titleScale, 1.0f);
        String titleText = new TranslatableComponent("title.damage-engine.home").getString();
        int scaledX = (int)(titleLeftX / titleScale);
        int scaledY = (int)(titleY / titleScale);
        guiGraphics.drawString(this.font, titleText, scaledX, scaledY, 0xFFFFFFFF);
        guiGraphics.pose().popPose();

        String devText = new TranslatableComponent("text.damage-engine.developed_by").getString();
        guiGraphics.drawString(this.font, devText, titleLeftX, titleY + (int)(this.font.lineHeight * titleScale) + 6, 0xFFB5F0C6);

        super.render(pose, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (btn1 != null && btn1.mouseClicked(mouseX, mouseY, button)) return true;
            if (btn2 != null && btn2.mouseClicked(mouseX, mouseY, button)) return true;
        }
        return false;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
