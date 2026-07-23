package damage.engine.client.gui;

import damage.engine.ClientKeybindings;
import damage.engine.DamageEngineClient;
import damage.engine.DamageEngineConfig;
import damage.engine.hud.DamageHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.network.chat.MutableComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

public class DamageConfigScreen extends Screen {
    private final Screen parent;
    private final DamageEngineConfig config;
    
    private CategoryListWidget categoryList;
    private ConfigOptionListWidget optionList;
    
    private final List<CategoryEntry> categories = new ArrayList<>();
    private int selectedCategoryIndex = 0;
    
    private double lastOptionScroll = 0;
    private double lastCategoryScroll = 0;
    
    private boolean expandDamageColors = false;
    private boolean expandInfoConfig = false;
    private boolean expandDamageDisplayConfig = false;
    private boolean expandDamageDisplayAppearance = false;
    private boolean expandInfoAppearance = false;
    private boolean expandDamageIndicatorColors = false;
    private boolean expandRatingConfig = false;
    private boolean expandRatingPoints = false;
    private boolean expandRatingGrades = false;
    private boolean expandRatingAppearance = false;
    private boolean expandDebugMode = false;
    
    private int resetButtonState = 0;
    private long resetButtonActionTime = 0;
    
    private boolean isNavigating = false;
    
    private boolean isBinding = false;
    private double selectedCategoryY = 0;
    private double targetCategoryY = 0;


    public DamageConfigScreen(Screen parent) {
        super(Component.translatable("title.damage-engine.config"));
        this.parent = parent;
        this.config = DamageEngineConfig.getInstance();
    }

    private final List<StyledButton> footerButtons = new ArrayList<>();

    private static class PlainTextButton extends AbstractWidget {
        private final Runnable onPress;
        private final int hoverColor;
        private int defaultColor;
        private boolean forceHover = false;

        public PlainTextButton(int x, int y, int width, int height, Component message, Runnable onPress, int defaultColor, int hoverColor) {
            super(x, y, width, height, message);
            this.onPress = onPress;
            this.defaultColor = defaultColor;
            this.hoverColor = hoverColor;
        }
        
        public void setForceHover(boolean forceHover) {
            this.forceHover = forceHover;
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
            int color = (isHovered() || forceHover) ? hoverColor : defaultColor;
            guiGraphics.drawCenteredString(Minecraft.getInstance().font, getMessage(), getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, color);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput builder) {
            this.defaultButtonNarrationText(builder);
        }
        @Override
        public void playDownSound(net.minecraft.client.sounds.SoundManager soundManager) {
            if (Minecraft.getInstance().screen instanceof DamageConfigScreen s) {
                s.playClickSound();
            } else {
                super.playDownSound(soundManager);
            }
        }
    }
    
    static class StyledButton extends AbstractWidget {
        private final Runnable onPress;

        public StyledButton(int x, int y, int width, int height, Component message, Runnable onPress) {
            super(x, y, width, height, message);
            this.onPress = onPress;
        }
        
        @Override
        public void playDownSound(net.minecraft.client.sounds.SoundManager soundManager) {
            if (Minecraft.getInstance().screen instanceof DamageConfigScreen s) {
                s.playClickSound();
            } else if (Minecraft.getInstance().screen instanceof HudEditorScreen s) {
                s.playClickSound();
            } else {
                super.playDownSound(soundManager);
            }
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
            guiGraphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0x20000000);
            
            int borderColor = isHovered() ? 0xFFFFFFFF : 0xFFA0A0A0;
            int x = getX(); int y = getY(); int w = getWidth(); int h = getHeight();
            guiGraphics.fill(x, y, x + w, y + 1, borderColor); 
            guiGraphics.fill(x, y + h - 1, x + w, y + h, borderColor); 
            guiGraphics.fill(x, y, x + 1, y + h, borderColor); 
            guiGraphics.fill(x + w - 1, y, x + w, y + h, borderColor); 
            
            guiGraphics.drawCenteredString(Minecraft.getInstance().font, getMessage(), getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, 0xFFFFFFFF);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput builder) {
            this.defaultButtonNarrationText(builder);
        }
    }

    @Override
    protected void init() {
        try {
            if (optionList != null) lastOptionScroll = optionList.getScrollAmount();
            if (categoryList != null) lastCategoryScroll = categoryList.getScrollAmount();

            this.clearWidgets();
            categories.clear();
            footerButtons.clear();
            
            int leftWidth = 120;
            int footerY = this.height - 35;
            int topY = 0; 
            
            int listHeight = footerY; 
            
            categoryList = new CategoryListWidget(this.minecraft, leftWidth, listHeight, topY, 25);
            categoryList.setX(0);
            
            categoryList.addEntryPublic(new CategorySpacerEntry(this)); 
            
            optionList = new ConfigOptionListWidget(this.minecraft, this.width - leftWidth, listHeight, topY, 26);
            optionList.setX(leftWidth);
            
            initCategories();
            
            initOptionEntries();
            
            if (categoryList != null) categoryList.setScrollAmount(lastCategoryScroll);
            if (optionList != null) optionList.setScrollAmount(lastOptionScroll);
            
            // 初始化选中分类的Y位置
            if (!categories.isEmpty()) {
                // 手动计算CategoryEntry的Y位置
                int y = categoryList.getY() + 4;
                // 跳过CategorySpacerEntry
                y += 25;
                for (int i = 0; i < categories.size(); i++) {
                    CategoryEntry cat = categories.get(i);
                    cat.lastY = y;
                    y += 25; // 每个分类项的高度
                }
                
                CategoryEntry cat = categories.get(selectedCategoryIndex);
                targetCategoryY = cat.getY();
                selectedCategoryY = targetCategoryY;
            }
            
            this.addRenderableWidget(categoryList);
            this.addRenderableWidget(optionList);
            
            int buttonWidth = 80;
            int buttonY = footerY + 5;
            
            StyledButton saveBtn = new StyledButton(this.width - buttonWidth - 10, buttonY, buttonWidth, 20, Component.translatable("button.damage-engine.save_close").withColor(0xFFB7F3C8), () -> {
                config.save();
                this.minecraft.setScreen(parent);
            });
            footerButtons.add(saveBtn);
            this.addRenderableWidget(saveBtn);
            
            StyledButton cancelBtn = new StyledButton(this.width - buttonWidth * 2 - 20, buttonY, buttonWidth, 20, Component.translatable("gui.cancel").withColor(0xFFFC887E), () -> {
                config.load();
                this.minecraft.setScreen(parent);
            });
            footerButtons.add(cancelBtn);
            this.addRenderableWidget(cancelBtn);

            // 左下角预览开关
            final StyledButton[] previewBtnRef = new StyledButton[1];
            previewBtnRef[0] = new StyledButton(5, buttonY, 80, 20,
                Component.translatable("text.damage-engine.preview").append(": ").append(Component.translatable(config.previewEnabled ? "options.on" : "options.off").withColor(config.previewEnabled ? 0xFFB5F0C6 : 0xFFFC887E)), () -> {
                config.previewEnabled = !config.previewEnabled;
                previewBtnRef[0].setMessage(Component.translatable("text.damage-engine.preview").append(": ").append(Component.translatable(config.previewEnabled ? "options.on" : "options.off").withColor(config.previewEnabled ? 0xFFB5F0C6 : 0xFFFC887E)));
            });
            footerButtons.add(previewBtnRef[0]);
            this.addRenderableWidget(previewBtnRef[0]);
        } catch (Exception e) {
            DamageEngineClient.LOGGER.error("Failed to initialize config screen: ", e);
        }
    }
    
    private void refreshOptions() {
        if (optionList == null) return;
        double scroll = optionList.getScrollAmount();
        optionList.clearEntriesPublic();
        initOptionEntries();
        optionList.setScrollAmount(scroll);
    }
    
    private void initCategories() {
        categories.clear();
        categoryList.clearEntriesPublic();
        
        categoryList.addEntryPublic(new CategorySpacerEntry(this)); 
        
        addCategory("category.damage_engine.general", 0);
        addCategory("category.damage_engine.appearance", 1);
        addCategory("category.damage_engine.keybinds", 2);
        addCategory("category.damage_engine.other", 3);
    }

    private void initOptionEntries() {
        addHeader("category.damage_engine.general");
        
        addOption(new BooleanOptionEntry("option.damage-engine.showDamage", config.showDamage, v -> config.showDamage = v));
        
        addOption(new ExpandableHeaderEntry("option.damage-engine.info_config", expandInfoConfig, v -> {
            expandInfoConfig = v;
            refreshOptions();
        }));
        
        if (expandInfoConfig) {
            addOption(new BooleanOptionEntry("option.damage-engine.showInfo", config.showInfo, v -> config.showInfo = v));
            addOption(new NumericEntry("option.damage-engine.infoTrackTime", config.infoTrackTime, v -> config.infoTrackTime = v));
        }
        
        addOption(new ExpandableHeaderEntry("option.damage-engine.damage_display_config", expandDamageDisplayConfig, v -> {
            expandDamageDisplayConfig = v;
            refreshOptions();
        }));
        
        if (expandDamageDisplayConfig) {
            addOption(new BooleanOptionEntry("option.damage-engine.showDamageDisplay", config.showDamageDisplay, v -> config.showDamageDisplay = v));
            addOption(new BooleanOptionEntry("option.damage-engine.resetEnabled", config.resetEnabled, v -> config.resetEnabled = v));
            addOption(new NumericEntry("option.damage-engine.resetTime", config.resetTime, v -> config.resetTime = v));
            addOption(new BooleanOptionEntry("option.damage-engine.showProgressBar", config.showProgressBar, v -> config.showProgressBar = v));
            addOption(new BooleanOptionEntry("option.damage-engine.showCombo", config.showCombo, v -> config.showCombo = v));
            addOption(new BooleanOptionEntry("option.damage-engine.showDamageHistory", config.showDamageHistory, v -> config.showDamageHistory = v));
            addOption(new BooleanOptionEntry("option.damage-engine.showDamageIndicator", config.showDamageIndicator, v -> config.showDamageIndicator = v));
            addOption(new BooleanOptionEntry("option.damage-engine.showKillIndicator", config.showKillIndicator, v -> config.showKillIndicator = v));
            
            addOption(new NumericEntry("option.damage-engine.historyDisappearanceTime", config.historyDisappearanceTime, v -> config.historyDisappearanceTime = v));
            addOption(new IntegerSliderEntry("option.damage-engine.historyLimit", config.historyLimit, 1, 50, v -> config.historyLimit = v, true));
            addOption(new IntegerSliderEntry("option.damage-engine.decimalPlaces", config.decimalPlaces, 0, 10, v -> config.decimalPlaces = v, true));
        }
        
        // Rating config at General level
        addOption(new ExpandableHeaderEntry("option.damage-engine.rating_config", expandRatingConfig, v -> {
            expandRatingConfig = v;
            refreshOptions();
        }));
        if (expandRatingConfig) {
                addOption(new BooleanOptionEntry("option.damage-engine.showRating", config.showRating, v -> config.showRating = v));
                addOption(new NumericEntry("option.damage-engine.ratingResetTime", config.ratingResetTime, v -> config.ratingResetTime = v));
            
            addOption(new ExpandableHeaderEntry("option.damage-engine.rating_points", expandRatingPoints, v -> {
                expandRatingPoints = v;
                refreshOptions();
            }));
            if (expandRatingPoints) {
                    addOption(new WeightEntry("option.damage-engine.comboPoints", config.comboPoints, v -> config.comboPoints = v, "hint.damage-engine.comboPoints"));
                    addOption(new WeightEntry("option.damage-engine.hitPoints", config.hitPoints, v -> config.hitPoints = v, null));
                    addOption(new WeightEntry("option.damage-engine.critPoints", config.critPoints, v -> config.critPoints = v, null));
                }
            
            addOption(new ExpandableHeaderEntry("option.damage-engine.rating_grades", expandRatingGrades, v -> {
                expandRatingGrades = v;
                refreshOptions();
            }));
            if (expandRatingGrades) {
                config.ratingGrades.sort((a, b) -> Float.compare(b.minScore, a.minScore));
                for (int i = 0; i < config.ratingGrades.size(); i++) {
                    config.ratingGrades.get(i).index = i + 1;
                }
                for (DamageEngineConfig.RatingGrade g : new ArrayList<>(config.ratingGrades)) {
                    addOption(new RatingGradeEntry(g, () -> {
                        config.ratingGrades.remove(g);
                        refreshOptions();
                    }));
                }
                addOption(new AddButtonEntry(() -> {
                    int nextIndex = config.ratingGrades.size() + 1;
                    config.ratingGrades.add(new DamageEngineConfig.RatingGrade(0f, "F", 0xFFFFFFFF, nextIndex));
                    refreshOptions();
                }));
            }
        }
        
        addHeader("category.damage_engine.appearance");
        addOption(new ButtonActionEntry("option.damage-engine.edit_pos_label", "button.damage-engine.adjust", () -> {
            playClickSound();
            this.minecraft.setScreen(new HudEditorScreen(this));
        }));

        addOption(new ExpandableHeaderEntry("option.damage-engine.info_appearance", expandInfoAppearance, v -> {
            expandInfoAppearance = v;
            refreshOptions();
        }));
        
        if (expandInfoAppearance) {
            addOption(new BooleanOptionEntry("option.damage-engine.infoNoRoundedBorder", config.infoNoRoundedBorder, v -> config.infoNoRoundedBorder = v));
            addOption(new IntegerSliderEntry("option.damage-engine.infoBackgroundOpacity", config.infoBackgroundOpacity, 0, 100, v -> config.infoBackgroundOpacity = v, true));
            addOption(new HexColorEntry("option.damage-engine.infoBackgroundColor", config.infoBackgroundColor, v -> config.infoBackgroundColor = v));
            addOption(new HexColorEntry("option.damage-engine.infoBarColor", config.infoBarColor, v -> config.infoBarColor = v));
            addOption(new HexColorEntry("option.damage-engine.infoBarHealColor", config.infoBarHealColor, v -> config.infoBarHealColor = v));
            addOption(new HexColorEntry("option.damage-engine.infoBarDamageColor", config.infoBarDamageColor, v -> config.infoBarDamageColor = v));
        }
        
        addOption(new ExpandableHeaderEntry("option.damage-engine.damage_display_appearance", expandDamageDisplayAppearance, v -> {
            expandDamageDisplayAppearance = v;
            refreshOptions();
        }));
        
        if (expandDamageDisplayAppearance) {
            addOption(new HexColorEntry("option.damage-engine.progressBarColor", config.progressBarColor, v -> config.progressBarColor = v));
            addOption(new HexColorEntry("option.damage-engine.comboColor", config.comboColor, v -> config.comboColor = v));
            addOption(new HexColorEntry("option.damage-engine.historyColor", config.historyColor, v -> config.historyColor = v));
            addOption(new HexColorEntry("option.damage-engine.normalColor", config.normalColor, v -> config.normalColor = v));
            addOption(new HexColorEntry("option.damage-engine.critColor", config.critColor, v -> config.critColor = v));
            
            addOption(new ExpandableHeaderEntry("option.damage-engine.total_damage_colors", expandDamageColors, v -> {
                expandDamageColors = v;
                refreshOptions();
            }));
            
            if (expandDamageColors) {
                 config.damageThresholds.sort((a, b) -> Float.compare(a.threshold, b.threshold));
                 
                 for (DamageEngineConfig.DamageThreshold dt : new ArrayList<>(config.damageThresholds)) {
                     addOption(new DamageThresholdEntry(dt, () -> {
                         config.damageThresholds.remove(dt);
                         refreshOptions();
                     }));
                 }
                 
                 addOption(new AddButtonEntry(() -> {
                     float nextVal = 0f;
                     if (!config.damageThresholds.isEmpty()) {
                         nextVal = config.damageThresholds.get(config.damageThresholds.size() - 1).threshold + 50f;
                     }
                     config.damageThresholds.add(new DamageEngineConfig.DamageThreshold(nextVal, 0xFFFFFFFF));
                     refreshOptions();
                 }));
            }
            
            addOption(new ExpandableHeaderEntry("option.damage-engine.damage_indicator_colors", expandDamageIndicatorColors, v -> {
                expandDamageIndicatorColors = v;
                refreshOptions();
            }));
            
            if (expandDamageIndicatorColors) {
                addOption(new HexColorEntry("option.damage-engine.damageIndicatorNormalColor", config.damageIndicatorNormalColor, v -> config.damageIndicatorNormalColor = v));
                addOption(new HexColorEntry("option.damage-engine.damageIndicatorCritColor", config.damageIndicatorCritColor, v -> config.damageIndicatorCritColor = v));
                addOption(new HexColorEntry("option.damage-engine.damageIndicatorKillColor", config.damageIndicatorKillColor, v -> config.damageIndicatorKillColor = v));
            }
        }
        
        addOption(new ExpandableHeaderEntry("option.damage-engine.rating_appearance", expandRatingAppearance, v -> {
            expandRatingAppearance = v;
            refreshOptions();
        }));
        if (expandRatingAppearance) {
            addOption(new BooleanOptionEntry("option.damage-engine.rating_use_images", config.ratingUseImages, v -> {
                config.ratingUseImages = v;
            }));
            config.ratingGrades.sort((a, b) -> Float.compare(b.minScore, a.minScore));
            for (int i = 0; i < config.ratingGrades.size(); i++) {
                config.ratingGrades.get(i).index = i + 1;
            }
            for (DamageEngineConfig.RatingGrade g : new ArrayList<>(config.ratingGrades)) {
                addOption(new RatingGradeAppearanceEntry(g, config));
            }
        }
        
        addHeader("category.damage_engine.keybinds");
        
        if (ClientKeybindings.configKeyBinding != null) {
            addOption(new KeybindEntry("key.damage_engine.config", ClientKeybindings.configKeyBinding));
        }
        if (ClientKeybindings.toggleHudKeyBinding != null) {
            addOption(new KeybindEntry("key.damage_engine.toggle_hud", ClientKeybindings.toggleHudKeyBinding));
        }
        if (ClientKeybindings.clearDamageKeyBinding != null) {
            addOption(new KeybindEntry("key.damage_engine.clear_damage", ClientKeybindings.clearDamageKeyBinding));
        }
        
        addHeader("category.damage_engine.other");
        addOption(new BooleanOptionEntry("option.damage-engine.hideOnF1", config.hideOnF1, v -> config.hideOnF1 = v));
        addOption(new ExpandableHeaderEntry("option.damage-engine.debugMode", expandDebugMode, v -> {
            expandDebugMode = v;
            refreshOptions();
        }));
        if (expandDebugMode) {
            addOption(new BooleanOptionEntry("option.damage-engine.debugShowDamageInfo", config.debugShowDamageInfo, v -> config.debugShowDamageInfo = v));
            addOption(new BooleanOptionEntry("option.damage-engine.debugShowRating", config.debugShowRating, v -> config.debugShowRating = v));
        }
        
        addOption(new ResetButtonEntry("option.damage-engine.reset", () -> {
            config.resetToDefaults();
            KeyMapping.resetMapping();
            this.minecraft.options.save();
            this.init();
        }));
        
        addOption(new SpacerEntry(30));
        addOption(new SpacerEntry(30));
    }
    
    private void addCategory(String key, int id) {
        CategoryEntry cat = new CategoryEntry(this, Component.translatable(key), id, 0);
        categoryList.addEntryPublic(cat);
        categories.add(cat);
    }
    
    private void addHeader(String key) {
        int currentIndex = optionList.children().size();
        
        String keyString = Component.translatable(key).getString();
        
        for (CategoryEntry cat : categories) {
            if (cat.text.getString().equals(keyString)) {
                cat.targetIndex = currentIndex;
                break;
            }
        }
        
        optionList.addEntryPublic(new HeaderEntry(Component.translatable(key)));
    }
    
    private void addOption(OptionEntry entry) {
        optionList.addEntryPublic(entry);
    }
    
    private void selectCategory(int index) {
        selectedCategoryIndex = index;
        if (index >= 0 && index < categories.size()) {
            CategoryEntry cat = categories.get(index);
            // 计算目标Y位置
            targetCategoryY = cat.getY();
            
            double y = 0;
            for (int i = 0; i < cat.targetIndex; i++) {
                y += 26;
            }
            isNavigating = true;
            optionList.setTargetScroll(y);
        }
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
        } catch (Exception e) {
            DamageEngineClient.LOGGER.error("Failed to play sound", e);
        }
    }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (isBinding) {
                if (optionList != null) {
                    for (GuiEventListener widget : optionList.children()) {
                        if (widget instanceof KeybindEntry ke) {
                            if (ke.isBinding()) {
                                if (ke.button.keyPressed(keyCode, scanCode, modifiers)) {
                                    return true;
                                }
                            }
                        }
                    }
                }
                if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                    setBinding(false);
                    return true;
                }
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (optionList != null) {
            for (GuiEventListener widget : optionList.children()) {
                if (widget instanceof OptionEntry entry) {
                    for (GuiEventListener child : entry.children()) {
                        if (child instanceof EditBox tf) {
                            tf.setFocused(false);
                        }
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
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        super.onClose();
    }
    
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        this.renderBackground(guiGraphics, mouseX, mouseY, delta);
        
        if (categoryList != null) categoryList.render(guiGraphics, mouseX, mouseY, delta);
        if (optionList != null) {
            optionList.render(guiGraphics, mouseX, mouseY, delta);
            optionList.updateSmoothScroll(delta);
        }
        
        if (optionList != null && !isNavigating) {
            int hoveredIndex = optionList.getHoveredEntryIndex(mouseX, mouseY);
            if (hoveredIndex != -1) {
                 int bestCatId = -1;
                 for (CategoryEntry cat : categories) {
                     if (cat.targetIndex <= hoveredIndex) {
                         bestCatId = cat.id;
                     } else {
                         break;
                     }
                 }
                 if (bestCatId != -1 && bestCatId != selectedCategoryIndex) {
                     selectedCategoryIndex = bestCatId;
                     // 更新目标Y位置
                     if (bestCatId >= 0 && bestCatId < categories.size()) {
                         CategoryEntry cat = categories.get(bestCatId);
                         targetCategoryY = cat.getY();
                     }
                 }
            }
        }
        
        // 平滑更新选中分类的Y位置
        selectedCategoryY = Mth.lerp(0.15f, selectedCategoryY, targetCategoryY);
        
        int leftWidth = 120;
        int footerY = this.height - 35;
        
        guiGraphics.fill(leftWidth - 1, 0, leftWidth, footerY, 0xFF555555);
        guiGraphics.fill(0, footerY - 1, this.width, footerY, 0xFF555555);
        
        guiGraphics.fill(0, 25, leftWidth, 26, 0xFF555555);
        
        // 绘制平滑移动的选中亮条
        if (selectedCategoryY > 0) {
            guiGraphics.fill(0, (int)selectedCategoryY + 2, 2, (int)selectedCategoryY + 25 - 2, 0xFFFFFFFF);
        }
        
        for (StyledButton btn : footerButtons) {
            btn.render(guiGraphics, mouseX, mouseY, delta);
        }
        
        guiGraphics.drawString(this.font, Component.translatable("category.damage_engine"), 10, 10, 0xFFFFFFFF);

        // 客户端模式提示（左侧栏分类底部）
        if (DamageEngineClient.serverModChecked && !DamageEngineClient.serverHasMod) {
            String[] lines = Component.translatable("text.damage-engine.client_mode_hint").getString().split("\n");
            int hintY = 155; // 分类列表底部
            for (int i = 0; i < lines.length; i++) {
                guiGraphics.drawString(this.font, Component.literal(lines[i]).withColor(0xFFFC887E), 5, hintY + i * 12, 0xFFFC887E);
            }
        }
        
        if (config.previewEnabled) {
            new DamageHud().renderPreview(guiGraphics, 30, this.height - 30);
        }
        

    }
    

    
    
    private static class CategorySpacerEntry extends CategoryEntry {
        public CategorySpacerEntry(DamageConfigScreen screen) {
            super(screen, Component.empty(), -1, -1);
        }
        @Override public void render(GuiGraphics guiGraphics, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {}
        public boolean mouseClicked(double mouseX, double mouseY, int button) { return false; }
    }

    static class CategoryEntry extends AbstractSelectionList.Entry<CategoryEntry> {
        private final Component text;
        private final int id;
        private int targetIndex;
        private final Minecraft client = Minecraft.getInstance();
        private final DamageConfigScreen screen;
        private int lastY = 0;
        
        public CategoryEntry(DamageConfigScreen screen, Component text, int id, int targetIndex) { 
            this.screen = screen;
            this.text = text; 
            this.id = id; 
            this.targetIndex = targetIndex;
        }
        
        public int getY() {
            return lastY;
        }
        
        @Override
        public void render(GuiGraphics guiGraphics, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            lastY = y;
            
            guiGraphics.drawString(client.font, text, x + 10, y + 8, 0xFFFFFFFF);
        }
        
        public List<? extends GuiEventListener> children() { return Collections.emptyList(); }
        public List<? extends NarratableEntry> narratables() { return Collections.emptyList(); }
        
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            screen.selectCategory(id);
            screen.playClickSound();
            return true;
        }
    }
    
    private class CategoryListWidget extends AbstractSelectionList<CategoryEntry> {
        public CategoryListWidget(Minecraft client, int width, int height, int y, int itemHeight) {
            super(client, width, height, y, 25);
        }
        
        @Override
        protected void renderListBackground(GuiGraphics guiGraphics) {
        }
        public void clearEntriesPublic() { this.clearEntries(); }
        
        public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
             this.enableScissor(guiGraphics);
             this.renderListItems(guiGraphics, mouseX, mouseY, delta);
             guiGraphics.disableScissor();
        }

        @Override public int getRowWidth() { return 100; }
        public void addEntryPublic(CategoryEntry entry) { this.addEntry(entry); }
        @Override public int getRowLeft() { return 0; }
        @Override protected void renderListSeparators(GuiGraphics guiGraphics) {}
        @Override protected void renderSelection(GuiGraphics guiGraphics, int top, int width, int height, int outerColor, int innerColor) {}
        @Override public void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {}
    }
    
    abstract static class OptionEntry extends AbstractSelectionList.Entry<OptionEntry> {
        private double hoverAnimationProgress = 0;
        
        @Override
        public void render(GuiGraphics guiGraphics, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            DamageConfigScreen screen = (DamageConfigScreen) Minecraft.getInstance().screen;
            if (screen == null || screen.optionList == null) return;
            
            int listLeft = screen.optionList.getX();
        int listRight = screen.optionList.getRight();
        boolean isHovered = mouseX >= listLeft && mouseX <= listRight && mouseY >= y && mouseY < y + entryHeight;
        
        if (isHovered && shouldHighlight()) {
            hoverAnimationProgress = Mth.lerp(0.1f, hoverAnimationProgress, 1.0f);
        } else {
            hoverAnimationProgress = Mth.lerp(0.1f, hoverAnimationProgress, 0.0f);
        }
        
        if (hoverAnimationProgress > 0.01 && shouldHighlight()) {
            int alpha = (int)(26 * hoverAnimationProgress);
            int top = y;
            int bottom = y + entryHeight + 2;
            guiGraphics.fill(listLeft, top, listRight, bottom, (alpha << 24) | 0xFFFFFF);
        }
            renderContent(guiGraphics, index, y, x, entryWidth, entryHeight, mouseX, mouseY, isHovered, tickDelta);
        }
        
        public abstract void renderContent(GuiGraphics guiGraphics, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta);
        
        public List<? extends GuiEventListener> children() { return Collections.emptyList(); }
        public List<? extends NarratableEntry> narratables() { return Collections.emptyList(); }
        
        protected boolean shouldHighlight() { return true; }
        
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            for (GuiEventListener child : this.children()) {
                if (child instanceof EditBox tf) {
                    if (tf.isMouseOver(mouseX, mouseY)) {
                        tf.setFocused(true);
                        return true;
                    } else {
                        tf.setFocused(false);
                    }
                } else if (child.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
            }
            return false;
        }
        
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            for (GuiEventListener child : this.children()) {
                if (child.mouseReleased(mouseX, mouseY, button)) {
                    return true;
                }
            }
            return false;
        }
        
        public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
            for (GuiEventListener child : this.children()) {
                if (child.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
                    return true;
                }
            }
            return false;
        }
                  
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            for (GuiEventListener child : this.children()) {
                if (child.keyPressed(keyCode, scanCode, modifiers)) {
                    return true;
                }
            }
            return false;
        }
        
        public boolean charTyped(char chr, int modifiers) {
            for (GuiEventListener child : this.children()) {
                if (child.charTyped(chr, modifiers)) {
                    return true;
                }
            }
            return false;
        }
        
        public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
             for (GuiEventListener child : this.children()) {
                if (child.keyReleased(keyCode, scanCode, modifiers)) {
                    return true;
                }
            }
            return false;
        }
    }
    
    private class ConfigOptionListWidget extends AbstractSelectionList<OptionEntry> {
        private double targetScroll = 0;
        private boolean isSmoothScrolling = false;
        private boolean scrolling = false;

        public ConfigOptionListWidget(Minecraft client, int width, int height, int y, int itemHeight) {
            super(client, width, height, y, 26);
        }
        
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (mouseX >= getScrollbarX() && mouseX <= getScrollbarX() + 6) {
                 scrolling = true;
                 return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
        
        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            scrolling = false;
            return super.mouseReleased(mouseX, mouseY, button);
        }
        
        @Override
        protected void renderListBackground(GuiGraphics guiGraphics) {
        }

        public int getHoveredEntryIndex(double mouseX, double mouseY) {
             if (mouseX < this.getX() || mouseX > this.getRight() || mouseY < this.getY() || mouseY > this.getBottom()) {
                 return -1;
             }
             int i = Mth.floor(mouseY - (double)this.getY() - 0 + this.getScrollAmount() - 4.0D);
             int index = i / 26;
             if (index >= 0 && index < this.getItemCount()) {
                 return index;
             }
             return -1;
        }
        
        public void clearEntriesPublic() { this.clearEntries(); }
        @Override public int getRowWidth() { return this.width - 20; } 
        public void addEntryPublic(OptionEntry entry) { this.addEntry(entry); }
        @Override
        public int getRowLeft() { return this.getX() + 10; }
        public int getScrollbarX() { return this.getRight() - 6; }
        @Override public void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {}
        @Override protected void renderListSeparators(GuiGraphics guiGraphics) {}
        @Override protected void renderSelection(GuiGraphics guiGraphics, int top, int width, int height, int outerColor, int innerColor) {}
        
        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
             if (scrolling) {
                 int barHeight = this.getHeight();
                 int contentHeight = this.getMaxScroll() + this.getHeight();
                 if (contentHeight > barHeight) {
                     int scrollbarHeight = (int)((float)(barHeight * barHeight) / (float)contentHeight);
                     scrollbarHeight = Math.max(32, scrollbarHeight);
                     if (scrollbarHeight > barHeight) scrollbarHeight = barHeight;
                     
                     double d = Math.max(1, this.getMaxScroll() / (double)(barHeight - scrollbarHeight));
                     this.setScrollAmount(this.getScrollAmount() + deltaY * d);
                 }
                 return true;
             }
             return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }
        
        public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
             this.enableScissor(guiGraphics);
             this.renderListItems(guiGraphics, mouseX, mouseY, delta);
             guiGraphics.disableScissor();
             
             int scrollbarX = this.getScrollbarX();
             int scrollbarY = this.getY();
             int scrollbarHeight = this.getHeight();
             int contentHeight = this.getMaxScroll() + this.getHeight();
             
             if (contentHeight > this.getHeight()) {
                 int barHeight = (int)((float)(this.getHeight() * this.getHeight()) / (float)contentHeight);
                 barHeight = Math.max(32, barHeight);
                 if (barHeight > this.getHeight()) barHeight = this.getHeight();
                 
                 int barTop = (int)this.getScrollAmount() * (this.getHeight() - barHeight) / (this.getMaxScroll()) + this.getY();
                 if (barTop < this.getY()) barTop = this.getY();
                 
                 guiGraphics.fill(scrollbarX, scrollbarY, scrollbarX + 6, scrollbarY + scrollbarHeight, 0x80000000);
                 guiGraphics.fill(scrollbarX, barTop, scrollbarX + 6, barTop + barHeight, 0xA0FFFFFF);
            }
       }

        public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
            DamageConfigScreen.this.isNavigating = false;
            this.targetScroll = this.getScrollAmount() - verticalAmount * 80.0;
            this.targetScroll = Math.max(0, Math.min(this.targetScroll, this.getMaxScroll()));
            this.isSmoothScrolling = true;
            return true;
        }
        
        public void setTargetScroll(double scroll) {
            this.targetScroll = Math.max(0, Math.min(scroll, this.getMaxScroll()));
            this.isSmoothScrolling = true;
        }
        
        public void updateSmoothScroll(float delta) {
            if (isSmoothScrolling) {
                double current = this.getScrollAmount();
                if (Math.abs(current - targetScroll) < 0.5) {
                    this.setScrollAmount(targetScroll);
                    isSmoothScrolling = false;
                    DamageConfigScreen.this.isNavigating = false;
                } else {
                    this.setScrollAmount(Mth.lerp(0.5f * delta, current, targetScroll));
                }
            } else {
                targetScroll = this.getScrollAmount();
                DamageConfigScreen.this.isNavigating = false;
            }
        }
    }
    
    
    private static class HeaderEntry extends OptionEntry {
        private final Component text;
        private final Minecraft client = Minecraft.getInstance();
        public HeaderEntry(Component text) { this.text = text; }
        @Override
        protected boolean shouldHighlight() { return false; }
        @Override
        public void renderContent(GuiGraphics guiGraphics, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            guiGraphics.drawCenteredString(client.font, text, x + entryWidth / 2, y + 9, 0xFFFFFFFF);
        }
        public List<? extends GuiEventListener> children() { return Collections.emptyList(); }
        public List<? extends NarratableEntry> narratables() { return Collections.emptyList(); }
    }

    private static class BooleanOptionEntry extends OptionEntry {
        private final StyledButton button;
        private final Component label;
        private final Component hint;
        private boolean state;
        private final Minecraft client = Minecraft.getInstance();
        
        public BooleanOptionEntry(String key, boolean initial, Consumer<Boolean> onToggle) {
            this(Component.translatable(key), initial, onToggle,
                "option.damage-engine.resetEnabled".equals(key) ? Component.translatable("hint.damage-engine.resetEnabled")
                    : "option.damage-engine.showInfo".equals(key) ? Component.translatable("hint.damage-engine.showInfo")
                    : "option.damage-engine.showDamageDisplay".equals(key) ? Component.translatable("hint.damage-engine.showDamageDisplay")
                    : null);
        }

        public BooleanOptionEntry(Component label, boolean initial, Consumer<Boolean> onToggle, Component hint) {
            this.state = initial;
            this.label = label;
            this.hint = hint;
            int onColor = 0xFFB5F0C6;
            int offColor = 0xFFFC887E;
            
            final StyledButton[] btnRef = new StyledButton[1];
            btnRef[0] = new StyledButton(0, 0, 100, 20, Component.translatable(state ? "options.on" : "options.off").withColor(state ? onColor : offColor), () -> {
                state = !state;
                btnRef[0].setMessage(Component.translatable(state ? "options.on" : "options.off").withColor(state ? onColor : offColor));
                onToggle.accept(state);
            });
            this.button = btnRef[0];
        }
        @Override
        public void renderContent(GuiGraphics guiGraphics, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            if (hint != null) {
                guiGraphics.drawString(client.font, label, x, y + 4, 0xFFFFFFFF);
                guiGraphics.drawString(client.font, hint, x, y + 14, 0xFFA0A0A0);
            } else {
                guiGraphics.drawString(client.font, label, x, y + 8, 0xFFFFFFFF);
            }
            button.setX(x + entryWidth - 110);
            button.setY(y + 2);
            button.setFocused(false); 
            button.render(guiGraphics, mouseX, mouseY, tickDelta);
        }
        public List<? extends GuiEventListener> children() { return Collections.singletonList(button); }
        public List<? extends NarratableEntry> narratables() { return Collections.singletonList(button); }
    }
    
    private static class StyledSliderWidget extends AbstractSliderButton {
        private final boolean showValue;
        private final boolean soundOnPress;
        private final boolean soundOnRelease;
        private boolean pendingReleaseSound;
        private double valueBeforeInteraction;
        

        
        public StyledSliderWidget(int x, int y, int width, int height, Component text, double value, boolean showValue, boolean soundOnPress, boolean soundOnRelease) {
            super(x, y, width, height, text, value);
            this.showValue = showValue;
            this.soundOnPress = soundOnPress;
            this.soundOnRelease = soundOnRelease;
        }
        
        @Override
        protected void updateMessage() {} 
        
        @Override
        protected void applyValue() {} 

        private void playConfiguredClickSound() {
            if (Minecraft.getInstance().screen instanceof DamageConfigScreen s) {
                s.playClickSound();
            } else {
                super.playDownSound(Minecraft.getInstance().getSoundManager());
            }
        }
        
        @Override
        public void playDownSound(net.minecraft.client.sounds.SoundManager soundManager) {
             if (!soundOnPress) return;
             playConfiguredClickSound();
        }
        
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            boolean handled = super.mouseClicked(mouseX, mouseY, button);
            if (handled && soundOnRelease) {
                pendingReleaseSound = true;
                valueBeforeInteraction = this.value;
            }
            return handled;
        }
        
        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            boolean handled = super.mouseReleased(mouseX, mouseY, button);
            if (soundOnRelease && pendingReleaseSound) {
                pendingReleaseSound = false;
                if (Math.abs(this.value - valueBeforeInteraction) > 1.0E-9) {
                    playConfiguredClickSound();
                }
            }
            return handled;
        }
        
        @Override
        public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
            int x = getX();
            int y = getY();
            int w = getWidth();
            int h = getHeight();
            
            guiGraphics.fill(x, y, x + w, y + h, 0x20000000);
            
            int borderColor = isHovered() ? 0xFFFFFFFF : 0xFFA0A0A0;
            guiGraphics.fill(x, y, x + w, y + 1, borderColor); 
            guiGraphics.fill(x, y + h - 1, x + w, y + h, borderColor); 
            guiGraphics.fill(x, y, x + 1, y + h, borderColor); 
            guiGraphics.fill(x + w - 1, y, x + w, y + h, borderColor); 
            
            int handleWidth = 8;
            int handleX = x + (int)(this.value * (double)(w - handleWidth));
            int handleY = y;
            
            int handleBorderColor = (isHovered() || isFocused()) ? 0xFFFFFFFF : 0xFFCCCCCC;
            
            guiGraphics.fill(handleX, handleY, handleX + handleWidth, handleY + h, 0xFF000000); 
            
            guiGraphics.fill(handleX, handleY, handleX + handleWidth, handleY + 1, handleBorderColor);
            guiGraphics.fill(handleX, handleY + h - 1, handleX + handleWidth, handleY + h, handleBorderColor);
            guiGraphics.fill(handleX, handleY, handleX + 1, handleY + h, handleBorderColor);
            guiGraphics.fill(handleX + handleWidth - 1, handleY, handleX + handleWidth, handleY + h, handleBorderColor);
            
            if (showValue) {
                guiGraphics.drawCenteredString(Minecraft.getInstance().font, getMessage(), x + w / 2, y + (h - 8) / 2, 0xFFFFFFFF);
            }
        }
    }



    private static class IntegerSliderEntry extends OptionEntry {
        private final StyledSliderWidget slider;
        private final Component label;
        private final Minecraft client = Minecraft.getInstance();
        

        
        public IntegerSliderEntry(String key, int current, int min, int max, Consumer<Integer> onChange, boolean soundOnReleaseOnly) {
            this.label = Component.translatable(key);
            float minF = (float)min;
            float maxF = (float)max;
            float currentF = (float)current;
            
            this.slider = new StyledSliderWidget(0, 0, 100, 20, Component.literal(String.valueOf(current)), (currentF - minF) / (maxF - minF), true, !soundOnReleaseOnly, soundOnReleaseOnly) {
                @Override protected void updateMessage() { 
                    int val = (int)Math.round(minF + this.value * (maxF - minF));
                    this.setMessage(Component.literal(String.valueOf(val))); 
                }
                @Override protected void applyValue() { 
                    int val = (int)Math.round(minF + this.value * (maxF - minF));
                    onChange.accept(val); 
                }
            };
        }
        @Override
        public void renderContent(GuiGraphics guiGraphics, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            guiGraphics.drawString(client.font, label, x, y + 8, 0xFFFFFFFF);
            slider.setX(x + entryWidth - 110);
            slider.setY(y + 2);
            slider.render(guiGraphics, mouseX, mouseY, tickDelta);
        }
        public List<? extends GuiEventListener> children() { return Collections.singletonList(slider); }
        public List<? extends NarratableEntry> narratables() { return Collections.singletonList(slider); }
    }
    
    private class ResetButtonEntry extends OptionEntry {
        private final StyledButton button;
        private final Runnable onReset;
        private final Component label;
        private final Minecraft client = Minecraft.getInstance();

        public ResetButtonEntry(String key, Runnable onReset) {
            this.label = Component.translatable(key);
            this.onReset = onReset;
            int color = 0xFFFC887E; 
            Component btnText = Component.translatable("gui.reset");
            
            if (resetButtonState == 1) {
                 btnText = Component.translatable("gui.confirm").append("?");
            } else if (resetButtonState == 2) {
                 color = 0xFFB5F0C6;
                 btnText = Component.translatable("text.damage-engine.reset_done");
            }
            
            this.button = new StyledButton(0, 0, 100, 20, btnText.copy().withColor(color), () -> handleClick());
        }
        
        private void handleClick() {
            if (resetButtonState == 0) {
                resetButtonState = 1;
                button.setMessage(Component.translatable("gui.confirm").append("?").withColor(0xFFFC887E));
                resetButtonActionTime = System.currentTimeMillis();
            } else if (resetButtonState == 1) {
                resetButtonState = 2;
                resetButtonActionTime = System.currentTimeMillis();
                onReset.run();
                button.setMessage(Component.translatable("text.damage-engine.reset_done").withColor(0xFFB5F0C6));
            }
        }
        
        @Override
        public void renderContent(GuiGraphics guiGraphics, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            if (resetButtonState == 2) {
                if (System.currentTimeMillis() - resetButtonActionTime > 3000) {
                    resetButtonState = 0;
                    button.setMessage(Component.translatable("gui.reset").withColor(0xFFFC887E));
                }
            } else if (resetButtonState == 1) {
                if (System.currentTimeMillis() - resetButtonActionTime > 5000) {
                    resetButtonState = 0;
                    button.setMessage(Component.translatable("gui.reset").withColor(0xFFFC887E));
                }
            }
            
            guiGraphics.drawString(client.font, label, x, y + 8, 0xFFFC887E);
            
            button.setX(x + entryWidth - 110);
            button.setY(y + 2);
            button.setFocused(false);
            button.render(guiGraphics, mouseX, mouseY, tickDelta);
        }
        public List<? extends GuiEventListener> children() { return Collections.singletonList(button); }
        public List<? extends NarratableEntry> narratables() { return Collections.singletonList(button); }
    }
    
    private static class ExpandableHeaderEntry extends OptionEntry {
        private final PlainTextButton button;
        private final Component label;
        private final Minecraft client = Minecraft.getInstance();
        private boolean expanded;
        private final Consumer<Boolean> onToggle;
        
        public ExpandableHeaderEntry(String key, boolean expanded, Consumer<Boolean> onToggle) {
            this.label = Component.translatable(key);
            this.expanded = expanded;
            this.onToggle = onToggle;
            
            this.button = new PlainTextButton(0, 0, 20, 20, Component.literal(expanded ? "-" : "+"), () -> onToggle.accept(!expanded), 0xFFFFFFFF, 0xFFFBFB54);
        }
        
        @Override
        public void renderContent(GuiGraphics guiGraphics, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            boolean isHovered = hovered || (mouseX >= x && mouseX <= x + entryWidth && mouseY >= y && mouseY <= y + entryHeight);
            int color = isHovered ? 0xFFFBFB54 : 0xFFFFFFFF;
            
            guiGraphics.drawString(client.font, label, x, y + 8, color);
            
            button.setX(x + entryWidth - 25);
            button.setY(y + 2);
            button.setFocused(false);
            button.setForceHover(isHovered);
            button.render(guiGraphics, mouseX, mouseY, tickDelta);
        }
        
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
             if (this.button.mouseClicked(mouseX, mouseY, button)) return true;
             
             if (this.onToggle != null) {
                 this.onToggle.accept(!expanded);
                 if (Minecraft.getInstance().screen instanceof DamageConfigScreen s) {
                     s.playClickSound();
                 }
                 return true;
             }
             return false;
        }
        
        public List<? extends GuiEventListener> children() { return Collections.singletonList(button); }
        public List<? extends NarratableEntry> narratables() { return Collections.singletonList(button); }
    }

    private static class DamageThresholdEntry extends OptionEntry {
        private final EditBox valField;
        private final EditBox colField;
        private final PlainTextButton delBtn;
        private final Minecraft client = Minecraft.getInstance();
        private int currentColor;
        
        public DamageThresholdEntry(DamageEngineConfig.DamageThreshold dt, Runnable onDelete) {
            this.currentColor = dt.color;
            
            this.valField = new EditBox(client.font, 0, 0, 40, 20, Component.empty());
            this.valField.setBordered(false);
            this.valField.setValue(String.format("%.0f", dt.threshold));
            this.valField.setResponder(s -> {
                try { dt.threshold = Float.parseFloat(s); } catch(Exception ignored){}
            });
            
            this.colField = new EditBox(client.font, 0, 0, 55, 20, Component.empty());
            this.colField.setBordered(false);
            this.colField.setMaxLength(7);
            this.colField.setValue("#" + String.format("%06X", dt.color & 0xFFFFFF));
            this.colField.setResponder(s -> {
                try { 
                    String hex = s.startsWith("#") ? s.substring(1) : s;
                    int v = (int)Long.parseLong(hex, 16) | 0xFF000000;
                    dt.color = v;
                    this.currentColor = v;
                } catch(Exception ignored){}
            });
            
            this.delBtn = new PlainTextButton(0, 0, 20, 20, Component.literal("-"), onDelete, 0xFFFFFFFF, 0xFFFBFB54);
        }
        
        @Override
        public void renderContent(GuiGraphics guiGraphics, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            int rightX = x + entryWidth;
            
            delBtn.setX(rightX - 25); 
            delBtn.setY(y + 2);
            delBtn.setWidth(20);
            delBtn.setForceHover(hovered);
            
            int previewSize = 18;
            int previewX = rightX - 25 - 5 - previewSize;
            int previewY = y + 2 + 1;
            
            int colBoxW = 55;
            int colBoxX = previewX - 5 - colBoxW;
            int colBoxY = y + 2;
            int colBoxH = 20;
            
            colField.setX(colBoxX + 4);
            colField.setY(colBoxY + 6);
            colField.setWidth(colBoxW - 8);
            colField.setHeight(12);
            
            int valBoxW = 40;
            int valBoxX = colBoxX - 5 - valBoxW;
            int valBoxY = y + 2;
            int valBoxH = 20;
            
            valField.setX(valBoxX + 4);
            valField.setY(valBoxY + 6);
            valField.setWidth(valBoxW - 8);
            valField.setHeight(12); 
            
            Component label = Component.translatable("text.damage-engine.damage_reach");
            
            int labelColor = 0xFFFFFFFF; 
            
            guiGraphics.drawString(client.font, label, x + 10, y + 8, labelColor);
            
            guiGraphics.fill(valBoxX, valBoxY, valBoxX + valBoxW, valBoxY + valBoxH, 0x20000000);
            int vx = valBoxX, vy = valBoxY, vw = valBoxW, vh = valBoxH;
            int valBorderColor = (valField.isFocused() || valField.isMouseOver(mouseX, mouseY)) ? 0xFFFFFFFF : 0xFFA0A0A0;
            guiGraphics.fill(vx, vy, vx + vw, vy + 1, valBorderColor);
            guiGraphics.fill(vx, vy + vh - 1, vx + vw, vy + vh, valBorderColor);
            guiGraphics.fill(vx, vy, vx + 1, vy + vh, valBorderColor);
            guiGraphics.fill(vx + vw - 1, vy, vx + vw, vy + vh, valBorderColor);
            
            guiGraphics.fill(colBoxX, colBoxY, colBoxX + colBoxW, colBoxY + colBoxH, 0x20000000);
            int cx = colBoxX, cy = colBoxY, cw = colBoxW, ch = colBoxH;
            int colBorderColor = (colField.isFocused() || colField.isMouseOver(mouseX, mouseY)) ? 0xFFFFFFFF : 0xFFA0A0A0;
            guiGraphics.fill(cx, cy, cx + cw, cy + 1, colBorderColor);
            guiGraphics.fill(cx, cy + ch - 1, cx + cw, cy + ch, colBorderColor);
            guiGraphics.fill(cx, cy, cx + 1, cy + ch, colBorderColor);
            guiGraphics.fill(cx + cw - 1, cy, cx + cw, cy + ch, colBorderColor);
            
            valField.render(guiGraphics, mouseX, mouseY, tickDelta);
            colField.render(guiGraphics, mouseX, mouseY, tickDelta);
            delBtn.render(guiGraphics, mouseX, mouseY, tickDelta);
            
            guiGraphics.fill(previewX - 1, previewY - 1, previewX + previewSize + 1, previewY + previewSize + 1, 0xFFFFFFFF);
            guiGraphics.fill(previewX, previewY, previewX + previewSize, previewY + previewSize, 0xFF000000 | (currentColor & 0xFFFFFF));
        }
        
        public List<? extends GuiEventListener> children() {
            return java.util.Arrays.asList(valField, colField, delBtn);
        }
        
        public List<? extends NarratableEntry> narratables() {
            return java.util.Arrays.asList(valField, colField, delBtn);
        }
    }

    private static class AddButtonEntry extends OptionEntry {
        private final PlainTextButton button;
        private final Runnable action;
        
        public AddButtonEntry(Runnable action) {
            this.action = action;
            this.button = new PlainTextButton(0, 0, 20, 20, Component.literal("+"), action, 0xFFFFFFFF, 0xFFFBFB54);
        }
        
        @Override
        public void renderContent(GuiGraphics guiGraphics, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            // Full row clickable area
            boolean rowHovered = mouseX >= x && mouseX <= x + entryWidth && mouseY >= y && mouseY <= y + entryHeight;
            button.setX(x + entryWidth - 25);
            button.setY(y + 2);
            button.setFocused(false);
            button.setForceHover(rowHovered);

            button.render(guiGraphics, mouseX, mouseY, tickDelta);
        }
        
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (isMouseOver(mouseX, mouseY)) {
                if (Minecraft.getInstance().screen instanceof DamageConfigScreen s) {
                    s.playClickSound();
                }
                action.run();
                return true;
            }
            return false;
        }
        
        public List<? extends GuiEventListener> children() { return Collections.singletonList(button); }
        public List<? extends NarratableEntry> narratables() { return Collections.singletonList(button); }
    }
    
    private static class ButtonActionEntry extends OptionEntry {
        private final StyledButton button;
        private final Component label;
        private final Minecraft client = Minecraft.getInstance();
        public ButtonActionEntry(String labelKey, String buttonKey, Runnable action) {
            this.label = Component.translatable(labelKey); 
            this.button = new StyledButton(0, 0, 100, 20, Component.translatable(buttonKey), action);
        }
        @Override
        public void renderContent(GuiGraphics guiGraphics, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            guiGraphics.drawString(client.font, label, x, y + 8, 0xFFFFFFFF);
            button.setX(x + entryWidth - 110);
            button.setY(y + 2);
            button.setFocused(false);
            button.render(guiGraphics, mouseX, mouseY, tickDelta);
        }
        public List<? extends GuiEventListener> children() { return Collections.singletonList(button); }
        public List<? extends NarratableEntry> narratables() { return Collections.singletonList(button); }
    }
    
    private static class NumericEntry extends OptionEntry {
        private final EditBox field;
        private final Component label;
        private final Minecraft client = Minecraft.getInstance();
        public NumericEntry(String key, float initial, Consumer<Float> onChange) {
            this.label = Component.translatable(key);
            this.field = new EditBox(client.font, 0, 0, 100, 20, Component.empty());
            this.field.setBordered(false);
            this.field.setValue(String.valueOf(initial));
            this.field.setResponder(s -> { try { onChange.accept(Float.parseFloat(s)); } catch(Exception ignored){} });
        }
        @Override
        public void renderContent(GuiGraphics guiGraphics, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            guiGraphics.drawString(client.font, label, x, y + 8, 0xFFFFFFFF);
            
            int boxX = x + entryWidth - 110;
            int boxY = y + 2;
            int boxW = 100;
            int boxH = 20;
            
            field.setX(boxX + 4);
            field.setY(boxY + 6);
            field.setWidth(boxW - 8);
            field.setHeight(12);
            
            guiGraphics.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0x20000000);
            int fx = boxX, fy = boxY, fw = boxW, fh = boxH;
            int borderColor = (field.isFocused() || field.isMouseOver(mouseX, mouseY)) ? 0xFFFFFFFF : 0xFFA0A0A0;
            guiGraphics.fill(fx, fy, fx + fw, fy + 1, borderColor);
            guiGraphics.fill(fx, fy + fh - 1, fx + fw, fy + fh, borderColor);
            guiGraphics.fill(fx, fy, fx + 1, fy + fh, borderColor);
            guiGraphics.fill(fx + fw - 1, fy, fx + fw, fy + fh, borderColor);
            
            field.render(guiGraphics, mouseX, mouseY, tickDelta);
        }
        public List<? extends GuiEventListener> children() { return Collections.singletonList(field); }
        public List<? extends NarratableEntry> narratables() { return Collections.singletonList(field); }
    }
    
    private static class HexColorEntry extends OptionEntry {
        private final EditBox field;
        private final Component label;
        private final Minecraft client = Minecraft.getInstance();
        private int currentColor;

        public HexColorEntry(String key, int initial, Consumer<Integer> onChange) {
            this.label = Component.translatable(key);
            this.currentColor = initial;
            this.field = new EditBox(client.font, 0, 0, 75, 20, Component.empty());
            this.field.setBordered(false);
            this.field.setMaxLength(7);
            this.field.setValue("#" + String.format("%06X", initial & 0xFFFFFF));
            this.field.setResponder(s -> { 
                try { 
                    String hex = s.startsWith("#") ? s.substring(1) : s;
                    int val = (int)Long.parseLong(hex, 16) | 0xFF000000;
                    this.currentColor = val;
                    onChange.accept(val); 
                } catch(Exception ignored){} 
            });
        }
        @Override
        public void renderContent(GuiGraphics guiGraphics, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            guiGraphics.drawString(client.font, label, x, y + 8, 0xFFFFFFFF);
            
            int startX = x + entryWidth - 110;
            int boxX = startX;
            int boxY = y + 2;
            int boxW = 75;
            int boxH = 20;
            
            field.setX(boxX + 4);
            field.setY(boxY + 6);
            field.setWidth(boxW - 8);
            field.setHeight(12);
            
            guiGraphics.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0x20000000);
            int fx = boxX, fy = boxY, fw = boxW, fh = boxH;
            int borderColor = (field.isFocused() || field.isMouseOver(mouseX, mouseY)) ? 0xFFFFFFFF : 0xFFA0A0A0;
            guiGraphics.fill(fx, fy, fx + fw, fy + 1, borderColor);
            guiGraphics.fill(fx, fy + fh - 1, fx + fw, fy + fh, borderColor);
            guiGraphics.fill(fx, fy, fx + 1, fy + fh, borderColor);
            guiGraphics.fill(fx + fw - 1, fy, fx + fw, fy + fh, borderColor);
            
            field.render(guiGraphics, mouseX, mouseY, tickDelta);
            
            int previewSize = 17;
            int previewX = startX + 80;
            
            int previewY = y + 2 + 1; 
            
            guiGraphics.fill(previewX - 1, previewY - 1, previewX + previewSize + 1, previewY + previewSize + 1, 0xFFFFFFFF);
            
            guiGraphics.fill(previewX, previewY, previewX + previewSize, previewY + previewSize, 0xFF000000 | (currentColor & 0xFFFFFF));
        }
        public List<? extends GuiEventListener> children() { return Collections.singletonList(field); }
        public List<? extends NarratableEntry> narratables() { return Collections.singletonList(field); }
    }
    

    
    private static class SpacerEntry extends OptionEntry {
        public SpacerEntry(int height) { }
        @Override
        protected boolean shouldHighlight() { return false; }
        @Override
        public void renderContent(GuiGraphics guiGraphics, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
        }
        public List<? extends GuiEventListener> children() { return Collections.emptyList(); }
        public List<? extends NarratableEntry> narratables() { return Collections.emptyList(); }
    }
    
    private static class KeybindEntry extends OptionEntry {
        private final Component label;
        private final BindingButton button;
        private final KeyMapping keyBinding;
        private List<KeyMapping> conflicts = java.util.Collections.emptyList();
        
        private class BindingButton extends AbstractWidget {
            private final Runnable onPress;

            public BindingButton(int x, int y, int width, int height, Component message, Runnable onPress) {
                super(x, y, width, height, message);
                this.onPress = onPress;
            }
            
            @Override
            public void playDownSound(net.minecraft.client.sounds.SoundManager soundManager) {
                if (Minecraft.getInstance().screen instanceof DamageConfigScreen s) {
                    s.playClickSound();
                } else {
                    super.playDownSound(soundManager);
                }
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
                guiGraphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0x20000000);
                
                int borderColor = isHovered() ? 0xFFFFFFFF : 0xFFA0A0A0;
                int x = getX(); int y = getY(); int w = getWidth(); int h = getHeight();
                guiGraphics.fill(x, y, x + w, y + 1, borderColor); 
                guiGraphics.fill(x, y + h - 1, x + w, y + h, borderColor); 
                guiGraphics.fill(x, y, x + 1, y + h, borderColor); 
                guiGraphics.fill(x + w - 1, y, x + w, y + h, borderColor); 
                
                guiGraphics.drawCenteredString(Minecraft.getInstance().font, getMessage(), getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, 0xFFFFFFFF);
            }
            
            @Override
            protected void updateWidgetNarration(NarrationElementOutput builder) {
                this.defaultButtonNarrationText(builder);
            }
            
        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
             if (isBinding()) {
                if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                    keyBinding.setKey(InputConstants.UNKNOWN);
                } else {
                    keyBinding.setKey(InputConstants.Type.KEYSYM.getOrCreate(keyCode));
                }
                finishBinding();
                return true;
            }
            return false;
        }
        }

        private boolean binding = false;
        
        public KeybindEntry(String keyName, KeyMapping keyBinding) {
            this.label = Component.translatable(keyName);
            this.keyBinding = keyBinding;
            
            this.button = new BindingButton(0, 0, 100, 20, Component.literal("BIND"), () -> {
                setBinding(true);
            });
            updateMessage();
        }
        
        private void setBinding(boolean val) {
            this.binding = val;
            if (Minecraft.getInstance().screen instanceof DamageConfigScreen s) {
                s.setBinding(val);
            }
            updateMessage();
        }
        
        private boolean isBinding() { return binding; }
        
        private void finishBinding() {
            setBinding(false);
            Minecraft.getInstance().options.save();
            KeyMapping.resetMapping();
        }
        
        public void bindMouse(int button) {
            keyBinding.setKey(InputConstants.Type.MOUSE.getOrCreate(button));
            finishBinding();
        }
        
        private void updateMessage() {
            Component text;
            List<KeyMapping> foundConflicts = new ArrayList<>();

            if (binding) {
                MutableComponent boundText = keyBinding.isUnbound()
                    ? Component.translatable("key.damage_engine.not_bound")
                    : keyBinding.getTranslatedKeyMessage().copy();
                text = Component.literal("> ").withColor(0xFFFFFF55).append(boundText.withStyle(ChatFormatting.UNDERLINE).withColor(0xFFFFFFFF)).append(Component.literal(" <").withColor(0xFFFFFF55));
            } else {
                if (keyBinding.isUnbound()) {
                    text = Component.translatable("key.damage_engine.not_bound").withColor(0xFFFFFFFF);
                } else {
                    MutableComponent keyText = keyBinding.getTranslatedKeyMessage().copy();

                    for (KeyMapping kb : Minecraft.getInstance().options.keyMappings) {
                        if (kb != keyBinding && kb.same(keyBinding)) {
                            foundConflicts.add(kb);
                        }
                    }

                    if (!foundConflicts.isEmpty()) {
                        text = Component.literal("[ ").withColor(0xFFFFFF55).append(keyText.withColor(0xFFFFFFFF)).append(Component.literal(" ]").withColor(0xFFFFFF55));
                    } else {
                        text = keyText;
                    }
                }
            }

            conflicts = foundConflicts;
            button.setMessage(text);

            if (!conflicts.isEmpty()) {
                MutableComponent tooltip = Component.translatable("text.damage-engine.key_conflict").append("\n");
                for (int i = 0; i < conflicts.size(); i++) {
                    Component conflictName = Component.translatable(conflicts.get(i).getName());
                    if (i > 0) {
                        tooltip.append(Component.literal(", "));
                    }
                    tooltip.append(conflictName);
                }
                button.setTooltip(Tooltip.create(tooltip));
            } else {
                button.setTooltip(null);
            }
        }
        
        @Override
        public void renderContent(GuiGraphics guiGraphics, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            if (!binding) {
                updateMessage();
            }
            guiGraphics.drawString(Minecraft.getInstance().font, label, x, y + 8, 0xFFFFFFFF);

            button.setX(x + entryWidth - 110);
            button.setY(y + 2);
            button.setWidth(100);
            button.setFocused(false);

            button.render(guiGraphics, mouseX, mouseY, tickDelta);
        }
        
        public List<? extends GuiEventListener> children() { return Collections.singletonList(button); }
        public List<? extends NarratableEntry> narratables() { return Collections.singletonList(button); }
    }
    
    private static class WeightEntry extends OptionEntry {
        private final EditBox input;
        private final Component label;
        private final Component hint;
        private final Minecraft client = Minecraft.getInstance();
        private final float[] valueRef;
        
        public WeightEntry(String key, float current, Consumer<Float> onChange, String hintKey) {
            this.label = Component.translatable(key);
            this.hint = hintKey != null ? Component.translatable(hintKey).withColor(0xFFAAAAAA) : null;
            this.valueRef = new float[]{current};
            this.input = new EditBox(client.font, 0, 0, 50, 20, Component.empty());
            this.input.setBordered(false);
            this.input.setValue(formatValue(current));
            this.input.setResponder(s -> {
                try {
                    float v = Float.parseFloat(s);
                    valueRef[0] = v;
                    onChange.accept(v);
                } catch(Exception ignored){}
            });
        }
        
        private static String formatValue(float v) {
            if (v == (int)v) return String.valueOf((int)v);
            return String.format("%.1f", v);
        }
        
        @Override
        public void renderContent(GuiGraphics guiGraphics, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            if (hint != null) {
                guiGraphics.drawString(client.font, label, x, y + 4, 0xFFFFFFFF);
                guiGraphics.drawString(client.font, hint, x, y + 14, 0xFFA0A0A0);
            } else {
                guiGraphics.drawString(client.font, label, x, y + 8, 0xFFFFFFFF);
            }
            int boxW = 100;
            int boxX = x + entryWidth - 110;
            int boxY = y + 2;
            int boxH = 20;
            guiGraphics.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0x20000000);
            int bc = (input.isFocused() || input.isMouseOver(mouseX, mouseY)) ? 0xFFFFFFFF : 0xFFA0A0A0;
            guiGraphics.fill(boxX, boxY, boxX + boxW, boxY + 1, bc);
            guiGraphics.fill(boxX, boxY + boxH - 1, boxX + boxW, boxY + boxH, bc);
            guiGraphics.fill(boxX, boxY, boxX + 1, boxY + boxH, bc);
            guiGraphics.fill(boxX + boxW - 1, boxY, boxX + boxW, boxY + boxH, bc);
            input.setX(boxX + 4);
            input.setY(boxY + 6);
            input.setWidth(boxW - 8);
            input.setHeight(12);
            input.render(guiGraphics, mouseX, mouseY, tickDelta);
        }
        public List<? extends GuiEventListener> children() { return Collections.singletonList(input); }
        public List<? extends NarratableEntry> narratables() { return Collections.singletonList(input); }
    }
    
    private static class RatingGradeEntry extends OptionEntry {
        private final EditBox scoreField;
        private final EditBox textField;
        private final PlainTextButton delBtn;
        private final Minecraft client = Minecraft.getInstance();
        
        public RatingGradeEntry(DamageEngineConfig.RatingGrade g, Runnable onDelete) {
            this.scoreField = new EditBox(client.font, 0, 0, 60, 20, Component.empty());
            this.scoreField.setBordered(false);
            this.scoreField.setValue(String.format("%.0f", g.minScore));
            this.scoreField.setResponder(s -> {
                try { g.minScore = Float.parseFloat(s); } catch(Exception ignored){}
            });
            
            this.textField = new EditBox(client.font, 0, 0, 60, 20, Component.empty());
            this.textField.setBordered(false);
            this.textField.setMaxLength(4);
            this.textField.setValue(g.text);
            this.textField.setResponder(s -> {
                g.text = s;
            });
            
            this.delBtn = new PlainTextButton(0, 0, 20, 20, Component.literal("-"), onDelete, 0xFFFFFFFF, 0xFFFBFB54);
        }
        
        @Override
        public void renderContent(GuiGraphics guiGraphics, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            int rightX = x + entryWidth;
            int boxW = 60;
            int boxH = 20;
            int boxY = y + (entryHeight - boxH) / 2;
            
            delBtn.setX(rightX - 25);
            delBtn.setY(boxY);
            delBtn.setWidth(20);
            delBtn.setForceHover(hovered);
            
            int textBoxX = rightX - 25 - 3 - boxW;

            textField.setX(textBoxX + 4);
            textField.setY(boxY + 6);
            textField.setWidth(boxW - 8);
            textField.setHeight(12);

            int scoreBoxX = textBoxX - 3 - boxW;

            scoreField.setX(scoreBoxX + 4);
            scoreField.setY(boxY + 6);
            scoreField.setWidth(boxW - 8);
            scoreField.setHeight(12);
            
            Component label = Component.literal("≥");
            guiGraphics.drawString(client.font, label, x + 10, y + 8, 0xFFFFFFFF);
            
            drawBox(guiGraphics, scoreBoxX, boxY, boxW, boxH, scoreField, mouseX, mouseY);
            drawBox(guiGraphics, textBoxX, boxY, boxW, boxH, textField, mouseX, mouseY);
            
            scoreField.render(guiGraphics, mouseX, mouseY, tickDelta);
            textField.render(guiGraphics, mouseX, mouseY, tickDelta);
            delBtn.render(guiGraphics, mouseX, mouseY, tickDelta);
        }
        
        private void drawBox(GuiGraphics guiGraphics, int bx, int by, int bw, int bh, EditBox field, int mouseX, int mouseY) {
            guiGraphics.fill(bx, by, bx + bw, by + bh, 0x20000000);
            int bc = (field.isFocused() || field.isMouseOver(mouseX, mouseY)) ? 0xFFFFFFFF : 0xFFA0A0A0;
            guiGraphics.fill(bx, by, bx + bw, by + 1, bc);
            guiGraphics.fill(bx, by + bh - 1, bx + bw, by + bh, bc);
            guiGraphics.fill(bx, by, bx + 1, by + bh, bc);
            guiGraphics.fill(bx + bw - 1, by, bx + bw, by + bh, bc);
        }
        
        public List<? extends GuiEventListener> children() {
            return java.util.Arrays.asList(scoreField, textField, delBtn);
        }
        public List<? extends NarratableEntry> narratables() {
            return java.util.Arrays.asList(scoreField, textField, delBtn);
        }
    }
    
    private static class RatingGradeAppearanceEntry extends OptionEntry {
        private final DamageEngineConfig.RatingGrade grade;
        private final DamageEngineConfig config;
        private final EditBox colField;
        private final StyledButton selectImageBtn;
        private final PlainTextButton resetImageBtn;
        private final Minecraft client = Minecraft.getInstance();
        private int currentColor;
        
        public RatingGradeAppearanceEntry(DamageEngineConfig.RatingGrade g, DamageEngineConfig config) {
            this.grade = g;
            this.config = config;
            this.currentColor = g.color;
            
            this.colField = new EditBox(client.font, 0, 0, 75, 20, Component.empty());
            this.colField.setBordered(false);
            this.colField.setMaxLength(7);
            this.colField.setValue("#" + String.format("%06X", g.color & 0xFFFFFF));
            this.colField.setResponder(s -> {
                try {
                    String hex = s.startsWith("#") ? s.substring(1) : s;
                    int v = (int)Long.parseLong(hex, 16) | 0xFF000000;
                    g.color = v;
                    this.currentColor = v;
                } catch(Exception ignored){}
            });
            
            this.selectImageBtn = new StyledButton(0, 0, 100, 20, Component.translatable("option.damage-engine.select_image"), this::onSelectImage);
            this.resetImageBtn = new PlainTextButton(0, 0, 20, 20, Component.literal("×"), this::onResetImage, 0xFFFC887E, 0xFFFBFB54);
            
            java.io.File configDir = new java.io.File(client.gameDirectory, "config/damage-engine/images");
            if (!configDir.exists()) {
                configDir.mkdirs();
            }
        }
        
        private void onSelectImage() {
            try {
                java.io.File imagesDir = new java.io.File(client.gameDirectory, "config/damage-engine/images");
                if (!imagesDir.exists()) {
                    imagesDir.mkdirs();
                }
                
                String fileName = "rating_" + grade.index + ".png";
                java.io.File destFile = new java.io.File(imagesDir, fileName);
                
                if (System.getProperty("os.name").toLowerCase().startsWith("windows")) {
                    ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-Command", 
                        "[System.Reflection.Assembly]::LoadWithPartialName('System.Windows.Forms') | Out-Null;" +
                        "$dlg = New-Object System.Windows.Forms.OpenFileDialog;" +
                        "$dlg.Filter = 'PNG Images (*.png)|*.png|All Files (*.*)|*.*';" +
                        "$dlg.Title = '选择评价图片';" +
                        "$dlg.ShowDialog() | Out-Null;" +
                        "$dlg.FileName");
                    pb.redirectErrorStream(true);
                    Process process = pb.start();
                    
                    java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(process.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
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
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        private void onResetImage() {
            grade.imagePath = "";
        }
        
        @Override
        public void renderContent(GuiGraphics guiGraphics, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            int rightX = x + entryWidth;
            int boxY = y + 2;
            
            if (config.ratingUseImages) {
                boolean hasImage = grade.imagePath != null && !grade.imagePath.isEmpty();
                int resetBtnW = hasImage ? 22 : 0;
                
                selectImageBtn.setX(rightX - 110);
                selectImageBtn.setY(boxY);
                selectImageBtn.setWidth(100);
                selectImageBtn.visible = true;
                
                resetImageBtn.visible = hasImage;
                if (hasImage) {
                    resetImageBtn.setX(rightX - 110 - 25);
                    resetImageBtn.setY(boxY);
                    resetImageBtn.setWidth(20);
                }
                
                // 显示路径文本（只读，支持滚动）
                String pathText = grade.imagePath != null && !grade.imagePath.isEmpty() ? grade.imagePath : "未设置";
                
                // 只显示最后一段路径（文件名）
                if (pathText.contains("/")) {
                    pathText = pathText.substring(pathText.lastIndexOf("/") + 1);
                }
                
                int maxWidth = 80;
                int textWidth = client.font.width(pathText);
                
                // 如果还是太长，截取并加省略号
                if (textWidth > maxWidth) {
                    while (textWidth > maxWidth - 20 && pathText.length() > 4) {
                        pathText = pathText.substring(0, pathText.length() - 1);
                        textWidth = client.font.width(pathText + "...");
                    }
                    pathText = pathText + "...";
                }
                
                int pathX = rightX - 110 - 5 - textWidth - resetBtnW - 5;
                if (pathX < x + 10) pathX = x + 10;
                
                Component label = Component.literal(grade.text);
                guiGraphics.drawString(client.font, label, x + 10, y + 8, 0xFFFFFFFF);
                
                guiGraphics.drawString(client.font, Component.literal(pathText), pathX, y + 8, 0xFFFFFFFF);
                resetImageBtn.render(guiGraphics, mouseX, mouseY, tickDelta);
                selectImageBtn.render(guiGraphics, mouseX, mouseY, tickDelta);
            } else {
                selectImageBtn.visible = false;
                
                int startX = x + entryWidth - 110;
                int previewSize = 17;
                int previewX = startX + 80;
                int previewY = boxY + 1;
                
                int colBoxW = 75;
                int colBoxX = startX;
                
                colField.setX(colBoxX + 4);
                colField.setY(boxY + 6);
                colField.setWidth(colBoxW - 8);
                colField.setHeight(12);
                colField.visible = true;

                Component label = Component.literal(grade.text);
                guiGraphics.drawString(client.font, label, x + 10, y + 8, 0xFFFFFFFF);

                drawBox(guiGraphics, colBoxX, boxY, colBoxW, 20, colField, mouseX, mouseY);
                colField.render(guiGraphics, mouseX, mouseY, tickDelta);
                
                guiGraphics.fill(previewX - 1, previewY - 1, previewX + previewSize + 1, previewY + previewSize + 1, 0xFFFFFFFF);
                guiGraphics.fill(previewX, previewY, previewX + previewSize, previewY + previewSize, 0xFF000000 | (currentColor & 0xFFFFFF));
            }
        }
        
        private void drawBox(GuiGraphics guiGraphics, int bx, int by, int bw, int bh, EditBox field, int mouseX, int mouseY) {
            guiGraphics.fill(bx, by, bx + bw, by + bh, 0x20000000);
            int bc = (field.isFocused() || field.isMouseOver(mouseX, mouseY)) ? 0xFFFFFFFF : 0xFFA0A0A0;
            guiGraphics.fill(bx, by, bx + bw, by + 1, bc);
            guiGraphics.fill(bx, by + bh - 1, bx + bw, by + bh, bc);
            guiGraphics.fill(bx, by, bx + 1, by + bh, bc);
            guiGraphics.fill(bx + bw - 1, by, bx + bw, by + bh, bc);
        }
        
        public List<? extends GuiEventListener> children() {
            if (config.ratingUseImages) {
                java.util.List<GuiEventListener> list = new java.util.ArrayList<>();
                list.add(selectImageBtn);
                if (resetImageBtn.visible) list.add(resetImageBtn);
                return list;
            } else {
                return java.util.Arrays.asList(colField);
            }
        }
        public List<? extends NarratableEntry> narratables() {
            if (config.ratingUseImages) {
                java.util.List<NarratableEntry> list = new java.util.ArrayList<>();
                list.add(selectImageBtn);
                if (resetImageBtn.visible) list.add(resetImageBtn);
                return list;
            } else {
                return java.util.Arrays.asList(colField);
            }
        }
    }
}
