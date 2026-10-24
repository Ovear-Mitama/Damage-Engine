package damage.engine;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** 配置管理：在多个配置档案之间切换。 */
public class GuiProfileManager extends GuiScreen {
    private static final int ENTRY_H = 22;
    private static final int ENTRY_GAP = 2;

    private final GuiScreen parent;
    private final List<String> profiles = new ArrayList<>();
    private String selectedProfile;
    private int listTop;
    private int listBottom;
    private int listWidth;
    private int listX;
    private int scrollPx;

    public GuiProfileManager(GuiScreen parent) {
        this.parent = parent;
        this.selectedProfile = DamageEngineConfig.getCurrentProfile();
    }

    @Override
    public void initGui() {
        profiles.clear();
        profiles.addAll(DamageEngineConfig.getProfileNames());
        if (selectedProfile == null || !profiles.contains(selectedProfile)) {
            selectedProfile = DamageEngineConfig.getCurrentProfile();
        }

        listWidth = 220;
        listX = width / 2 - listWidth / 2;
        listTop = 42;
        listBottom = height - 45;
        scrollPx = Math.max(0, Math.min(scrollPx, maxScroll()));
    }

    private int contentHeight() {
        return profiles.size() * (ENTRY_H + ENTRY_GAP);
    }

    private int maxScroll() {
        return Math.max(0, contentHeight() - (listBottom - listTop));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        drawDefaultBackground();
        Gui.drawRect(0, 0, width, height, 0x50000000);
        drawCenteredString(fontRenderer, I18n.format("title.damage-engine.profile_manager"),
            width / 2, 16, 0xFFFFFFFF);

        int factor = new net.minecraft.client.gui.ScaledResolution(mc).getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(listX * factor, mc.displayHeight - listBottom * factor,
            listWidth * factor, (listBottom - listTop) * factor);

        int y = listTop - scrollPx;
        for (String profile : profiles) {
            boolean hovered = mouseX >= listX && mouseX <= listX + listWidth
                && mouseY >= Math.max(y, listTop) && mouseY <= Math.min(y + ENTRY_H, listBottom);
            boolean selected = profile.equals(selectedProfile);

            Gui.drawRect(listX, y, listX + listWidth, y + ENTRY_H,
                hovered ? 0x30FFFFFF : 0x10000000);
            if (selected) {
                Gui.drawRect(listX, y, listX + listWidth, y + 1, 0xFFB5F0C6);
                Gui.drawRect(listX, y + ENTRY_H - 1, listX + listWidth, y + ENTRY_H, 0xFFB5F0C6);
                Gui.drawRect(listX, y, listX + 1, y + ENTRY_H, 0xFFB5F0C6);
                Gui.drawRect(listX + listWidth - 1, y, listX + listWidth, y + ENTRY_H, 0xFFB5F0C6);
            }
            fontRenderer.drawStringWithShadow(profile, listX + 10, y + (ENTRY_H - 8) / 2,
                selected ? 0xFFB5F0C6 : 0xFFFFFFFF);
            if (selected) {
                fontRenderer.drawStringWithShadow("\u2713",
                    listX + listWidth - 18, y + (ENTRY_H - 8) / 2, 0xFFB5F0C6);
            }
            y += ENTRY_H + ENTRY_GAP;
        }
        GL11.glDisable(GL11.GL_SCISSOR_TEST);

        if (contentHeight() > listBottom - listTop) {
            int trackX = listX + listWidth + 4;
            Gui.drawRect(trackX, listTop, trackX + 3, listBottom, 0x33FFFFFF);
            int listH = listBottom - listTop;
            int thumbH = Math.max(20, listH * listH / contentHeight());
            int thumbY = listTop + (listH - thumbH) * scrollPx / Math.max(1, maxScroll());
            Gui.drawRect(trackX, thumbY, trackX + 3, thumbY + thumbH, 0x99B5F0C6);
        }

        drawFooterButton(mouseX, mouseY, doneX(), doneY(), doneW(), 20,
            I18n.format("gui.done"), 0xFFB5F0C6);
        drawFooterButton(mouseX, mouseY, folderX(), doneY(), folderW(), 20,
            I18n.format("button.damage-engine.open_config_folder"), 0xFFFFFFFF);

        String current = I18n.format("title.damage-engine.profile_manager") + ": " + selectedProfile;
        fontRenderer.drawStringWithShadow(current, 10, height - 20, 0xFFA0A0A0);
    }

    private int doneW() {
        return 80;
    }

    private int doneX() {
        return width - doneW() - 10;
    }

    private int doneY() {
        return height - 35;
    }

    private int folderW() {
        return 100;
    }

    private int folderX() {
        return doneX() - folderW() - 8;
    }

    private void drawFooterButton(int mouseX, int mouseY, int x, int y, int w, int h,
                                  String label, int color) {
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        Gui.drawRect(x, y, x + w, y + h, 0x20000000);
        int border = hovered ? 0xFFFFFFFF : 0xFFA0A0A0;
        Gui.drawRect(x, y, x + w, y + 1, border);
        Gui.drawRect(x, y + h - 1, x + w, y + h, border);
        Gui.drawRect(x, y, x + 1, y + h, border);
        Gui.drawRect(x + w - 1, y, x + w, y + h, border);
        fontRenderer.drawStringWithShadow(label,
            x + (w - fontRenderer.getStringWidth(label)) / 2.0f,
            y + (h - 8) / 2.0f, color);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton != 0) return;

        if (mouseX >= doneX() && mouseX <= doneX() + doneW()
            && mouseY >= doneY() && mouseY <= doneY() + 20) {
            applyAndClose();
            return;
        }
        if (mouseX >= folderX() && mouseX <= folderX() + folderW()
            && mouseY >= doneY() && mouseY <= doneY() + 20) {
            openConfigFolder();
            return;
        }

        if (mouseY < listTop || mouseY > listBottom) return;
        if (mouseX < listX || mouseX > listX + listWidth) return;

        int index = (mouseY - listTop + scrollPx) / (ENTRY_H + ENTRY_GAP);
        if (index >= 0 && index < profiles.size()) {
            selectedProfile = profiles.get(index);
        }
    }

    private void applyAndClose() {
        DamageEngineConfig config = DamageEngineConfig.getInstance();
        if (selectedProfile != null && !selectedProfile.equals(DamageEngineConfig.getCurrentProfile())) {
            // 先把当前设置存回它自己的文件，再载入目标档案
            config.save();
            config.load(selectedProfile);
        } else {
            config.save();
        }
        mc.displayGuiScreen(parent);
    }

    private void openConfigFolder() {
        try {
            File dir = DamageEngineConfig.getProfilesDir().toFile();
            if (!dir.exists()) {
                dir.mkdirs();
            }
            java.awt.Desktop.getDesktop().open(dir);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            scrollPx -= Integer.signum(wheel) * 20;
            scrollPx = Math.max(0, Math.min(scrollPx, maxScroll()));
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
