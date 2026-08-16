package damage.engine.client.gui;

import damage.engine.ClientKeybindings;
import damage.engine.DamageEngineClient;
import damage.engine.DamageEngineConfig;
import com.google.gson.GsonBuilder;
import damage.engine.hud.DamageHud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.network.chat.TextColor;

import java.util.*;
import java.util.function.Consumer;

public class DamageConfigScreen extends Screen {
    private final Screen parent;
    private final DamageEngineConfig config;
    
    private static final String[] TAB_KEYS = {
        "tab.damage-engine.general",
        "tab.damage-engine.damage_interface",
        "tab.damage-engine.damage_numbers",
        "tab.damage-engine.entity_info",
        "tab.damage-engine.rating",
        "tab.damage-engine.keybinds",
        "tab.damage-engine.other"
    };
    private static final int TAB_COUNT = TAB_KEYS.length;
    
    private int selectedTab = 0;
    private ConfigOptionListWidget optionList;
    private double lastScroll = 0;
    private boolean hasUnsavedChanges = false;
    
    // Expand state trackers
    private final Map<String, Boolean> expandStates = new HashMap<>();
    
    private int resetButtonState = 0;
    private long resetButtonActionTime = 0;
    private boolean isBinding = false;
    private String configSnapshot;

    /**
     * Widget currently being interacted with (slider dragged / edit box focused).
     * DamageConfigScreen drives it directly from mouseDragged/keyPressed/charTyped,
     * because AbstractSelectionList's default event routing does not reliably reach
     * row children for press-and-hold drags or text input.
     */
    private GuiEventListener activeWidget = null;

    public DamageConfigScreen(Screen parent) {
        super(Component.translatable("title.damage-engine.config"));
        this.parent = parent;
        this.config = DamageEngineConfig.getInstance();
        this.config.load(); // Reload from disk to ensure the screen shows the latest saved config
        this.configSnapshot = new GsonBuilder().setPrettyPrinting().create().toJson(config);
    }

    @Override
    protected void init() {
        try {
            if (optionList != null) lastScroll = optionList.getScrollAmount();
            this.clearWidgets();
            
            int tabHeight = 24;
            int footerY = this.height - 35;
            int topY = tabHeight + 4;
            int listHeight = footerY - topY;
            
            optionList = new ConfigOptionListWidget(this.minecraft, this.width, listHeight, topY, 26);
            this.addWidget(optionList);
            
            initOptionEntries();
            
            if (optionList != null && lastScroll > 0) optionList.setScrollAmount(lastScroll);
            
            // Footer buttons
            int buttonWidth = 80;
            int buttonY = footerY + 5;
            
            // Save & Close (green)
            this.addRenderableWidget(new StyledButton(this.width - buttonWidth - 10, buttonY, buttonWidth, 20,
                Component.translatable("button.damage-engine.save_close").withStyle(style -> style.withColor(TextColor.fromRgb(0xFFB7F3C8))), () -> {
                    config.save();
                    hasUnsavedChanges = false;
                    this.minecraft.setScreen(parent);
                }));
            
            // Cancel (red)
            this.addRenderableWidget(new StyledButton(this.width - buttonWidth * 2 - 20, buttonY, buttonWidth, 20,
                Component.translatable("gui.cancel").withStyle(style -> style.withColor(TextColor.fromRgb(0xFFFC887E))), () -> {
                    if (hasUnsavedChanges) {
                        showUnsavedPrompt();
                    } else {
                        config.load();
                        this.minecraft.setScreen(parent);
                    }
                }));
            
            // Preview toggle
            final StyledButton[] previewBtnRef = new StyledButton[1];
            previewBtnRef[0] = new StyledButton(5, buttonY, 80, 20,
                Component.translatable("text.damage-engine.preview").append(": ").append(
                    Component.translatable(config.previewEnabled ? "options.on" : "options.off")
                        .withStyle(style -> style.withColor(TextColor.fromRgb(config.previewEnabled ? 0xFFB5F0C6 : 0xFFFC887E)))), () -> {
                config.previewEnabled = !config.previewEnabled;
                previewBtnRef[0].setMessage(Component.translatable("text.damage-engine.preview").append(": ").append(
                    Component.translatable(config.previewEnabled ? "options.on" : "options.off")
                        .withStyle(style -> style.withColor(TextColor.fromRgb(config.previewEnabled ? 0xFFB5F0C6 : 0xFFFC887E)))));
            });
            this.addRenderableWidget(previewBtnRef[0]);
        } catch (Exception e) {
            DamageEngineClient.LOGGER.error("Failed to init config screen: ", e);
        }
    }

    private void showUnsavedPrompt() {
        // Show confirmation dialog for unsaved changes
        this.minecraft.setScreen(new ConfirmExitScreen(this, parent, config));
    }

    private void initOptionEntries() {
        optionList.clearEntriesPublic();
        
        switch (selectedTab) {
            case 0 -> initGeneralTab();
            case 1 -> initDamageInterfaceTab();
            case 2 -> initDamageNumbersTab();
            case 3 -> initEntityInfoTab();
            case 4 -> initRatingTab();
            case 5 -> initKeybindsTab();
            case 6 -> initOtherTab();
        }
        
        // Spacer at bottom
        addOption(new SpacerEntry(30));
        addOption(new SpacerEntry(30));
    }

    private void initGeneralTab() {
        addOption(new BooleanOptionEntry("option.damage-engine.showDamage", config.showDamage, v -> { config.showDamage = v; markChanged(); }));
        addOption(new ButtonActionEntry("option.damage-engine.edit_pos_label", "button.damage-engine.adjust", () -> {
            playClickSound();
            this.minecraft.setScreen(new HudEditorScreen(this));
        }));
        addOption(new SeparatorToggleEntry("option.damage-engine.numberSeparator", config.numberSeparator, v -> { config.numberSeparator = v; markChanged(); }));
        addOption(new BooleanOptionEntry("option.damage-engine.hideOnF1", config.hideOnF1, v -> { config.hideOnF1 = v; markChanged(); }));
    }

    private void initDamageInterfaceTab() {
        addOption(new BooleanOptionEntry("option.damage-engine.showDamageDisplay", config.showDamageDisplay, v -> { config.showDamageDisplay = v; markChanged(); }));
        addOption(new IntegerSliderEntry("option.damage-engine.decimalPlaces", config.decimalPlaces, 0, 10, v -> { config.decimalPlaces = v; markChanged(); }, true));
        addOption(new HexColorEntry("option.damage-engine.normalColor", config.normalColor, v -> { config.normalColor = v; markChanged(); }));
        addOption(new HexColorEntry("option.damage-engine.critColor", config.critColor, v -> { config.critColor = v; markChanged(); }));
        
        // Total damage color thresholds
        addOption(new ExpandableHeaderEntry("option.damage-engine.total_damage_colors", "total_damage_colors_dmg", v -> refreshOptions()));
        if (isExpanded("total_damage_colors_dmg")) {
            config.damageThresholds.sort((a, b) -> Float.compare(a.threshold, b.threshold));
            for (DamageEngineConfig.DamageThreshold dt : new ArrayList<>(config.damageThresholds)) {
                addOption(new DamageThresholdEntry(dt, () -> {
                    config.damageThresholds.remove(dt);
                    markChanged();
                    refreshOptions();
                }));
            }
            addOption(new AddButtonEntry(() -> {
                float nextVal = 0f;
                if (!config.damageThresholds.isEmpty()) {
                    nextVal = config.damageThresholds.get(config.damageThresholds.size() - 1).threshold + 50f;
                }
                config.damageThresholds.add(new DamageEngineConfig.DamageThreshold(nextVal, 0xFFFFFFFF));
                markChanged();
                refreshOptions();
            }));
        }
        
        addOption(new BooleanOptionEntry("option.damage-engine.resetEnabled", config.resetEnabled, v -> { config.resetEnabled = v; markChanged(); }));
        addOption(new NumericEntry("option.damage-engine.resetTime", config.resetTime, v -> { config.resetTime = v; markChanged(); }));
        addOption(new BooleanOptionEntry("option.damage-engine.showProgressBar", config.showProgressBar, v -> { config.showProgressBar = v; markChanged(); }));
        addOption(new HexColorEntry("option.damage-engine.progressBarColor", config.progressBarColor, v -> { config.progressBarColor = v; markChanged(); }));
        addOption(new BooleanOptionEntry("option.damage-engine.showCombo", config.showCombo, v -> { config.showCombo = v; markChanged(); }));
        addOption(new HexColorEntry("option.damage-engine.comboColor", config.comboColor, v -> { config.comboColor = v; markChanged(); }));
        addOption(new BooleanOptionEntry("option.damage-engine.showDamageHistory", config.showDamageHistory, v -> { config.showDamageHistory = v; markChanged(); }));
        addOption(new BooleanOptionEntry("option.damage-engine.recordOtherPlayers", config.recordOtherPlayers, v -> { config.recordOtherPlayers = v; markChanged(); }));
        addOption(new NumericEntry("option.damage-engine.historyDisappearanceTime", config.historyDisappearanceTime, v -> { config.historyDisappearanceTime = v; markChanged(); }));
        addOption(new IntegerSliderEntry("option.damage-engine.historyLimit", config.historyLimit, 1, 50, v -> { config.historyLimit = v; markChanged(); }, true));
        addOption(new IntegerSliderEntry("option.damage-engine.historyDecimalPlaces", config.historyDecimalPlaces, 0, 10, v -> { config.historyDecimalPlaces = v; markChanged(); }, true));
    }

    private void initDamageNumbersTab() {
        addOption(new BooleanOptionEntry("option.damage-engine.showDamageIndicator", config.showDamageIndicator, v -> { config.showDamageIndicator = v; markChanged(); }));
        addOption(new IntegerSliderEntry("option.damage-engine.indicatorDecimalPlaces", config.indicatorDecimalPlaces, 0, 10, v -> { config.indicatorDecimalPlaces = v; markChanged(); }, true));
        
        // Mode selector
        addOption(new ModeSelectorEntry("option.damage-engine.indicatorMode", config.indicatorMode, v -> { config.indicatorMode = v; markChanged(); }));
        
        addOption(new NumericEntry("option.damage-engine.indicatorScale", config.indicatorScale, v -> { config.indicatorScale = v; markChanged(); }));
        addOption(new IntegerSliderEntry("option.damage-engine.indicatorOpacity", config.indicatorOpacity, 0, 100, v -> { config.indicatorOpacity = v; markChanged(); }, true));
        addOption(new HexColorEntry("option.damage-engine.damageIndicatorNormalColor", config.damageIndicatorNormalColor, v -> { config.damageIndicatorNormalColor = v; markChanged(); }));
        addOption(new HexColorEntry("option.damage-engine.damageIndicatorCritColor", config.damageIndicatorCritColor, v -> { config.damageIndicatorCritColor = v; markChanged(); }));
        addOption(new BooleanOptionEntry("option.damage-engine.indicatorBold", config.indicatorBold, v -> { config.indicatorBold = v; markChanged(); }));
        addOption(new BooleanOptionEntry("option.damage-engine.showHealIndicator", config.showHealIndicator, v -> { config.showHealIndicator = v; markChanged(); }));
        addOption(new HexColorEntry("option.damage-engine.healIndicatorColor", config.healIndicatorColor, v -> { config.healIndicatorColor = v; markChanged(); }));
        addOption(new BooleanOptionEntry("option.damage-engine.showGlobalDamageIndicator", config.showGlobalDamageIndicator, v -> { config.showGlobalDamageIndicator = v; markChanged(); }));
        addOption(new NumericEntry("option.damage-engine.globalIndicatorMaxDistance", config.globalIndicatorMaxDistance,
            v -> { config.globalIndicatorMaxDistance = v; markChanged(); },
            Component.translatable("hint.damage-engine.globalIndicatorMaxDistance")));
        addOption(new BooleanOptionEntry("option.damage-engine.indicatorPrefixSign", config.indicatorPrefixSign, v -> { config.indicatorPrefixSign = v; markChanged(); }));
        addOption(new BooleanOptionEntry("option.damage-engine.showKillIndicator", config.showKillIndicator, v -> { config.showKillIndicator = v; markChanged(); }));
        addOption(new TextEntry("option.damage-engine.killText", config.killText, v -> { config.killText = v; markChanged(); }));
        addOption(new HexColorEntry("option.damage-engine.killTextColor", config.killTextColor, v -> { config.killTextColor = v; markChanged(); }));
    }

    private void initEntityInfoTab() {
        addOption(new BooleanOptionEntry("option.damage-engine.showInfo", config.showInfo, v -> { config.showInfo = v; markChanged(); }));
        addOption(new NumericEntry("option.damage-engine.infoTrackTime", config.infoTrackTime, v -> { config.infoTrackTime = v; markChanged(); }));
        addOption(new BooleanOptionEntry("option.damage-engine.infoNoRoundedBorder", config.infoNoRoundedBorder, v -> { config.infoNoRoundedBorder = v; markChanged(); }));
        addOption(new HexColorEntry("option.damage-engine.infoBackgroundColor", config.infoBackgroundColor, v -> { config.infoBackgroundColor = v; markChanged(); }));
        addOption(new IntegerSliderEntry("option.damage-engine.infoBackgroundOpacity", config.infoBackgroundOpacity, 0, 100, v -> { config.infoBackgroundOpacity = v; markChanged(); }, true));
        addOption(new HexColorEntry("option.damage-engine.infoBarColor", config.infoBarColor, v -> { config.infoBarColor = v; markChanged(); }));
        addOption(new HexColorEntry("option.damage-engine.infoBarHealColor", config.infoBarHealColor, v -> { config.infoBarHealColor = v; markChanged(); }));
        addOption(new HexColorEntry("option.damage-engine.infoBarDamageColor", config.infoBarDamageColor, v -> { config.infoBarDamageColor = v; markChanged(); }));
    }

    private void initRatingTab() {
        addOption(new BooleanOptionEntry("option.damage-engine.showRating", config.showRating, v -> { config.showRating = v; markChanged(); }));
        addOption(new NumericEntry("option.damage-engine.ratingResetTime", config.ratingResetTime, v -> { config.ratingResetTime = v; markChanged(); }));
        
        // Points
        addOption(new ExpandableHeaderEntry("option.damage-engine.rating_points", "rating_points", v -> refreshOptions()));
        if (isExpanded("rating_points")) {
            addOption(new WeightEntry("option.damage-engine.comboPoints", config.comboPoints, v -> { config.comboPoints = v; markChanged(); }, "hint.damage-engine.comboPoints"));
            addOption(new WeightEntry("option.damage-engine.hitPoints", config.hitPoints, v -> { config.hitPoints = v; markChanged(); }, null));
            addOption(new WeightEntry("option.damage-engine.critPoints", config.critPoints, v -> { config.critPoints = v; markChanged(); }, null));
        }
        
        // Grades
        addOption(new ExpandableHeaderEntry("option.damage-engine.rating_grades", "rating_grades", v -> refreshOptions()));
        if (isExpanded("rating_grades")) {
            config.ratingGrades.sort((a, b) -> Float.compare(b.minScore, a.minScore));
            for (int i = 0; i < config.ratingGrades.size(); i++) {
                config.ratingGrades.get(i).index = i + 1;
            }
            for (DamageEngineConfig.RatingGrade g : new ArrayList<>(config.ratingGrades)) {
                addOption(new RatingGradeEntry(g, () -> {
                    config.ratingGrades.remove(g);
                    markChanged();
                    refreshOptions();
                }));
            }
            addOption(new AddButtonEntry(() -> {
                int nextIndex = config.ratingGrades.size() + 1;
                config.ratingGrades.add(new DamageEngineConfig.RatingGrade(0f, "F", 0xFFFFFFFF, nextIndex));
                markChanged();
                refreshOptions();
            }));
        }
        
        // Appearance
        addOption(new ExpandableHeaderEntry("option.damage-engine.rating_appearance", "rating_appearance", v -> refreshOptions()));
        if (isExpanded("rating_appearance")) {
            addOption(new BooleanOptionEntry("option.damage-engine.rating_use_images", config.ratingUseImages, v -> {
                config.ratingUseImages = v;
                markChanged();
            }));
            config.ratingGrades.sort((a, b) -> Float.compare(b.minScore, a.minScore));
            for (int i = 0; i < config.ratingGrades.size(); i++) {
                config.ratingGrades.get(i).index = i + 1;
            }
            for (DamageEngineConfig.RatingGrade g : new ArrayList<>(config.ratingGrades)) {
                addOption(new RatingGradeAppearanceEntry(g, config));
            }
        }
    }

    private void initKeybindsTab() {
        if (ClientKeybindings.configKeyBinding != null) {
            addOption(new KeybindEntry("key.damage_engine.config", ClientKeybindings.configKeyBinding));
        }
        if (ClientKeybindings.toggleHudKeyBinding != null) {
            addOption(new KeybindEntry("key.damage_engine.toggle_hud", ClientKeybindings.toggleHudKeyBinding));
        }
        if (ClientKeybindings.clearDamageKeyBinding != null) {
            addOption(new KeybindEntry("key.damage_engine.clear_damage", ClientKeybindings.clearDamageKeyBinding));
        }
    }

    private void initOtherTab() {
        addOption(new BooleanOptionEntry("option.damage-engine.shareDamage", config.shareDamage, v -> { config.shareDamage = v; markChanged(); }));
        addOption(new BooleanOptionEntry("option.damage-engine.checkUpdate", config.checkUpdate, v -> { config.checkUpdate = v; markChanged(); }));
        addOption(new ExpandableHeaderEntry("option.damage-engine.debugMode", "debugMode", v -> refreshOptions()));
        if (isExpanded("debugMode")) {
            addOption(new BooleanOptionEntry("option.damage-engine.debugShowDamageInfo", config.debugShowDamageInfo, v -> { config.debugShowDamageInfo = v; markChanged(); }));
            addOption(new BooleanOptionEntry("option.damage-engine.debugShowRating", config.debugShowRating, v -> { config.debugShowRating = v; markChanged(); }));
        }
        
        addOption(new ResetButtonEntry("option.damage-engine.reset", () -> {
            config.resetToDefaults();
            KeyMapping.resetMapping();
            this.minecraft.options.save();
            hasUnsavedChanges = false;
            this.init();
        }));
    }

    private boolean isExpanded(String key) {
        return expandStates.getOrDefault(key, false);
    }

    private void refreshOptions() {
        if (optionList == null) return;
        double scroll = optionList.getScrollAmount();
        optionList.clearEntriesPublic();
        initOptionEntries();
        optionList.setScrollAmount(scroll);
    }

    private void addOption(OptionEntry entry) {
        optionList.addEntryPublic(entry);
    }

    private void markChanged() {
        // Compare current config JSON with snapshot to detect real changes
        String current = new GsonBuilder().setPrettyPrinting().create().toJson(config);
        hasUnsavedChanges = !current.equals(configSnapshot);
    }

    public void setBinding(boolean binding) {
        this.isBinding = binding;
    }

    public void playClickSound() {
        try {
            Minecraft client = Minecraft.getInstance();
            if (client != null) {
                SimpleSoundInstance sound = SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F);
                client.getSoundManager().play(sound);
            }
        } catch (Exception ignored) {}
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (isBinding) {
            if (optionList != null) {
                for (GuiEventListener widget : optionList.children()) {
                    if (widget instanceof KeybindEntry ke && ke.isBinding()) {
                        if (ke.getButton().keyPressed(keyCode, scanCode, modifiers)) return true;
                    }
                }
            }
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                setBinding(false);
                return true;
            }
        }
        // Forward keys to the active edit box (the box is focused via mouseClicked).
        if (activeWidget instanceof EditBox box) {
            box.setFocused(true);
            if (box.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (activeWidget instanceof EditBox box) {
            box.setFocused(true);
            if (box.charTyped(codePoint, modifiers)) {
                return true;
            }
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        // Drive the active slider directly for the whole press-and-hold drag,
        // even when the cursor leaves the slider bar (AbstractSelectionList's
        // default routing can stop reaching row children).
        if (activeWidget instanceof StyledSliderWidget slider && button == 0) {
            slider.updateValueFromMouse(mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        // Drive the slider release directly so the release-click sound always fires,
        // even if the default event chain does not reach the row child. The second
        // call through super.mouseReleased is a no-op (pendingReleaseSound consumed).
        if (activeWidget instanceof StyledSliderWidget slider) {
            slider.mouseReleased(mouseX, mouseY, button);
        }
        // NOTE: keep activeWidget so an edit box stays the keyboard target after the
        // mouse is released (until the user clicks somewhere else).
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Reset the active widget on every click; re-set below when a slider / edit
        // box is hit.
        activeWidget = null;

        // Tab clicks
        int tabY = 0;
        int tabHeight = 24;
        if (mouseY >= tabY && mouseY <= tabY + tabHeight) {
            int tabWidth = this.width / TAB_COUNT;
            int tabIndex = (int)(mouseX / tabWidth);
            if (tabIndex >= 0 && tabIndex < TAB_COUNT) {
                selectedTab = tabIndex;
                playClickSound();
                init();
                return true;
            }
        }
        
        // Unfocus text fields
        if (optionList != null) {
            for (GuiEventListener widget : optionList.children()) {
                if (widget instanceof OptionEntry entry) {
                    for (GuiEventListener child : entry.children()) {
                        if (child instanceof EditBox tf) tf.setFocused(false);
                    }
                }
            }
        }
        
        if (isBinding) {
            if (optionList != null) {
                for (GuiEventListener widget : optionList.children()) {
                    if (widget instanceof KeybindEntry ke && ke.isBinding()) {
                        ke.bindMouse(button);
                        return true;
                    }
                }
            }
            setBinding(false);
            return true;
        }

        // Direct hit-testing on visible row widgets: AbstractSelectionList's row dispatch can miss
        // (scroll drift, row-window x bounds), so hit-test each visible row's children by their
        // rendered absolute rect - this keeps clicking in sync with what is drawn on screen.
        if (button == 0 && optionList != null) {
            java.util.List<OptionEntry> entries = optionList.children();
            int listTop = optionList.listTop();
            int listBottom = optionList.listBottom();
            for (int i = 0; i < entries.size(); i++) {
                OptionEntry entry = entries.get(i);
                int rowTop = optionList.rowTop(i);
                int rowBottom = optionList.rowBottom(i);
                // Only process rows intersecting the visible list area, so stale
                // widget rects of scrolled-away rows can never capture the click.
                if (rowBottom <= listTop || rowTop >= listBottom) continue;
                for (GuiEventListener c : entry.children()) {
                    if (c instanceof AbstractWidget w && w.active && w.visible) {
                        if (mouseX >= w.getX() && mouseX < w.getX() + w.getWidth()
                            && mouseY >= w.getY() && mouseY < w.getY() + w.getHeight()) {
                            // Record the interacted widget (slider / edit box) so this
                            // screen can drive press-and-hold drags and text input
                            // directly, without relying on AbstractSelectionList's
                            // default event routing.
                            if (c instanceof EditBox || c instanceof StyledSliderWidget) {
                                activeWidget = c;
                                if (c instanceof EditBox) {
                                    // Build the 屏幕→列表→行 焦点链 so keyboard events reach the box
                                    this.setFocused(optionList);
                                    optionList.setFocused(entry);
                                }
                            }
                            return c.mouseClicked(mouseX, mouseY, button);
                        }
                    }
                }
                // No child widget hit: if the click is inside this row, hand it to the
                // entry itself (whole-row click targets like the expand/collapse
                // headers, which must toggle even when clicking the label area).
                if (mouseY >= rowTop && mouseY < rowBottom) {
                    if (entry.mouseClicked(mouseX, mouseY, button)) {
                        return true;
                    }
                    return super.mouseClicked(mouseX, mouseY, button);
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        // Keep the active edit box focused so its cursor stays visible while typing.
        // (Caret blink itself is driven by EditBoxCursorMixin using wall-clock time.)
        if (activeWidget instanceof EditBox box) box.setFocused(true);
        this.renderBackground(guiGraphics);
        
        // Render tabs
        int tabHeight = 24;
        int tabWidth = this.width / TAB_COUNT;
        for (int i = 0; i < TAB_COUNT; i++) {
            int tabX = i * tabWidth;
            int tabY = 0;
            boolean isSelected = i == selectedTab;
            boolean isHovered = mouseX >= tabX && mouseX < tabX + tabWidth && mouseY >= tabY && mouseY < tabY + tabHeight;
            
            int bgColor = isSelected ? 0x30000000 : (isHovered ? 0x20000000 : 0x10000000);
            guiGraphics.fill(tabX, tabY, tabX + tabWidth, tabY + tabHeight, bgColor);
            
            String tabText = Component.translatable(TAB_KEYS[i]).getString();
            int textColor = isSelected ? 0xFFFFFFFF : 0xFFA0A0A0;
            guiGraphics.drawCenteredString(this.font, tabText, tabX + tabWidth / 2, tabY + 8, textColor);
            
            // Green bottom border for selected tab
            if (isSelected) {
                guiGraphics.fill(tabX, tabY + tabHeight - 2, tabX + tabWidth, tabY + tabHeight, 0xFFB5F0C6);
            }
        }
        
        // Separator below tabs
        guiGraphics.fill(0, tabHeight, this.width, tabHeight + 1, 0xFF555555);
        
        if (optionList != null) {
            // Self-heal stale dimensions (window resize / GUI scale change that skipped Screen.resize)
            int guiW = this.minecraft.getWindow().getGuiScaledWidth();
            int guiH = this.minecraft.getWindow().getGuiScaledHeight();
            if (optionList.listWidth() != this.width || this.width != guiW || this.height != guiH) {
                this.width = guiW;
                this.height = guiH;
                init();
            }
            // Advance smooth scroll BEFORE rendering so rendered rows and the next click share the same scrollAmount
            optionList.updateSmoothScroll(delta);
            optionList.render(guiGraphics, mouseX, mouseY, delta);
        }
        
        // Footer separator
        int footerY = this.height - 35;
        guiGraphics.fill(0, footerY - 1, this.width, footerY, 0xFF555555);
        
        // Client mode hint
        if (DamageEngineClient.serverModChecked && !DamageEngineClient.serverHasMod) {
            String[] lines = Component.translatable("text.damage-engine.client_mode_hint").getString().split("\n");
            int hintX = 5;
            int hintY = footerY - 30;
            for (int i = 0; i < lines.length; i++) {
                guiGraphics.drawString(this.font, Component.literal(lines[i]).withStyle(style -> style.withColor(TextColor.fromRgb(0xFFFC887E))), hintX, hintY + i * 12, 0xFFFC887E);
            }
        }
        
        // Preview
        if (config.previewEnabled) {
            new DamageHud().renderPreview(guiGraphics, 30, this.height - 30);
        }
        
        // Render footer widgets (avoid super.render() blur)
        for (GuiEventListener child : this.children()) {
            if (child != optionList && child instanceof AbstractWidget w) {
                w.render(guiGraphics, mouseX, mouseY, delta);
            }
        }
        // Tooltip is handled by vanilla AbstractWidget system — do NOT render manually
    }

    @Override
    public void onClose() {
        if (hasUnsavedChanges) {
            showUnsavedPrompt();
        } else {
            this.minecraft.setScreen(parent);
        }
    }

    // ========== Inner Widget Classes ==========

    static class StyledButton extends AbstractWidget {
        private final Runnable onPress;
        public StyledButton(int x, int y, int width, int height, Component message, Runnable onPress) {
            super(x, y, width, height, message);
            this.onPress = onPress;
        }
        @Override
        public void playDownSound(net.minecraft.client.sounds.SoundManager sm) {
            if (Minecraft.getInstance().screen instanceof DamageConfigScreen s) s.playClickSound();
            else if (Minecraft.getInstance().screen instanceof HudEditorScreen s) s.playClickSound();
            else super.playDownSound(sm);
        }
        @Override
        public boolean mouseClicked(double mx, double my, int btn) {
            if (this.active && this.visible && btn == 0) {
                if (mx >= (double)this.getX() && mx < (double)(this.getX() + this.getWidth())
                    && my >= (double)this.getY() && my < (double)(this.getY() + this.getHeight())) {
                    this.playDownSound(Minecraft.getInstance().getSoundManager());
                    this.onPress.run();
                    return true;
                }
            }
            return false;
        }
        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float d) {
            g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0x20000000);
            int bc = isHovered() ? 0xFFFFFFFF : 0xFFA0A0A0;
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            g.fill(x, y, x + w, y + 1, bc);
            g.fill(x, y + h - 1, x + w, y + h, bc);
            g.fill(x, y, x + 1, y + h, bc);
            g.fill(x + w - 1, y, x + w, y + h, bc);
            g.drawCenteredString(Minecraft.getInstance().font, getMessage(), x + w / 2, y + (h - 8) / 2, 0xFFFFFFFF);
        }
        @Override
        protected void updateWidgetNarration(NarrationElementOutput b) { this.defaultButtonNarrationText(b); }
    }

    private static class PlainTextButton extends AbstractWidget {
        private final Runnable onPress;
        private final int hoverColor;
        private int defaultColor;
        private boolean forceHover;
        public PlainTextButton(int x, int y, int w, int h, Component msg, Runnable onPress, int defaultColor, int hoverColor) {
            super(x, y, w, h, msg);
            this.onPress = onPress;
            this.defaultColor = defaultColor;
            this.hoverColor = hoverColor;
        }
        public void setForceHover(boolean v) { this.forceHover = v; }
        @Override
        public boolean mouseClicked(double mx, double my, int btn) {
            if (this.active && this.visible && btn == 0) {
                if (mx >= (double)this.getX() && mx < (double)(this.getX() + this.getWidth())
                    && my >= (double)this.getY() && my < (double)(this.getY() + this.getHeight())) {
                    this.playDownSound(Minecraft.getInstance().getSoundManager());
                    this.onPress.run();
                    return true;
                }
            }
            return false;
        }
        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float d) {
            int c = (isHovered() || forceHover) ? hoverColor : defaultColor;
            g.drawCenteredString(Minecraft.getInstance().font, getMessage(), getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, c);
        }
        @Override
        protected void updateWidgetNarration(NarrationElementOutput b) { this.defaultButtonNarrationText(b); }
        @Override
        public void playDownSound(net.minecraft.client.sounds.SoundManager sm) {
            if (Minecraft.getInstance().screen instanceof DamageConfigScreen s) s.playClickSound();
            else super.playDownSound(sm);
        }
    }

    // ========== Option List ==========

    abstract static class OptionEntry extends AbstractSelectionList.Entry<OptionEntry> {
        private double hoverAnim = 0;
        @Override
        public void render(GuiGraphics g, int idx, int y, int x, int ew, int eh, int mx, int my, boolean hovered, float dt) {
            DamageConfigScreen screen = (DamageConfigScreen) Minecraft.getInstance().screen;
            if (screen == null || screen.optionList == null) return;
            int ll = 0, lr = screen.width;
            boolean isHov = mx >= ll && mx <= lr && my >= y && my < y + eh;
            if (isHov && shouldHighlight()) hoverAnim = Mth.lerp(0.1f, hoverAnim, 1.0f);
            else hoverAnim = Mth.lerp(0.1f, hoverAnim, 0.0f);
            if (hoverAnim > 0.01 && shouldHighlight()) {
                g.fill(ll, y, lr, y + eh + 2, ((int)(26 * hoverAnim) << 24) | 0xFFFFFF);
            }
            renderContent(g, idx, y, x, ew, eh, mx, my, isHov, dt);
        }
        public abstract void renderContent(GuiGraphics g, int idx, int y, int x, int ew, int eh, int mx, int my, boolean hovered, float dt);
        public List<? extends GuiEventListener> children() { return Collections.emptyList(); }
        public List<? extends NarratableEntry> narratables() { return Collections.emptyList(); }
        protected boolean shouldHighlight() { return true; }
        @Override
        public boolean mouseClicked(double mx, double my, int btn) {
            for (GuiEventListener c : children()) {
                if (c instanceof EditBox tf) {
                    if (tf.isMouseOver(mx, my)) { tf.setFocused(true); return true; }
                    else tf.setFocused(false);
                } else if (c.mouseClicked(mx, my, btn)) return true;
            }
            return false;
        }
        @Override public boolean mouseReleased(double mx, double my, int btn) { for (GuiEventListener c : children()) if (c.mouseReleased(mx, my, btn)) return true; return false; }
        @Override public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) { for (GuiEventListener c : children()) if (c.mouseDragged(mx, my, btn, dx, dy)) return true; return false; }
        @Override public boolean keyPressed(int kc, int sc, int mod) { for (GuiEventListener c : children()) if (c.keyPressed(kc, sc, mod)) return true; return false; }
        @Override public boolean charTyped(char ch, int mod) { for (GuiEventListener c : children()) if (c.charTyped(ch, mod)) return true; return false; }
        @Override public boolean keyReleased(int kc, int sc, int mod) { for (GuiEventListener c : children()) if (c.keyReleased(kc, sc, mod)) return true; return false; }
    }

    private class ConfigOptionListWidget extends AbstractSelectionList<OptionEntry> {
        private double targetScroll = 0;
        private boolean isSmoothScrolling = false;
        // NOTE: must not be named "scrolling" - it would shadow AbstractSelectionList.scrolling and break drag logic
        private boolean dragScrolling = false;

        public ConfigOptionListWidget(Minecraft mc, int w, int h, int y, int ih) { super(mc, w, h, y, y + h, ih); }
        @Override public boolean mouseClicked(double mx, double my, int btn) {
            if (mx >= getScrollbarX() && mx <= getScrollbarX() + 6) { dragScrolling = true; return true; }
            return super.mouseClicked(mx, my, btn);
        }
        @Override public boolean mouseReleased(double mx, double my, int btn) { dragScrolling = false; return super.mouseReleased(mx, my, btn); }
        @Override protected void renderBackground(GuiGraphics g) {}
        public void clearEntriesPublic() { this.clearEntries(); }
        @Override public int getRowWidth() { return this.width - 20; }
        public void addEntryPublic(OptionEntry e) { this.addEntry(e); }
        @Override public int getRowLeft() { return 10; }
        public int getScrollbarX() { return this.width - 6; }
        public int listWidth() { return this.width; }
        /** Top of the visible list area (y0 of AbstractSelectionList). */
        public int listTop() { return this.y0; }
        /** Bottom of the visible list area (y1 of AbstractSelectionList). */
        public int listBottom() { return this.y1; }
        public int rowTop(int index) { return this.getRowTop(index); }
        public int rowBottom(int index) { return this.getRowBottom(index); }
        @Override public void updateNarration(NarrationElementOutput n) {}
        @Override protected void renderSelection(GuiGraphics g, int t, int w, int h, int oc, int ic) {}
        @Override public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
            if (dragScrolling) {
                int bh = this.height, ch = this.getMaxScroll() + this.height;
                if (ch > bh) {
                    int sbh = Math.max(32, (int)((float)(bh * bh) / (float)ch));
                    if (sbh > bh) sbh = bh;
                    double d = Math.max(1, this.getMaxScroll() / (double)(bh - sbh));
                    this.setScrollAmount(this.getScrollAmount() + dy * d);
                }
                return true;
            }
            return super.mouseDragged(mx, my, btn, dx, dy);
        }
        @Override
        public void render(GuiGraphics g, int mx, int my, float delta) {
            // enableScissor(x, y, width, height) - height is y1-y0, not the bottom edge
            g.enableScissor(0, this.y0, this.width, this.y1 - this.y0);
            super.renderList(g, mx, my, delta);
            g.disableScissor();
            int sx = this.getScrollbarX(), sy = this.y0, sh = this.height;
            int ch = this.getMaxScroll() + this.height;
            if (ch > this.height) {
                int bh = Math.max(32, (int)((float)(this.height * this.height) / (float)ch));
                if (bh > this.height) bh = this.height;
                int bt = (int)this.getScrollAmount() * (this.height - bh) / this.getMaxScroll() + this.y0;
                if (bt < this.y0) bt = this.y0;
                g.fill(sx, sy, sx + 6, sy + sh, 0x80000000);
                g.fill(sx, bt, sx + 6, bt + bh, 0xA0FFFFFF);
            }
        }
        @Override public boolean mouseScrolled(double mx, double my, double vAmt) {
            this.targetScroll = this.getScrollAmount() - vAmt * 80.0;
            this.targetScroll = Math.max(0, Math.min(this.targetScroll, this.getMaxScroll()));
            this.isSmoothScrolling = true;
            return true;
        }
        public void updateSmoothScroll(float delta) {
            if (isSmoothScrolling) {
                double cur = this.getScrollAmount();
                if (Math.abs(cur - targetScroll) < 0.5) {
                    this.setScrollAmount(targetScroll);
                    isSmoothScrolling = false;
                } else {
                    this.setScrollAmount(Mth.lerp(0.5f * delta, cur, targetScroll));
                }
            } else {
                targetScroll = this.getScrollAmount();
            }
        }
    }

    // ========== Option Entry Types ==========

    private static class SpacerEntry extends OptionEntry {
        public SpacerEntry(int h) {}
        @Override protected boolean shouldHighlight() { return false; }
        @Override public void renderContent(GuiGraphics g, int idx, int y, int x, int ew, int eh, int mx, int my, boolean hovered, float dt) {}
    }

    private static class BooleanOptionEntry extends OptionEntry {
        private final StyledButton button;
        private final Component label;
        private boolean state;
        public BooleanOptionEntry(String key, boolean initial, Consumer<Boolean> onToggle) {
            this.state = initial;
            this.label = Component.translatable(key);
            final StyledButton[] ref = new StyledButton[1];
            ref[0] = new StyledButton(0, 0, 100, 20, Component.translatable(state ? "options.on" : "options.off").withStyle(style -> style.withColor(TextColor.fromRgb(state ? 0xFFB5F0C6 : 0xFFFC887E))), () -> {
                state = !state;
                ref[0].setMessage(Component.translatable(state ? "options.on" : "options.off").withStyle(style -> style.withColor(TextColor.fromRgb(state ? 0xFFB5F0C6 : 0xFFFC887E))));
                onToggle.accept(state);
            });
            this.button = ref[0];
        }
        @Override
        public void renderContent(GuiGraphics g, int idx, int y, int x, int ew, int eh, int mx, int my, boolean hovered, float dt) {
            g.drawString(Minecraft.getInstance().font, label, x, y + 8, 0xFFFFFFFF);
            button.setX(x + ew - 110); button.setY(y + 2); button.setFocused(false);
            button.render(g, mx, my, dt);
        }
        public List<? extends GuiEventListener> children() { return Collections.singletonList(button); }
        public List<? extends NarratableEntry> narratables() { return Collections.singletonList(button); }
    }

    private static class IntegerSliderEntry extends OptionEntry {
        private final StyledSliderWidget slider;
        private final Component label;
        public IntegerSliderEntry(String key, int cur, int min, int max, Consumer<Integer> onChange, boolean soundOnRelease) {
            this.label = Component.translatable(key);
            float minF = min, maxF = max;
            this.slider = new StyledSliderWidget(0, 0, 100, 20, Component.literal(String.valueOf(cur)), (cur - minF) / (maxF - minF), true, !soundOnRelease, soundOnRelease) {
                @Override protected void updateMessage() { this.setMessage(Component.literal(String.valueOf((int)Math.round(minF + this.value * (maxF - minF))))); }
                @Override protected void applyValue() { onChange.accept((int)Math.round(minF + this.value * (maxF - minF))); }
            };
        }
        @Override public void renderContent(GuiGraphics g, int idx, int y, int x, int ew, int eh, int mx, int my, boolean hovered, float dt) {
            g.drawString(Minecraft.getInstance().font, label, x, y + 8, 0xFFFFFFFF);
            slider.setX(x + ew - 110); slider.setY(y + 2);
            slider.render(g, mx, my, dt);
        }
        public List<? extends GuiEventListener> children() { return Collections.singletonList(slider); }
        public List<? extends NarratableEntry> narratables() { return Collections.singletonList(slider); }
    }

    private static class NumericEntry extends OptionEntry {
        private final EditBox field;
        private final Component label;
        public NumericEntry(String key, float initial, Consumer<Float> onChange) {
            this(key, initial, onChange, null);
        }
        public NumericEntry(String key, float initial, Consumer<Float> onChange, Component tooltip) {
            this.label = Component.translatable(key);
            this.field = new EditBox(Minecraft.getInstance().font, 0, 0, 100, 12, Component.empty());
            this.field.setBordered(false);
            this.field.setValue(formatFloat(initial));
            this.field.setResponder(s -> { try { onChange.accept(Float.parseFloat(s)); } catch(Exception ignored){} });
            if (tooltip != null) {
                this.field.setTooltip(Tooltip.create(tooltip));
            }
        }
        private static String formatFloat(float v) {
            if (v == (int)v) return String.valueOf((int)v);
            return String.format("%.1f", v);
        }
        @Override public void renderContent(GuiGraphics g, int idx, int y, int x, int ew, int eh, int mx, int my, boolean hovered, float dt) {
            g.drawString(Minecraft.getInstance().font, label, x, y + 8, 0xFFFFFFFF);
            int bx = x + ew - 110, by = y + 2, bw = 100, bh = 20;
            field.setX(bx + 4); field.setY(by + 6); field.setWidth(bw - 8);
            g.fill(bx, by, bx + bw, by + bh, 0x20000000);
            int bc = (field.isFocused() || field.isMouseOver(mx, my)) ? 0xFFFFFFFF : 0xFFA0A0A0;
            g.fill(bx, by, bx + bw, by + 1, bc); g.fill(bx, by + bh - 1, bx + bw, by + bh, bc);
            g.fill(bx, by, bx + 1, by + bh, bc); g.fill(bx + bw - 1, by, bx + bw, by + bh, bc);
            field.render(g, mx, my, dt);
        }
        public List<? extends GuiEventListener> children() { return Collections.singletonList(field); }
        public List<? extends NarratableEntry> narratables() { return Collections.singletonList(field); }
    }

    private static class TextEntry extends OptionEntry {
        private final EditBox field;
        private final Component label;
        public TextEntry(String key, String initial, Consumer<String> onChange) {
            this.label = Component.translatable(key);
            this.field = new EditBox(Minecraft.getInstance().font, 0, 0, 100, 12, Component.empty());
            this.field.setBordered(false);
            this.field.setMaxLength(20);
            this.field.setValue(initial);
            this.field.setResponder(onChange);
        }
        @Override public void renderContent(GuiGraphics g, int idx, int y, int x, int ew, int eh, int mx, int my, boolean hovered, float dt) {
            g.drawString(Minecraft.getInstance().font, label, x, y + 8, 0xFFFFFFFF);
            int bx = x + ew - 110, by = y + 2, bw = 100, bh = 20;
            field.setX(bx + 4); field.setY(by + 6); field.setWidth(bw - 8);
            g.fill(bx, by, bx + bw, by + bh, 0x20000000);
            int bc = (field.isFocused() || field.isMouseOver(mx, my)) ? 0xFFFFFFFF : 0xFFA0A0A0;
            g.fill(bx, by, bx + bw, by + 1, bc); g.fill(bx, by + bh - 1, bx + bw, by + bh, bc);
            g.fill(bx, by, bx + 1, by + bh, bc); g.fill(bx + bw - 1, by, bx + bw, by + bh, bc);
            field.render(g, mx, my, dt);
        }
        public List<? extends GuiEventListener> children() { return Collections.singletonList(field); }
        public List<? extends NarratableEntry> narratables() { return Collections.singletonList(field); }
    }

    // Mode selector for enhanced/cloud
    private static class ModeSelectorEntry extends OptionEntry {
        private final StyledButton button;
        private final Component label;
        private String mode;
        public ModeSelectorEntry(String key, String initial, Consumer<String> onChange) {
            this.label = Component.translatable(key);
            this.mode = initial;
            final StyledButton[] ref = new StyledButton[1];
            ref[0] = new StyledButton(0, 0, 100, 20, Component.translatable("option.damage-engine.mode." + mode), () -> {
                mode = "enhanced".equals(mode) ? "cloud" : "enhanced";
                ref[0].setMessage(Component.translatable("option.damage-engine.mode." + mode));
                onChange.accept(mode);
            });
            this.button = ref[0];
            this.button.setTooltip(Tooltip.create(Component.translatable("hint.damage-engine.indicatorMode")));
        }
        @Override public void renderContent(GuiGraphics g, int idx, int y, int x, int ew, int eh, int mx, int my, boolean hovered, float dt) {
            g.drawString(Minecraft.getInstance().font, label, x, y + 8, 0xFFFFFFFF);
            button.setX(x + ew - 110); button.setY(y + 2); button.setFocused(false);
            button.render(g, mx, my, dt);
        }
        public List<? extends GuiEventListener> children() { return Collections.singletonList(button); }
        public List<? extends NarratableEntry> narratables() { return Collections.singletonList(button); }
    }

    // Number separator toggle (1,000 / 1000)
    private static class SeparatorToggleEntry extends OptionEntry {
        private final StyledButton button;
        private final Component label;
        private boolean state;
        public SeparatorToggleEntry(String key, boolean initial, Consumer<Boolean> onChange) {
            this.label = Component.translatable(key);
            this.state = initial;
            final StyledButton[] ref = new StyledButton[1];
            ref[0] = new StyledButton(0, 0, 100, 20, Component.literal(state ? "1,000" : "1000"), () -> {
                state = !state;
                ref[0].setMessage(Component.literal(state ? "1,000" : "1000"));
                onChange.accept(state);
            });
            this.button = ref[0];
        }
        @Override public void renderContent(GuiGraphics g, int idx, int y, int x, int ew, int eh, int mx, int my, boolean hovered, float dt) {
            g.drawString(Minecraft.getInstance().font, label, x, y + 8, 0xFFFFFFFF);
            button.setX(x + ew - 110); button.setY(y + 2); button.setFocused(false);
            button.render(g, mx, my, dt);
        }
        public List<? extends GuiEventListener> children() { return Collections.singletonList(button); }
        public List<? extends NarratableEntry> narratables() { return Collections.singletonList(button); }
    }

    private static class HexColorEntry extends OptionEntry {
        private final EditBox field;
        private final Component label;
        private int currentColor;
        public HexColorEntry(String key, int initial, Consumer<Integer> onChange) {
            this.label = Component.translatable(key);
            this.currentColor = initial;
            this.field = new EditBox(Minecraft.getInstance().font, 0, 0, 75, 12, Component.empty());
            this.field.setBordered(false); this.field.setMaxLength(7);
            this.field.setValue("#" + String.format("%06X", initial & 0xFFFFFF));
            this.field.setResponder(s -> {
                try { String hex = s.startsWith("#") ? s.substring(1) : s;
                    currentColor = (int)Long.parseLong(hex, 16) | 0xFF000000;
                    onChange.accept(currentColor); } catch(Exception ignored){}
            });
        }
        @Override public void renderContent(GuiGraphics g, int idx, int y, int x, int ew, int eh, int mx, int my, boolean hovered, float dt) {
            g.drawString(Minecraft.getInstance().font, label, x, y + 8, 0xFFFFFFFF);
            int sx = x + ew - 110, bx = sx, by = y + 2, bw = 75, bh = 20;
            field.setX(bx + 4); field.setY(by + 6); field.setWidth(bw - 8);
            g.fill(bx, by, bx + bw, by + bh, 0x20000000);
            int bc = (field.isFocused() || field.isMouseOver(mx, my)) ? 0xFFFFFFFF : 0xFFA0A0A0;
            g.fill(bx, by, bx + bw, by + 1, bc); g.fill(bx, by + bh - 1, bx + bw, by + bh, bc);
            g.fill(bx, by, bx + 1, by + bh, bc); g.fill(bx + bw - 1, by, bx + bw, by + bh, bc);
            field.render(g, mx, my, dt);
            int ps = 17, px = sx + 80, py = by + 1;
            g.fill(px - 1, py - 1, px + ps + 1, py + ps + 1, 0xFFFFFFFF);
            g.fill(px, py, px + ps, py + ps, 0xFF000000 | (currentColor & 0xFFFFFF));
        }
        public List<? extends GuiEventListener> children() { return Collections.singletonList(field); }
        public List<? extends NarratableEntry> narratables() { return Collections.singletonList(field); }
    }

    private static class ButtonActionEntry extends OptionEntry {
        private final StyledButton button;
        private final Component label;
        public ButtonActionEntry(String labelKey, String buttonKey, Runnable action) {
            this.label = Component.translatable(labelKey);
            this.button = new StyledButton(0, 0, 100, 20, Component.translatable(buttonKey), action);
        }
        @Override public void renderContent(GuiGraphics g, int idx, int y, int x, int ew, int eh, int mx, int my, boolean hovered, float dt) {
            g.drawString(Minecraft.getInstance().font, label, x, y + 8, 0xFFFFFFFF);
            button.setX(x + ew - 110); button.setY(y + 2); button.setFocused(false);
            button.render(g, mx, my, dt);
        }
        public List<? extends GuiEventListener> children() { return Collections.singletonList(button); }
        public List<? extends NarratableEntry> narratables() { return Collections.singletonList(button); }
    }

    private class ExpandableHeaderEntry extends OptionEntry {
        private final PlainTextButton button;
        private final Component label;
        private final String expandKey;
        public ExpandableHeaderEntry(String key, String expandKey, Consumer<Boolean> onToggle) {
            this.label = Component.translatable(key);
            this.expandKey = expandKey;
            boolean expanded = isExpanded(expandKey);
            this.button = new PlainTextButton(0, 0, 20, 20, Component.literal(expanded ? "-" : "+"), () -> {
                expandStates.put(expandKey, !isExpanded(expandKey));
                onToggle.accept(isExpanded(expandKey));
            }, 0xFFFFFFFF, 0xFFFBFB54);
        }
        @Override public void renderContent(GuiGraphics g, int idx, int y, int x, int ew, int eh, int mx, int my, boolean hovered, float dt) {
            boolean isHov = hovered || (mx >= x && mx <= x + ew && my >= y && my <= y + eh);
            int color = isHov ? 0xFFFBFB54 : 0xFFFFFFFF;
            g.drawString(Minecraft.getInstance().font, label, x, y + 8, color);
            button.setX(x + ew - 25); button.setY(y + 2); button.setFocused(false); button.setForceHover(isHov);
            button.render(g, mx, my, dt);
        }
        @Override public boolean mouseClicked(double mx, double my, int btn) {
            if (this.button.mouseClicked(mx, my, btn)) return true;
            expandStates.put(expandKey, !isExpanded(expandKey));
            if (Minecraft.getInstance().screen instanceof DamageConfigScreen s) s.playClickSound();
            refreshOptions();
            return true;
        }
        public List<? extends GuiEventListener> children() { return Collections.singletonList(button); }
        public List<? extends NarratableEntry> narratables() { return Collections.singletonList(button); }
    }

    private class ResetButtonEntry extends OptionEntry {
        private final StyledButton button;
        private final Component label;
        public ResetButtonEntry(String key, Runnable onReset) {
            this.label = Component.translatable(key);
            final int color;
            Component btnText = Component.translatable("gui.reset");
            if (resetButtonState == 1) { color = 0xFFFC887E; btnText = Component.translatable("gui.confirm").append("?"); }
            else if (resetButtonState == 2) { color = 0xFFB5F0C6; btnText = Component.translatable("text.damage-engine.reset_done"); }
            else { color = 0xFFFC887E; }
            this.button = new StyledButton(0, 0, 100, 20, btnText.copy().withStyle(style -> style.withColor(TextColor.fromRgb(color))), () -> handleClick(onReset));
        }
        private void handleClick(Runnable onReset) {
            if (resetButtonState == 0) {
                resetButtonState = 1;
                button.setMessage(Component.translatable("gui.confirm").append("?").withStyle(style -> style.withColor(TextColor.fromRgb(0xFFFC887E))));
                resetButtonActionTime = System.currentTimeMillis();
            } else if (resetButtonState == 1) {
                resetButtonState = 2;
                resetButtonActionTime = System.currentTimeMillis();
                onReset.run();
                button.setMessage(Component.translatable("text.damage-engine.reset_done").withStyle(style -> style.withColor(TextColor.fromRgb(0xFFB5F0C6))));
            }
        }
        @Override public void renderContent(GuiGraphics g, int idx, int y, int x, int ew, int eh, int mx, int my, boolean hovered, float dt) {
            if (resetButtonState == 2 && System.currentTimeMillis() - resetButtonActionTime > 3000) {
                resetButtonState = 0;
                button.setMessage(Component.translatable("gui.reset").withStyle(style -> style.withColor(TextColor.fromRgb(0xFFFC887E))));
            } else if (resetButtonState == 1 && System.currentTimeMillis() - resetButtonActionTime > 5000) {
                resetButtonState = 0;
                button.setMessage(Component.translatable("gui.reset").withStyle(style -> style.withColor(TextColor.fromRgb(0xFFFC887E))));
            }
            g.drawString(Minecraft.getInstance().font, label, x, y + 8, 0xFFFC887E);
            button.setX(x + ew - 110); button.setY(y + 2); button.setFocused(false);
            button.render(g, mx, my, dt);
        }
        public List<? extends GuiEventListener> children() { return Collections.singletonList(button); }
        public List<? extends NarratableEntry> narratables() { return Collections.singletonList(button); }
    }

    private static class DamageThresholdEntry extends OptionEntry {
        private final EditBox valField, colField;
        private final PlainTextButton delBtn;
        private int currentColor;
        public DamageThresholdEntry(DamageEngineConfig.DamageThreshold dt, Runnable onDelete) {
            this.currentColor = dt.color;
            this.valField = new EditBox(Minecraft.getInstance().font, 0, 0, 40, 12, Component.empty());
            this.valField.setBordered(false); this.valField.setValue(String.format("%.0f", dt.threshold));
            this.valField.setResponder(s -> { try { dt.threshold = Float.parseFloat(s); } catch(Exception ignored){} });
            this.colField = new EditBox(Minecraft.getInstance().font, 0, 0, 55, 12, Component.empty());
            this.colField.setBordered(false); this.colField.setMaxLength(7);
            this.colField.setValue("#" + String.format("%06X", dt.color & 0xFFFFFF));
            this.colField.setResponder(s -> { try { String hex = s.startsWith("#") ? s.substring(1) : s;
                currentColor = (int)Long.parseLong(hex, 16) | 0xFF000000; dt.color = currentColor; } catch(Exception ignored){} });
            this.delBtn = new PlainTextButton(0, 0, 20, 20, Component.literal("-"), onDelete, 0xFFFFFFFF, 0xFFFBFB54);
        }
        @Override public void renderContent(GuiGraphics g, int idx, int y, int x, int ew, int eh, int mx, int my, boolean hovered, float dt) {
            int rx = x + ew;
            delBtn.setX(rx - 25); delBtn.setY(y + 2); delBtn.setWidth(20); delBtn.setForceHover(hovered);
            int ps = 18, px = rx - 25 - 5 - ps, py = y + 3;
            int cbw = 55, cbx = px - 5 - cbw, cby = y + 2, cbh = 20;
            colField.setX(cbx + 4); colField.setY(cby + 6); colField.setWidth(cbw - 8);
            int vbw = 40, vbx = cbx - 5 - vbw, vby = y + 2, vbh = 20;
            valField.setX(vbx + 4); valField.setY(vby + 6); valField.setWidth(vbw - 8);
            g.drawString(Minecraft.getInstance().font, Component.translatable("text.damage-engine.damage_reach"), x + 10, y + 8, 0xFFFFFFFF);
            drawTextBox(g, vbx, vby, vbw, vbh, valField, mx, my);
            drawTextBox(g, cbx, cby, cbw, cbh, colField, mx, my);
            valField.render(g, mx, my, dt); colField.render(g, mx, my, dt);
            delBtn.render(g, mx, my, dt);
            g.fill(px - 1, py - 1, px + ps + 1, py + ps + 1, 0xFFFFFFFF);
            g.fill(px, py, px + ps, py + ps, 0xFF000000 | (currentColor & 0xFFFFFF));
        }
        private void drawTextBox(GuiGraphics g, int bx, int by, int bw, int bh, EditBox f, int mx, int my) {
            g.fill(bx, by, bx + bw, by + bh, 0x20000000);
            int bc = (f.isFocused() || f.isMouseOver(mx, my)) ? 0xFFFFFFFF : 0xFFA0A0A0;
            g.fill(bx, by, bx + bw, by + 1, bc); g.fill(bx, by + bh - 1, bx + bw, by + bh, bc);
            g.fill(bx, by, bx + 1, by + bh, bc); g.fill(bx + bw - 1, by, bx + bw, by + bh, bc);
        }
        public List<? extends GuiEventListener> children() { return Arrays.asList(valField, colField, delBtn); }
        public List<? extends NarratableEntry> narratables() { return Arrays.asList(valField, colField, delBtn); }
    }

    private static class AddButtonEntry extends OptionEntry {
        private final PlainTextButton button;
        private final Runnable action;
        public AddButtonEntry(Runnable action) {
            this.action = action;
            this.button = new PlainTextButton(0, 0, 20, 20, Component.literal("+"), action, 0xFFFFFFFF, 0xFFFBFB54);
        }
        @Override public void renderContent(GuiGraphics g, int idx, int y, int x, int ew, int eh, int mx, int my, boolean hovered, float dt) {
            boolean rowHov = mx >= x && mx <= x + ew && my >= y && my <= y + eh;
            button.setX(x + ew - 25); button.setY(y + 2); button.setFocused(false); button.setForceHover(rowHov);
            button.render(g, mx, my, dt);
        }
        @Override public boolean mouseClicked(double mx, double my, int btn) {
            // Entry.isMouseOver() is always false for AbstractSelectionList.Entry - forward to the real button
            return this.button.mouseClicked(mx, my, btn);
        }
        public List<? extends GuiEventListener> children() { return Collections.singletonList(button); }
        public List<? extends NarratableEntry> narratables() { return Collections.singletonList(button); }
    }

    private static class RatingGradeEntry extends OptionEntry {
        private final EditBox scoreField, textField;
        private final PlainTextButton delBtn;
        public RatingGradeEntry(DamageEngineConfig.RatingGrade g, Runnable onDelete) {
            this.scoreField = new EditBox(Minecraft.getInstance().font, 0, 0, 60, 12, Component.empty());
            this.scoreField.setBordered(false); this.scoreField.setValue(String.format("%.0f", g.minScore));
            this.scoreField.setResponder(s -> { try { g.minScore = Float.parseFloat(s); } catch(Exception ignored){} });
            this.textField = new EditBox(Minecraft.getInstance().font, 0, 0, 60, 12, Component.empty());
            this.textField.setBordered(false); this.textField.setMaxLength(4); this.textField.setValue(g.text);
            this.textField.setResponder(s -> { g.text = s; });
            this.delBtn = new PlainTextButton(0, 0, 20, 20, Component.literal("-"), onDelete, 0xFFFFFFFF, 0xFFFBFB54);
        }
        @Override public void renderContent(GuiGraphics g, int idx, int y, int x, int ew, int eh, int mx, int my, boolean hovered, float dt) {
            int rx = x + ew, bw = 60, bh = 20, by = y + (eh - bh) / 2;
            delBtn.setX(rx - 25); delBtn.setY(by); delBtn.setWidth(20); delBtn.setForceHover(hovered);
            int tbx = rx - 25 - 3 - bw;
            textField.setX(tbx + 4); textField.setY(by + 6); textField.setWidth(bw - 8);
            int sbx = tbx - 3 - bw;
            scoreField.setX(sbx + 4); scoreField.setY(by + 6); scoreField.setWidth(bw - 8);
            g.drawString(Minecraft.getInstance().font, Component.literal("≥"), x + 10, y + 8, 0xFFFFFFFF);
            drawBox(g, sbx, by, bw, bh, scoreField, mx, my);
            drawBox(g, tbx, by, bw, bh, textField, mx, my);
            scoreField.render(g, mx, my, dt); textField.render(g, mx, my, dt); delBtn.render(g, mx, my, dt);
        }
        private void drawBox(GuiGraphics g, int bx, int by, int bw, int bh, EditBox f, int mx, int my) {
            g.fill(bx, by, bx + bw, by + bh, 0x20000000);
            int bc = (f.isFocused() || f.isMouseOver(mx, my)) ? 0xFFFFFFFF : 0xFFA0A0A0;
            g.fill(bx, by, bx + bw, by + 1, bc); g.fill(bx, by + bh - 1, bx + bw, by + bh, bc);
            g.fill(bx, by, bx + 1, by + bh, bc); g.fill(bx + bw - 1, by, bx + bw, by + bh, bc);
        }
        public List<? extends GuiEventListener> children() { return Arrays.asList(scoreField, textField, delBtn); }
        public List<? extends NarratableEntry> narratables() { return Arrays.asList(scoreField, textField, delBtn); }
    }

    private static class RatingGradeAppearanceEntry extends OptionEntry {
        private final DamageEngineConfig.RatingGrade grade;
        private final DamageEngineConfig config;
        private final EditBox colField;
        private final StyledButton selectImageBtn;
        private final PlainTextButton resetImageBtn;
        private int currentColor;
        public RatingGradeAppearanceEntry(DamageEngineConfig.RatingGrade g, DamageEngineConfig config) {
            this.grade = g; this.config = config; this.currentColor = g.color;
            this.colField = new EditBox(Minecraft.getInstance().font, 0, 0, 75, 12, Component.empty());
            this.colField.setBordered(false); this.colField.setMaxLength(7);
            this.colField.setValue("#" + String.format("%06X", g.color & 0xFFFFFF));
            this.colField.setResponder(s -> { try { String hex = s.startsWith("#") ? s.substring(1) : s;
                currentColor = (int)Long.parseLong(hex, 16) | 0xFF000000; g.color = currentColor; } catch(Exception ignored){} });
            this.selectImageBtn = new StyledButton(0, 0, 100, 20, Component.translatable("option.damage-engine.select_image"), this::onSelectImage);
            this.resetImageBtn = new PlainTextButton(0, 0, 20, 20, Component.literal("×"), this::onResetImage, 0xFFFC887E, 0xFFFBFB54);
        }
        private void onSelectImage() {
            try {
                java.io.File imagesDir = new java.io.File(Minecraft.getInstance().gameDirectory, "config/damage-engine/images");
                if (!imagesDir.exists()) imagesDir.mkdirs();
                String fileName = "rating_" + grade.index + ".png";
                java.io.File destFile = new java.io.File(imagesDir, fileName);
                if (System.getProperty("os.name").toLowerCase().startsWith("windows")) {
                    ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-Command",
                        "[System.Reflection.Assembly]::LoadWithPartialName('System.Windows.Forms') | Out-Null;" +
                        "$dlg = New-Object System.Windows.Forms.OpenFileDialog;" +
                        "$dlg.Filter = 'PNG Images (*.png)|*.png|All Files (*.*)|*.*';" +
                        "$dlg.Title = 'Choose Image';" +
                        "$dlg.ShowDialog() | Out-Null;" +
                        "$dlg.FileName");
                    pb.redirectErrorStream(true);
                    Process process = pb.start();
                    java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
                    String selectedPath = reader.readLine();
                    process.waitFor();
                    if (selectedPath != null && !selectedPath.trim().isEmpty()) {
                        java.io.File selectedFile = new java.io.File(selectedPath.trim());
                        if (selectedFile.exists()) {
                            java.nio.file.Files.copy(selectedFile.toPath(), destFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            grade.imagePath = "rating_" + grade.index;
                        }
                    }
                }
            } catch (Exception e) { e.printStackTrace(); }
        }
        private void onResetImage() { grade.imagePath = ""; }
        @Override public void renderContent(GuiGraphics g, int idx, int y, int x, int ew, int eh, int mx, int my, boolean hovered, float dt) {
            int rx = x + ew, by = y + 2;
            if (config.ratingUseImages) {
                boolean hasImg = grade.imagePath != null && !grade.imagePath.isEmpty();
                selectImageBtn.setX(rx - 110); selectImageBtn.setY(by); selectImageBtn.setWidth(100); selectImageBtn.visible = true;
                resetImageBtn.visible = hasImg;
                if (hasImg) { resetImageBtn.setX(rx - 110 - 25); resetImageBtn.setY(by); resetImageBtn.setWidth(20); }
                String pt = hasImg ? grade.imagePath : "Not set";
                if (pt.contains("/")) pt = pt.substring(pt.lastIndexOf("/") + 1);
                g.drawString(Minecraft.getInstance().font, Component.literal(grade.text), x + 10, y + 8, 0xFFFFFFFF);
                int mw = 80, tw = Minecraft.getInstance().font.width(pt);
                if (tw > mw) { while (tw > mw - 20 && pt.length() > 4) { pt = pt.substring(0, pt.length() - 1); tw = Minecraft.getInstance().font.width(pt + "..."); } pt += "..."; }
                int px = rx - 110 - 5 - tw - (hasImg ? 25 : 0) - 5;
                if (px < x + 10) px = x + 10;
                g.drawString(Minecraft.getInstance().font, Component.literal(pt), px, y + 8, 0xFFFFFFFF);
                resetImageBtn.render(g, mx, my, dt); selectImageBtn.render(g, mx, my, dt);
            } else {
                selectImageBtn.visible = false;
                int sx = x + ew - 110, ps = 17, px = sx + 80, py = by + 1;
                colField.setX(sx + 4); colField.setY(by + 6); colField.setWidth(75 - 8); colField.visible = true;
                g.drawString(Minecraft.getInstance().font, Component.literal(grade.text), x + 10, y + 8, 0xFFFFFFFF);
                drawBox(g, sx, by, 75, 20, colField, mx, my);
                colField.render(g, mx, my, dt);
                g.fill(px - 1, py - 1, px + ps + 1, py + ps + 1, 0xFFFFFFFF);
                g.fill(px, py, px + ps, py + ps, 0xFF000000 | (currentColor & 0xFFFFFF));
            }
        }
        private void drawBox(GuiGraphics g, int bx, int by, int bw, int bh, EditBox f, int mx, int my) {
            g.fill(bx, by, bx + bw, by + bh, 0x20000000);
            int bc = (f.isFocused() || f.isMouseOver(mx, my)) ? 0xFFFFFFFF : 0xFFA0A0A0;
            g.fill(bx, by, bx + bw, by + 1, bc); g.fill(bx, by + bh - 1, bx + bw, by + bh, bc);
            g.fill(bx, by, bx + 1, by + bh, bc); g.fill(bx + bw - 1, by, bx + bw, by + bh, bc);
        }
        public List<? extends GuiEventListener> children() {
            if (config.ratingUseImages) { List<GuiEventListener> l = new ArrayList<>(); l.add(selectImageBtn); if (resetImageBtn.visible) l.add(resetImageBtn); return l; }
            else return Collections.singletonList(colField);
        }
        public List<? extends NarratableEntry> narratables() {
            if (config.ratingUseImages) { List<NarratableEntry> l = new ArrayList<>(); l.add(selectImageBtn); if (resetImageBtn.visible) l.add(resetImageBtn); return l; }
            else return Collections.singletonList(colField);
        }
    }

    private static class WeightEntry extends OptionEntry {
        private final EditBox input;
        private final Component label, hint;
        private final float[] valueRef;
        public WeightEntry(String key, float current, Consumer<Float> onChange, String hintKey) {
            this.label = Component.translatable(key);
            this.hint = hintKey != null ? Component.translatable(hintKey).withStyle(style -> style.withColor(TextColor.fromRgb(0xFFAAAAAA))) : null;
            this.valueRef = new float[]{current};
            this.input = new EditBox(Minecraft.getInstance().font, 0, 0, 50, 12, Component.empty());
            this.input.setBordered(false);
            this.input.setValue(formatValue(current));
            this.input.setResponder(s -> { try { float v = Float.parseFloat(s); valueRef[0] = v; onChange.accept(v); } catch(Exception ignored){} });
        }
        private static String formatValue(float v) { if (v == (int)v) return String.valueOf((int)v); return String.format("%.1f", v); }
        @Override public void renderContent(GuiGraphics g, int idx, int y, int x, int ew, int eh, int mx, int my, boolean hovered, float dt) {
            if (hint != null) { g.drawString(Minecraft.getInstance().font, label, x, y + 4, 0xFFFFFFFF); g.drawString(Minecraft.getInstance().font, hint, x, y + 14, 0xFFA0A0A0); }
            else g.drawString(Minecraft.getInstance().font, label, x, y + 8, 0xFFFFFFFF);
            int bw = 100, bx = x + ew - 110, by = y + 2, bh = 20;
            g.fill(bx, by, bx + bw, by + bh, 0x20000000);
            int bc = (input.isFocused() || input.isMouseOver(mx, my)) ? 0xFFFFFFFF : 0xFFA0A0A0;
            g.fill(bx, by, bx + bw, by + 1, bc); g.fill(bx, by + bh - 1, bx + bw, by + bh, bc);
            g.fill(bx, by, bx + 1, by + bh, bc); g.fill(bx + bw - 1, by, bx + bw, by + bh, bc);
            input.setX(bx + 4); input.setY(by + 6); input.setWidth(bw - 8);
            input.render(g, mx, my, dt);
        }
        public List<? extends GuiEventListener> children() { return Collections.singletonList(input); }
        public List<? extends NarratableEntry> narratables() { return Collections.singletonList(input); }
    }

    // Keybind entry
    private class KeybindEntry extends OptionEntry {
        private final Component label;
        private final BindingButton button;
        private final KeyMapping keyBinding;
        private List<KeyMapping> conflicts = Collections.emptyList();
        private boolean binding = false;

        private class BindingButton extends AbstractWidget {
            private final Runnable onPress;
            public BindingButton(int x, int y, int w, int h, Component msg, Runnable onPress) {
                super(x, y, w, h, msg); this.onPress = onPress;
            }
            @Override public void playDownSound(net.minecraft.client.sounds.SoundManager sm) {
                if (Minecraft.getInstance().screen instanceof DamageConfigScreen s) s.playClickSound();
                else super.playDownSound(sm);
            }
            @Override public boolean mouseClicked(double mx, double my, int btn) {
                if (this.active && this.visible && btn == 0 && this.isMouseOver(mx, my)) {
                    this.playDownSound(Minecraft.getInstance().getSoundManager()); this.onPress.run(); return true;
                }
                return false;
            }
            @Override protected void renderWidget(GuiGraphics g, int mx, int my, float d) {
                int bc = isHovered() || isBinding() ? 0xFFFFFFFF : 0xFFA0A0A0;
                int x = getX(), y = getY(), w = getWidth(), h = getHeight();
                g.fill(x, y, x + w, y + 1, bc); g.fill(x, y + h - 1, x + w, y + h, bc);
                g.fill(x, y, x + 1, y + h, bc); g.fill(x + w - 1, y, x + w, y + h, bc);
                g.drawCenteredString(Minecraft.getInstance().font, getMessage(), x + w / 2, y + (h - 8) / 2, 0xFFFFFFFF);
            }
            @Override protected void updateWidgetNarration(NarrationElementOutput b) { this.defaultButtonNarrationText(b); }
            @Override public boolean keyPressed(int kc, int sc, int mod) {
                if (isBinding()) {
                    if (kc == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) keyBinding.setKey(com.mojang.blaze3d.platform.InputConstants.UNKNOWN);
                    else keyBinding.setKey(com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(kc));
                    finishBinding(); return true;
                }
                return false;
            }
        }

        public KeybindEntry(String keyName, KeyMapping kb) {
            this.label = Component.translatable(keyName); this.keyBinding = kb;
            this.button = new BindingButton(0, 0, 100, 20, Component.literal("BIND"), () -> setBinding(true));
            updateMessage();
        }
        private void setBinding(boolean v) { this.binding = v; if (Minecraft.getInstance().screen instanceof DamageConfigScreen s) s.setBinding(v); updateMessage(); }
        public boolean isBinding() { return binding; }
        public BindingButton getButton() { return button; }
        private void finishBinding() { setBinding(false); Minecraft.getInstance().options.save(); KeyMapping.resetMapping(); }
        public void bindMouse(int btn) { keyBinding.setKey(com.mojang.blaze3d.platform.InputConstants.Type.MOUSE.getOrCreate(btn)); finishBinding(); }
        private void updateMessage() {
            Component text;
            List<KeyMapping> foundConflicts = new ArrayList<>();
            if (binding) {
                text = Component.literal("> ").withStyle(style -> style.withColor(TextColor.fromRgb(0xFFFFFF55)))
                    .append((keyBinding.isUnbound()
                        ? Component.translatable("key.damage_engine.not_bound")
                        : keyBinding.getTranslatedKeyMessage().copy())
                        .withStyle(net.minecraft.ChatFormatting.UNDERLINE).withStyle(style -> style.withColor(TextColor.fromRgb(0xFFFFFFFF))))
                    .append(Component.literal(" <").withStyle(style -> style.withColor(TextColor.fromRgb(0xFFFFFF55))));
            } else {
                if (keyBinding.isUnbound()) text = Component.translatable("key.damage_engine.not_bound").withStyle(style -> style.withColor(TextColor.fromRgb(0xFFFFFFFF)));
                else {
                    for (KeyMapping k : Minecraft.getInstance().options.keyMappings) if (k != keyBinding && k.same(keyBinding)) foundConflicts.add(k);
                    text = foundConflicts.isEmpty() ? keyBinding.getTranslatedKeyMessage().copy() :
                        Component.literal("[ ").withStyle(style -> style.withColor(TextColor.fromRgb(0xFFFFFF55))).append(keyBinding.getTranslatedKeyMessage().copy().withStyle(style -> style.withColor(TextColor.fromRgb(0xFFFFFFFF)))).append(Component.literal(" ]").withStyle(style -> style.withColor(TextColor.fromRgb(0xFFFFFF55))));
                }
            }
            conflicts = foundConflicts; button.setMessage(text);
            if (!conflicts.isEmpty()) {
                net.minecraft.network.chat.MutableComponent tt = Component.translatable("text.damage-engine.key_conflict").append("\n");
                for (int i = 0; i < conflicts.size(); i++) {
                    if (i > 0) tt.append(Component.literal(", "));
                    tt.append(Component.translatable(conflicts.get(i).getName()));
                }
                button.setTooltip(Tooltip.create(tt));
            } else button.setTooltip(null);
        }
        @Override public void renderContent(GuiGraphics g, int idx, int y, int x, int ew, int eh, int mx, int my, boolean hovered, float dt) {
            if (!binding) updateMessage();
            g.drawString(Minecraft.getInstance().font, label, x, y + 8, 0xFFFFFFFF);
            button.setX(x + ew - 110); button.setY(y + 2); button.setWidth(100); button.setFocused(false);
            button.render(g, mx, my, dt);
        }
        public List<? extends GuiEventListener> children() { return Collections.singletonList(button); }
        public List<? extends NarratableEntry> narratables() { return Collections.singletonList(button); }
    }

    // Slider widget
    private static class StyledSliderWidget extends AbstractSliderButton {
        private final boolean showValue, soundOnPress, soundOnRelease;
        private boolean pendingReleaseSound;
        private double valueBeforeInteraction;
        // Track an in-progress drag: MC's AbstractSliderButton only updates while the
        // mouse is over the widget, so a press-and-hold drag that moves past the edge
        // of the 100px slider bar stops following. We keep dragging until release.
        private boolean sliderDragging;
        public StyledSliderWidget(int x, int y, int w, int h, Component text, double value, boolean showValue, boolean soundOnPress, boolean soundOnRelease) {
            super(x, y, w, h, text, value);
            this.showValue = showValue; this.soundOnPress = soundOnPress; this.soundOnRelease = soundOnRelease;
        }
        @Override protected void updateMessage() {}
        @Override protected void applyValue() {}
        private void playClick() {
            if (Minecraft.getInstance().screen instanceof DamageConfigScreen s) s.playClickSound();
            else super.playDownSound(Minecraft.getInstance().getSoundManager());
        }
        @Override public void playDownSound(net.minecraft.client.sounds.SoundManager sm) { if (!soundOnPress) return; playClick(); }
        @Override public boolean mouseClicked(double mx, double my, int btn) {
            double before = this.value;
            boolean h = super.mouseClicked(mx, my, btn);
            if (h) { sliderDragging = true; if (soundOnRelease) { pendingReleaseSound = true; valueBeforeInteraction = before; } }
            return h;
        }
        @Override public boolean mouseReleased(double mx, double my, int btn) {
            sliderDragging = false;
            boolean h = super.mouseReleased(mx, my, btn);
            if (soundOnRelease && pendingReleaseSound) { pendingReleaseSound = false; if (Math.abs(this.value - valueBeforeInteraction) > 1.0E-9) playClick(); }
            return h;
        }
        @Override public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
            // Keep following the mouse for the whole press-and-hold drag, even when the
            // cursor leaves the slider bar (MC's default requires isMouseOver).
            if (sliderDragging && btn == 0) {
                updateValueFromMouse(mx);
                return true;
            }
            return super.mouseDragged(mx, my, btn, dx, dy);
        }
        /** Drag the slider to the given mouse X (screen coords), regardless of hover. */
        public void updateValueFromMouse(double mx) {
            // setValue/setValueFromMouse are private in 1.20.1; replicate the formula.
            this.value = Mth.clamp((mx - (double) (this.getX() + 4)) / (double) (this.getWidth() - 8), 0.0, 1.0);
            this.updateMessage();
            this.applyValue();
        }
        @Override public void renderWidget(GuiGraphics g, int mx, int my, float d) {
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            g.fill(x, y, x + w, y + h, 0x20000000);
            int bc = isHovered() ? 0xFFFFFFFF : 0xFFA0A0A0;
            g.fill(x, y, x + w, y + 1, bc); g.fill(x, y + h - 1, x + w, y + h, bc);
            g.fill(x, y, x + 1, y + h, bc); g.fill(x + w - 1, y, x + w, y + h, bc);
            int hw = 8, hx = x + (int)(this.value * (double)(w - hw));
            g.fill(hx, y, hx + hw, y + h, 0xFF000000);
            int hbc = (isHovered() || isFocused()) ? 0xFFFFFFFF : 0xFFCCCCCC;
            g.fill(hx, y, hx + hw, y + 1, hbc); g.fill(hx, y + h - 1, hx + hw, y + h, hbc);
            g.fill(hx, y, hx + 1, y + h, hbc); g.fill(hx + hw - 1, y, hx + hw, y + h, hbc);
            if (showValue) g.drawCenteredString(Minecraft.getInstance().font, getMessage(), x + w / 2, y + (h - 8) / 2, 0xFFFFFFFF);
        }
    }
}