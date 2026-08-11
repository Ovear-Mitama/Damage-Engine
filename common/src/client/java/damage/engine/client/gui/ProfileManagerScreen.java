package damage.engine.client.gui;

import damage.engine.DamageEngineConfig;
import net.minecraft.util.Util;import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ProfileManagerScreen extends Screen {
    private final Screen parent;
    private String selectedProfile;
    private final List<String> profiles = new ArrayList<>();

    public ProfileManagerScreen(Screen parent) {
        super(Component.translatable("title.damage-engine.profile_manager"));
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
            Component.translatable("gui.done").withColor(0xFFB5F0C6),
            () -> {
                if (selectedProfile != null) {
                    DamageEngineConfig.setCurrentProfile(selectedProfile);
                    DamageEngineConfig.getInstance().save();
                }
                this.minecraft.setScreen(parent);
            }
        ));

        // Open config folder button
        this.addRenderableWidget(new StyledButton(
            this.width - buttonWidth * 2 - 20, buttonY, buttonWidth, 20,
            Component.translatable("button.damage-engine.open_config_folder"),
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
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float delta) {
        // 1.21.11: in-game background blur is applied once by the render pipeline;
        // calling renderBackground() here would blur twice and crash.
        if (this.minecraft != null && this.minecraft.level == null) {
            this.extractPanorama(guiGraphics, delta);
        }

        // Title
        String titleText = Component.translatable("title.damage-engine.profile_manager").getString();
        guiGraphics.centeredText(this.font, titleText, this.width / 2, 15, 0xFFFFFFFF);

        // Render widgets manually (avoid super.render() blur); tooltips are rendered
        // automatically by the widget system in 1.21.11
        for (var child : this.children()) {
            if (child instanceof AbstractWidget w) {
                w.extractRenderState(guiGraphics, mouseX, mouseY, delta);
            }
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    private static class ProfileButton extends AbstractWidget {
        private final String profileName;
        private final boolean isSelected;
        private final Runnable onPress;

        public ProfileButton(int x, int y, int width, int height, String profileName, boolean isSelected, Runnable onPress) {
            super(x, y, width, height, Component.literal(profileName));
            this.profileName = profileName;
            this.isSelected = isSelected;
            this.onPress = onPress;
        }

        @Override
        public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean bl) {
            if (this.active && this.visible && event.button() == 0 && this.isMouseOver(event.x(), event.y())) {
                this.playDownSound(Minecraft.getInstance().getSoundManager());
                this.onPress.run();
                return true;
            }
            return false;
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float delta) {
            if (isHovered()) guiGraphics.requestCursor(DamageConfigScreen.CURSOR_HAND);
            int x = getX(); int y = getY(); int w = getWidth(); int h = getHeight();

            // Background
            int bgColor = isHovered() ? 0x30FFFFFF : 0x10000000;
            guiGraphics.fill(x, y, x + w, y + h, bgColor);

            // Border for selected
            if (isSelected) {
                guiGraphics.fill(x, y, x + w, y + 1, 0xFFB5F0C6);
                guiGraphics.fill(x, y + h - 1, x + w, y + h, 0xFFB5F0C6);
                guiGraphics.fill(x, y, x + 1, y + h, 0xFFB5F0C6);
                guiGraphics.fill(x + w - 1, y, x + w, y + h, 0xFFB5F0C6);
            }

            int color = isSelected ? 0xFFB5F0C6 : 0xFFFFFFFF;
            guiGraphics.text(Minecraft.getInstance().font, profileName, x + 10, y + (h - 8) / 2, color);

            if (isSelected) {
                guiGraphics.text(Minecraft.getInstance().font, "✓", x + w - 20, y + (h - 8) / 2, 0xFFB5F0C6);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput builder) {
            this.defaultButtonNarrationText(builder);
        }
    }

    private static class StyledButton extends AbstractWidget {
        private final Runnable onPress;

        public StyledButton(int x, int y, int width, int height, Component message, Runnable onPress) {
            super(x, y, width, height, message);
            this.onPress = onPress;
        }

        @Override
        public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean bl) {
            if (this.active && this.visible && event.button() == 0) {
                if (this.isMouseOver(event.x(), event.y())) {
                    this.playDownSound(Minecraft.getInstance().getSoundManager());
                    this.onPress.run();
                    return true;
                }
            }
            return false;
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float delta) {
            if (isHovered()) guiGraphics.requestCursor(DamageConfigScreen.CURSOR_HAND);
            guiGraphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0x20000000);

            int borderColor = isHovered() ? 0xFFFFFFFF : 0xFFA0A0A0;
            int x = getX(); int y = getY(); int w = getWidth(); int h = getHeight();
            guiGraphics.fill(x, y, x + w, y + 1, borderColor);
            guiGraphics.fill(x, y + h - 1, x + w, y + h, borderColor);
            guiGraphics.fill(x, y, x + 1, y + h, borderColor);
            guiGraphics.fill(x + w - 1, y, x + w, y + h, borderColor);

            guiGraphics.centeredText(Minecraft.getInstance().font, getMessage(), getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, 0xFFFFFFFF);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput builder) {
            this.defaultButtonNarrationText(builder);
        }
    }
}