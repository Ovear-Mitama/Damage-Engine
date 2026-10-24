package damage.engine;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** 模组首页：标题、作者信息与「设置 / 配置管理」入口。 */
public class GuiDamageHome extends GuiScreen {
    private final GuiScreen parent;
    private final List<Btn> buttons = new ArrayList<>();
    private int buttonsLeft;

    public GuiDamageHome(GuiScreen parent) {
        this.parent = parent;
    }

    private static final class Btn {
        final int x;
        final int y;
        final int w;
        final int h;
        final String label;
        final int color;
        final Runnable action;

        Btn(int x, int y, int w, int h, String label, int color, Runnable action) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.label = label;
            this.color = color;
            this.action = action;
        }

        boolean hovered(int mouseX, int mouseY) {
            return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        }
    }

    @Override
    public void initGui() {
        int buttonWidth = 100;
        int buttonHeight = 22;
        int spacing = 8;
        int total = buttonWidth * 2 + spacing;
        buttonsLeft = width / 2 - total / 2;
        int buttonY = height / 2 + 30;

        buttons.clear();
        buttons.add(new Btn(buttonsLeft, buttonY, buttonWidth, buttonHeight,
            I18n.format("button.damage-engine.settings"), 0xFFFFFFFF,
            () -> mc.displayGuiScreen(new GuiDamageConfig(this))));
        buttons.add(new Btn(buttonsLeft + buttonWidth + spacing, buttonY,
            buttonWidth, buttonHeight,
            I18n.format("button.damage-engine.config_management"), 0xFFFFFFFF,
            () -> mc.displayGuiScreen(new GuiProfileManager(this))));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        drawDefaultBackground();
        Gui.drawRect(0, 0, width, height, 0x50000000);

        int titleY = height / 4;
        GlStateManager.pushMatrix();
        GlStateManager.scale(2.0f, 2.0f, 1.0f);
        fontRenderer.drawStringWithShadow(I18n.format("title.damage-engine.home"),
            buttonsLeft / 2.0f, titleY / 2.0f, 0xFFFFFFFF);
        GlStateManager.popMatrix();

        fontRenderer.drawStringWithShadow(I18n.format("text.damage-engine.developed_by"),
            buttonsLeft, titleY + 24, 0xFFB5F0C6);

        for (Btn button : buttons) {
            drawButton(button, mouseX, mouseY);
        }

        String version = "Damage Engine " + DamageEngine.VERSION;
        fontRenderer.drawStringWithShadow(version,
            (width - fontRenderer.getStringWidth(version)) / 2.0f, height - 24, 0xFFA0A0A0);
    }

    private void drawButton(Btn button, int mouseX, int mouseY) {
        boolean hovered = button.hovered(mouseX, mouseY);
        Gui.drawRect(button.x, button.y, button.x + button.w, button.y + button.h, 0x20000000);
        int border = hovered ? 0xFFFFFFFF : 0xFFA0A0A0;
        Gui.drawRect(button.x, button.y, button.x + button.w, button.y + 1, border);
        Gui.drawRect(button.x, button.y + button.h - 1, button.x + button.w, button.y + button.h, border);
        Gui.drawRect(button.x, button.y, button.x + 1, button.y + button.h, border);
        Gui.drawRect(button.x + button.w - 1, button.y, button.x + button.w, button.y + button.h, border);
        fontRenderer.drawStringWithShadow(button.label,
            button.x + (button.w - fontRenderer.getStringWidth(button.label)) / 2.0f,
            button.y + (button.h - 8) / 2.0f, button.color);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton != 0) return;
        for (Btn button : buttons) {
            if (button.hovered(mouseX, mouseY)) {
                button.action.run();
                return;
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(parent);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
