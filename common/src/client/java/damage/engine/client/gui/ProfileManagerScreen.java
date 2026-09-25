package damage.engine.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import damage.engine.DamageEngineConfig;
import damage.engine.compat.GuiGraphics;
import damage.engine.compat.Tooltip;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ProfileManagerScreen extends Screen {
    private final Screen parent;
    private String selectedProfile;
    private final List<String> profiles = new ArrayList<>();

    public ProfileManagerScreen(Screen parent) {
        super(new TranslatableComponent("title.damage-engine.profile_manager"));
        this.parent = parent;
        this.selectedProfile = DamageEngineConfig.getCurrentProfile();
    }

    @Override
    protected void init() {
        this.clearWidgets();
        profiles.clear();
        profiles.addAll(DamageEngineConfig.getProfileNames());

        int listStartY = 45;
        int entryHeight = 22;
        int listWidth = 200;
        int listX = this.width / 2 - listWidth / 2;

        for (int i = 0; i < profiles.size(); i++) {
            String profileName = profiles.get(i);
            boolean isSelected = profileName.equals(selectedProfile);
            int entryY = listStartY + i * (entryHeight + 2);
            
            this.addRenderableWidget(new ProfileButton(
                listX, entryY, listWidth, entryHeight,
                profileName, isSelected,
                () -> {
                    selectedProfile = profileName;
                    this.init();
                }
            ));
        }

        // Done button
        int buttonWidth = 80;
        int buttonY = this.height - 35;
        this.addRenderableWidget(new StyledButton(
            this.width - buttonWidth - 10, buttonY, buttonWidth, 20,
            new TranslatableComponent("gui.done").withStyle(style -> style.withColor(TextColor.fromRgb(0xFFB5F0C6))),
            () -> {
                if (selectedProfile != null) {
                    String current = DamageEngineConfig.getCurrentProfile();
                    if (!selectedProfile.equals(current)) {
                        // 切换配置:先把当前设置存回它自己的文件,再从磁盘载入目标配置的内容。
                        // 注意不能反向 save()——那只是改了个"当前配置名",既不会载入目标配置的数值,
                        // 还会把当前设置覆盖进目标配置文件,导致看起来"没切换"且目标配置被冲掉。
                        DamageEngineConfig.getInstance().save();
                        DamageEngineConfig.getInstance().load(selectedProfile);
                    } else {
                        DamageEngineConfig.getInstance().save();
                    }
                }
                this.minecraft.setScreen(parent);
            }
        ));

        // Open config folder button
        this.addRenderableWidget(new StyledButton(
            this.width - buttonWidth * 2 - 20, buttonY, buttonWidth, 20,
            new TranslatableComponent("button.damage-engine.open_config_folder"),
            () -> {
                File profilesDir = DamageEngineConfig.getProfilesDir().toFile();
                if (!profilesDir.exists()) {
                    profilesDir.mkdirs();
                }
                Util.getPlatform().openFile(profilesDir);
            }
        ));
    }

    @Override
    public void render(PoseStack pose, int mouseX, int mouseY, float delta) {
        GuiGraphics guiGraphics = GuiGraphics.of(pose);
        this.renderBackground(pose);

        // Title
        String titleText = new TranslatableComponent("title.damage-engine.profile_manager").getString();
        guiGraphics.drawCenteredString(this.font, titleText, this.width / 2, 15, 0xFFFFFFFF);

        // Render widgets + tooltips manually (avoid super.render() blur)
        // 1.18 没有 z 层级(谁后画谁在上),提示必须等所有控件画完再画,
        // 否则会被列表里后面的按钮盖住。
        Tooltip hoveredTooltip = null;
        for (var child : this.children()) {
            if (child instanceof AbstractWidget w) {
                w.render(pose, mouseX, mouseY, delta);
                if (w.isMouseOver(mouseX, mouseY) && w instanceof TooltipHolder holder && holder.getTooltip() != null) {
                    hoveredTooltip = holder.getTooltip();
                }
            }
        }
        if (hoveredTooltip != null) {
            guiGraphics.renderTooltip(this.font, hoveredTooltip.toCharSequence(this.minecraft), mouseX, mouseY);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    private static class ProfileButton extends AbstractWidget implements TooltipHolder {
        private final String profileName;
        private final boolean isSelected;
        private final Runnable onPress;
        private Tooltip damageEngine$tooltip;

        public ProfileButton(int x, int y, int width, int height, String profileName, boolean isSelected, Runnable onPress) {
            super(x, y, width, height, new TextComponent(profileName));
            this.profileName = profileName;
            this.isSelected = isSelected;
            this.onPress = onPress;
        }

        @Override public void setTooltip(Tooltip tooltip) { this.damageEngine$tooltip = tooltip; }

        @Override public Tooltip getTooltip() { return this.damageEngine$tooltip; }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (this.active && this.visible && button == 0 && this.isMouseOver(mouseX, mouseY)) {
                this.playDownSound(Minecraft.getInstance().getSoundManager());
                this.onPress.run();
                return true;
            }
            return false;
        }

        @Override
        public void renderButton(PoseStack pose, int mouseX, int mouseY, float delta) {
            GuiGraphics guiGraphics = GuiGraphics.of(pose);
            // 1.18 的 AbstractWidget 只有公开的 x/y 字段,没有 getX()/getY()
            int x = this.x; int y = this.y; int w = getWidth(); int h = getHeight();

            // Background
            int bgColor = isHovered ? 0x30FFFFFF : 0x10000000;
            guiGraphics.fill(x, y, x + w, y + h, bgColor);

            // Border for selected
            if (isSelected) {
                guiGraphics.fill(x, y, x + w, y + 1, 0xFFB5F0C6);
                guiGraphics.fill(x, y + h - 1, x + w, y + h, 0xFFB5F0C6);
                guiGraphics.fill(x, y, x + 1, y + h, 0xFFB5F0C6);
                guiGraphics.fill(x + w - 1, y, x + w, y + h, 0xFFB5F0C6);
            }

            int color = isSelected ? 0xFFB5F0C6 : 0xFFFFFFFF;
            guiGraphics.drawString(Minecraft.getInstance().font, profileName, x + 10, y + (h - 8) / 2, color);

            if (isSelected) {
                guiGraphics.drawString(Minecraft.getInstance().font, "✓", x + w - 20, y + (h - 8) / 2, 0xFFB5F0C6);
            }
        }

        @Override
        public void updateNarration(NarrationElementOutput builder) {
            this.defaultButtonNarrationText(builder);
        }
    }

    private static class StyledButton extends AbstractWidget implements TooltipHolder {
        private final Runnable onPress;
        private Tooltip damageEngine$tooltip;

        public StyledButton(int x, int y, int width, int height, Component message, Runnable onPress) {
            super(x, y, width, height, message);
            this.onPress = onPress;
        }

        @Override public void setTooltip(Tooltip tooltip) { this.damageEngine$tooltip = tooltip; }

        @Override public Tooltip getTooltip() { return this.damageEngine$tooltip; }

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
        public void renderButton(PoseStack pose, int mouseX, int mouseY, float delta) {
            GuiGraphics guiGraphics = GuiGraphics.of(pose);
            guiGraphics.fill(this.x, this.y, this.x + getWidth(), this.y + getHeight(), 0x20000000);

            int borderColor = isHovered ? 0xFFFFFFFF : 0xFFA0A0A0;
            // 1.18 的 AbstractWidget 只有公开的 x/y 字段,没有 getX()/getY()
            int x = this.x; int y = this.y; int w = getWidth(); int h = getHeight();
            guiGraphics.fill(x, y, x + w, y + 1, borderColor);
            guiGraphics.fill(x, y + h - 1, x + w, y + h, borderColor);
            guiGraphics.fill(x, y, x + 1, y + h, borderColor);
            guiGraphics.fill(x + w - 1, y, x + w, y + h, borderColor);

            guiGraphics.drawCenteredString(Minecraft.getInstance().font, getMessage(), this.x + getWidth() / 2, this.y + (getHeight() - 8) / 2, 0xFFFFFFFF);
        }

        @Override
        public void updateNarration(NarrationElementOutput builder) {
            this.defaultButtonNarrationText(builder);
        }
    }
}