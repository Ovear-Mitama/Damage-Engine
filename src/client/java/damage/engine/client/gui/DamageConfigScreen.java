package damage.engine.client.gui;

import damage.engine.DamageEngineClient;
import damage.engine.DamageEngineConfig;
import damage.engine.hud.DamageHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.cursor.CursorType;
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

import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.CharacterEvent;

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

    // Cursor types for custom widget cursor support (1.21.11+)
    private static final CursorType CURSOR_HAND = createCursor(0x00036004, "hand");
    private static final CursorType CURSOR_TEXT = createCursor(0x00036002, "text");
    private static final CursorType CURSOR_V_RESIZE = createCursor(0x00036006, "vresize");
    private static final CursorType CURSOR_H_RESIZE = createCursor(0x00036005, "hresize");

    private static CursorType createCursor(int handle, String name) {
        try {
            return CursorType.createStandardCursor(handle, name, CursorType.DEFAULT);
        } catch (Exception e) {
            return CursorType.DEFAULT;
        }
    }

    public DamageConfigScreen(Screen parent) {
        super(Component.translatable("title.damage-engine.config"));
        this.parent = parent;
        this.config = DamageEngineConfig.getInstance();
    }

    private final List<StyledButton> footerButtons = new ArrayList<>();
    private StyledButton[] previewBtnRef = new StyledButton[1];

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
        public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
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
        protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
            int color = (isHovered() || forceHover) ? hoverColor : defaultColor;
            if (isHovered() || forceHover) {
                context.requestCursor(DamageConfigScreen.CURSOR_HAND);
            }
            context.drawCenteredString(Minecraft.getInstance().font, getMessage(), getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, color);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
            this.defaultButtonNarrationText(narrationElementOutput);
        }

        @Override
        public void playDownSound(net.minecraft.client.sounds.SoundManager handler) {
            if (Minecraft.getInstance().screen instanceof DamageConfigScreen s) {
                s.playClickSound();
            } else {
                super.playDownSound(handler);
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
        public void playDownSound(net.minecraft.client.sounds.SoundManager handler) {
            if (Minecraft.getInstance().screen instanceof DamageConfigScreen s) {
                s.playClickSound();
            } else if (Minecraft.getInstance().screen instanceof HudEditorScreen s) {
                s.playClickSound();
            } else {
                super.playDownSound(handler);
            }
        }
        
        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
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
        protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
            context.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0x20000000);
            
            int borderColor = isHovered() ? 0xFFFFFFFF : 0xFFA0A0A0;
            int x = getX(); int y = getY(); int w = getWidth(); int h = getHeight();
            context.fill(x, y, x + w, y + 1, borderColor); 
            context.fill(x, y + h - 1, x + w, y + h, borderColor); 
            context.fill(x, y, x + 1, y + h, borderColor); 
            context.fill(x + w - 1, y, x + w, y + h, borderColor); 
            
            if (isHovered()) {
                context.requestCursor(DamageConfigScreen.CURSOR_HAND);
            }
            
            context.drawCenteredString(Minecraft.getInstance().font, getMessage(), getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, 0xFFFFFFFF);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
            this.defaultButtonNarrationText(narrationElementOutput);
        }
    }

    @Override
    protected void init() {
        try {
            if (optionList != null) lastOptionScroll = optionList.scrollAmount();
            if (categoryList != null) lastCategoryScroll = categoryList.scrollAmount();

            this.clearWidgets();
            categories.clear();
            footerButtons.clear();
            
            int leftWidth = 120;
            int footerY = this.height - 35;
            int topY = -2; 
            
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

            if (!categories.isEmpty()) {
                int y = categoryList.getY() + 4;
                y += 25;
                for (int i = 0; i < categories.size(); i++) {
                    CategoryEntry cat = categories.get(i);
                    cat.lastY = y;
                    y += 25;
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
            
            int onColor = 0xFFB5F0C6;
            int offColor = 0xFFFC887E;
            previewBtnRef[0] = new StyledButton(10, buttonY, 80, 20, Component.translatable("text.damage-engine.preview").append(": ").append(Component.translatable(config.previewEnabled ? "options.on" : "options.off").withColor(config.previewEnabled ? onColor : offColor)), () -> {
                config.previewEnabled = !config.previewEnabled;
                previewBtnRef[0].setMessage(Component.translatable("text.damage-engine.preview").append(": ").append(Component.translatable(config.previewEnabled ? "options.on" : "options.off").withColor(config.previewEnabled ? onColor : offColor)));
            });
            footerButtons.add(previewBtnRef[0]);
            this.addRenderableWidget(previewBtnRef[0]);
        } catch (Exception e) {
            DamageEngineClient.LOGGER.error("Failed to initialize config screen: ", e);
        }
    }
    
    private void refreshOptions() {
        if (optionList == null) return;
        double scroll = optionList.scrollAmount();
        optionList.clearEntriesPublic();
        initOptionEntries();
        optionList.setScrollAmount(scroll);
        
        if (previewBtnRef[0] != null) {
            int onColor = 0xFFB5F0C6;
            int offColor = 0xFFFC887E;
            previewBtnRef[0].setMessage(Component.translatable("text.damage-engine.preview").append(": ").append(Component.translatable(config.previewEnabled ? "options.on" : "options.off").withColor(config.previewEnabled ? onColor : offColor)));
        }
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
        
        if (DamageEngineClient.configKeyBinding != null) {
            addOption(new KeybindEntry("key.damage_engine.config", DamageEngineClient.configKeyBinding));
        }
        if (DamageEngineClient.toggleHudKeyBinding != null) {
            addOption(new KeybindEntry("key.damage_engine.toggle_hud", DamageEngineClient.toggleHudKeyBinding));
        }
        if (DamageEngineClient.clearDamageKeyBinding != null) {
            addOption(new KeybindEntry("key.damage_engine.clear_damage", DamageEngineClient.clearDamageKeyBinding));
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
            this.minecraft.options.save();
            refreshOptions();
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
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null) {
                SimpleSoundInstance sound = SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F);
                minecraft.getSoundManager().play(sound);
            }
        } catch (Exception e) {
            DamageEngineClient.LOGGER.error("Failed to play sound", e);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (isBinding) {
            if (optionList != null) {
                for (OptionEntry entry : optionList.children()) {
                    if (entry instanceof KeybindEntry ke) {
                        if (ke.isBinding()) {
                            if (ke.button.keyPressed(event)) {
                                return true;
                            }
                        }
                    }
                }
            }
            if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
                setBinding(false);
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        if (optionList != null) {
            for (OptionEntry entry : optionList.children()) {
                for (GuiEventListener child : entry.children()) {
                    if (child instanceof EditBox tf) {
                        tf.setFocused(false);
                    }
                }
            }
        }
        
        if (isBinding) {
             if (optionList != null) {
                 for (OptionEntry entry : optionList.children()) {
                     if (entry instanceof KeybindEntry ke && ke.isBinding()) {
                         ke.bindMouse(event.button());
                         return true;
                     }
                 }
             }
             setBinding(false);
             return true;
         }
        return super.mouseClicked(event, bl);
    }

    @Override
    public void onClose() {
        super.onClose();
    }
    
    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        
        if (categoryList != null) categoryList.render(context, mouseX, mouseY, delta);
        if (optionList != null) {
            optionList.render(context, mouseX, mouseY, delta);
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
                     if (bestCatId >= 0 && bestCatId < categories.size()) {
                         CategoryEntry cat = categories.get(bestCatId);
                         targetCategoryY = cat.getY();
                     }
                 }
            }
        }

        selectedCategoryY = Mth.lerp(0.15f, selectedCategoryY, targetCategoryY);
        
        int leftWidth = 120;
        int footerY = this.height - 35;
        
        context.fill(leftWidth - 1, 0, leftWidth, footerY, 0xFF555555);
        context.fill(0, footerY - 1, this.width, footerY, 0xFF555555);
        
        context.fill(0, 25, leftWidth, 26, 0xFF555555);
        
        if (selectedCategoryY > 0) {
            context.fill(0, (int)selectedCategoryY + 2, 2, (int)selectedCategoryY + 25 - 2, 0xFFFFFFFF);
        }
        
        for (StyledButton btn : footerButtons) {
            btn.render(context, mouseX, mouseY, delta);
        }
        
        context.drawString(this.font, Component.translatable("category.damage_engine"), 10, 10, 0xFFFFFFFF, true);
        
        if (DamageEngineClient.serverModChecked && !DamageEngineClient.serverHasMod) {
            String[] lines = Component.translatable("text.damage-engine.client_mode_hint").getString().split("\n");
            int hintY = 155;
            for (int i = 0; i < lines.length; i++) {
                context.drawString(this.font, Component.literal(lines[i]).withColor(0xFFFC887E), 5, hintY + i * 12, 0xFFFC887E, true);
            }
        }
        
        if (config.previewEnabled) {
            new DamageHud().renderPreview(context, this.width/2, this.height/2);
        }
    }

    private static class CategorySpacerEntry extends CategoryEntry {
        public CategorySpacerEntry(DamageConfigScreen screen) {
            super(screen, Component.empty(), -1, -1);
        }
        @Override public void renderContent(GuiGraphics guiGraphics, int mouseX, int mouseY, boolean hovered, float f) {}
        @Override public boolean mouseClicked(MouseButtonEvent event, boolean bl) { return false; }
    }

    private static class CategoryEntry extends ContainerObjectSelectionList.Entry<CategoryEntry> {
        private final Component text;
        private final int id;
        private int targetIndex;
        private final Minecraft minecraft = Minecraft.getInstance();
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
        public void renderContent(GuiGraphics guiGraphics, int mouseX, int mouseY, boolean hovered, float f) {
            lastY = this.getY();
            int x = this.getX();
            int w = screen.categoryList != null ? screen.categoryList.getRowWidth() : 120;
            boolean isHovered = mouseX >= x && mouseX <= x + w && mouseY >= lastY && mouseY < lastY + 25;
            if (isHovered) {
                guiGraphics.requestCursor(DamageConfigScreen.CURSOR_HAND);
            }
            guiGraphics.drawString(minecraft.font, text, x + 10, lastY + 8, 0xFFFFFFFF, true);
        }
        
        @Override public List<? extends GuiEventListener> children() { return Collections.emptyList(); }
        @Override
        public List<? extends net.minecraft.client.gui.narration.NarratableEntry> narratables() {
            return Collections.emptyList();
        }
        
        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
            screen.selectCategory(id);
            screen.playClickSound();
            return true;
        }
    }
    
    private class CategoryListWidget extends ContainerObjectSelectionList<CategoryEntry> {
        public CategoryListWidget(Minecraft minecraft, int width, int height, int y, int itemHeight) {
            super(minecraft, width, height, y, 25);
        }
        
        protected void drawMenuListBackground(GuiGraphics context) {
        }
        public void clearEntriesPublic() { this.clearEntries(); }
        
        public void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
             context.enableScissor(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height);
             this.renderListItems(context, mouseX, mouseY, delta);
             context.disableScissor();
        }

        @Override public int getRowWidth() { return 100; }
        public void addEntryPublic(CategoryEntry entry) { this.addEntry(entry); }
        @Override public int getRowLeft() { return 0; }
        protected void drawHeaderAndFooterSeparators(GuiGraphics context) {}
    }
    
    private abstract static class OptionEntry extends ContainerObjectSelectionList.Entry<OptionEntry> {
        private double hoverAnimationProgress = 0;
        
        public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
            DamageConfigScreen screen = (DamageConfigScreen) Minecraft.getInstance().screen;
            if (screen == null || screen.optionList == null) return;
            
            int y = this.getY();
            int x = this.getX();
            int entryWidth = screen.optionList.getRowWidth();
            int entryHeight = 24;
            
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
                int top = y - 1;
                int bottom = y + entryHeight + 1;
                guiGraphics.fill(listLeft, top, listRight, bottom, (alpha << 24) | 0xFFFFFF);
            }
            renderContent(guiGraphics, 0, y, x, entryWidth, entryHeight, mouseX, mouseY, isHovered, partialTick);
        }
        
        public void renderOld(GuiGraphics context, int index, float f) {
        }
        
        public abstract void renderContent(GuiGraphics context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta);
        
        @Override
        public void renderContent(GuiGraphics guiGraphics, int i, int j, boolean bl, float f) {
            // Required by Entry abstract method in 1.21.11 - delegate to our full render logic
            this.render(guiGraphics, i, j, bl, f);
        }
        
        protected boolean shouldHighlight() { return true; }
        
        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
            for (GuiEventListener child : this.children()) {
                if (child instanceof EditBox tf) {
                    if (tf.isMouseOver(event.x(), event.y())) {
                        tf.setFocused(true);
                        return true;
                    } else {
                        tf.setFocused(false);
                    }
                } else if (child.mouseClicked(event, bl)) {
                    return true;
                }
            }
            return false;
        }
        
        @Override
        public boolean mouseReleased(MouseButtonEvent event) {
            for (GuiEventListener child : this.children()) {
                if (child.mouseReleased(event)) {
                    return true;
                }
            }
            return false;
        }
        
        @Override
        public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
            for (GuiEventListener child : this.children()) {
                if (child.mouseDragged(event, deltaX, deltaY)) {
                    return true;
                }
            }
            return false;
        }
                  
        @Override
        public boolean keyPressed(KeyEvent event) {
            for (GuiEventListener child : this.children()) {
                if (child.keyPressed(event)) {
                    return true;
                }
            }
            return false;
        }
        
        @Override
        public boolean charTyped(CharacterEvent event) {
            for (GuiEventListener child : this.children()) {
                if (child.charTyped(event)) {
                    return true;
                }
            }
            return false;
        }
        
        @Override
        public boolean keyReleased(KeyEvent event) {
             for (GuiEventListener child : this.children()) {
                if (child.keyReleased(event)) {
                    return true;
                }
            }
            return false;
        }
    }
    
    private class ConfigOptionListWidget extends ContainerObjectSelectionList<OptionEntry> {
        private double targetScroll = 0;
        private boolean isSmoothScrolling = false;
        private boolean scrolling = false;

        public ConfigOptionListWidget(Minecraft minecraft, int width, int height, int y, int itemHeight) {
            super(minecraft, width, height, y, 26);
        }
        
        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
            if (event.x() >= getScrollbarX() && event.x() <= getScrollbarX() + 6) {
                 scrolling = true;
                 return true;
            }
            return super.mouseClicked(event, bl);
        }
        
        @Override
        public boolean mouseReleased(MouseButtonEvent event) {
            scrolling = false;
            return super.mouseReleased(event);
        }
        
        protected void drawMenuListBackground(GuiGraphics context) {
        }

        public int getHoveredEntryIndex(double mouseX, double mouseY) {
             if (mouseX < this.getX() || mouseX > this.getRight() || mouseY < this.getY() || mouseY > this.getBottom()) {
                 return -1;
             }
             int i = Mth.floor(mouseY - (double)this.getY() - 0 + this.scrollAmount() - 4.0D);
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
        
        protected void drawHeaderAndFooterSeparators(GuiGraphics context) {}
        
        @Override
        public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
             if (scrolling) {
                 int barHeight = this.getHeight();
                 int contentHeight = this.maxScrollAmount() + this.getHeight();
                 if (contentHeight > barHeight) {
                     int scrollbarHeight = (int)((float)(barHeight * barHeight) / (float)contentHeight);
                     scrollbarHeight = Math.max(32, scrollbarHeight);
                     if (scrollbarHeight > barHeight) scrollbarHeight = barHeight;
                     
                     double d = Math.max(1, this.maxScrollAmount() / (double)(barHeight - scrollbarHeight));
                     this.setScrollAmount(this.scrollAmount() + deltaY * d);
                 }
                 return true;
             }
             return super.mouseDragged(event, deltaX, deltaY);
        }
        
        public void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
             context.enableScissor(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height);
             this.renderListItems(context, mouseX, mouseY, delta);
             context.disableScissor();
             
             int scrollbarX = this.getScrollbarX();
             int scrollbarY = this.getY();
             int scrollbarHeight = this.getHeight();
             int contentHeight = this.maxScrollAmount() + this.getHeight();
             
             if (contentHeight > this.getHeight()) {
                 int barHeight = (int)((float)(this.getHeight() * this.getHeight()) / (float)contentHeight);
                 barHeight = Math.max(32, barHeight);
                 if (barHeight > this.getHeight()) barHeight = this.getHeight();
                 
                 int barTop = (int)this.scrollAmount() * (this.getHeight() - barHeight) / (this.maxScrollAmount()) + this.getY();
                 if (barTop < this.getY()) barTop = this.getY();
                 
                 context.fill(scrollbarX, scrollbarY, scrollbarX + 6, scrollbarY + scrollbarHeight, 0x80000000);
                 context.fill(scrollbarX, barTop, scrollbarX + 6, barTop + barHeight, 0xA0FFFFFF);
            }
            
            // Cursor for scrollbar
            if (mouseX >= getScrollbarX() && mouseX <= getScrollbarX() + 6 && contentHeight > this.getHeight()) {
                context.requestCursor(scrolling ? CURSOR_V_RESIZE : CURSOR_HAND);
            }
       }

        public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
            DamageConfigScreen.this.isNavigating = false;
            this.targetScroll = this.scrollAmount() - verticalAmount * 80.0;
            this.targetScroll = Math.max(0, Math.min(this.targetScroll, this.maxScrollAmount()));
            this.isSmoothScrolling = true;
            return true;
        }
        
        public void setTargetScroll(double scroll) {
            this.targetScroll = Math.max(0, Math.min(scroll, this.maxScrollAmount()));
            this.isSmoothScrolling = true;
        }
        
        public void updateSmoothScroll(float delta) {
            if (isSmoothScrolling) {
                double current = this.scrollAmount();
                if (Math.abs(current - targetScroll) < 0.5) {
                    this.setScrollAmount(targetScroll);
                    isSmoothScrolling = false;
                    DamageConfigScreen.this.isNavigating = false;
                } else {
                    this.setScrollAmount(Mth.lerp(0.5f * delta, current, targetScroll));
                }
            } else {
                targetScroll = this.scrollAmount();
                DamageConfigScreen.this.isNavigating = false;
            }
        }
    }
    
    private static class HeaderEntry extends OptionEntry {
        private final Component text;
        private final Minecraft minecraft = Minecraft.getInstance();
        public HeaderEntry(Component text) { this.text = text; }
        @Override
        protected boolean shouldHighlight() { return false; }
        @Override
        public void renderContent(GuiGraphics context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            context.drawCenteredString(minecraft.font, text, x + entryWidth / 2, y + 9, 0xFFFFFFFF);
        }
        @Override public List<? extends GuiEventListener> children() { return Collections.emptyList(); }
        @Override
        public List<? extends net.minecraft.client.gui.narration.NarratableEntry> narratables() {
            return Collections.emptyList();
        }
    }

    private static class BooleanOptionEntry extends OptionEntry {
        private final StyledButton button;
        private final Component label;
        private final Component hint;
        private boolean state;
        private final Minecraft minecraft = Minecraft.getInstance();
        
        public BooleanOptionEntry(String key, boolean initial, Consumer<Boolean> onToggle) {
            this(Component.translatable(key), initial, onToggle,
                "option.damage-engine.resetEnabled".equals(key) ? Component.translatable("hint.damage-engine.resetEnabled")
                    : "option.damage-engine.showInfo".equals(key) ? Component.translatable("hint.damage-engine.showInfo")
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
        public void renderContent(GuiGraphics context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            if (hint != null) {
                context.drawString(minecraft.font, label, x, y + 4, 0xFFFFFFFF, true);
                context.drawString(minecraft.font, hint, x, y + 14, 0xFFA0A0A0, true);
            } else {
                context.drawString(minecraft.font, label, x, y + 8, 0xFFFFFFFF, true);
            }
            button.setX(x + entryWidth - 110);
            button.setY(y + 2);
            button.setFocused(false); 
            button.render(context, mouseX, mouseY, tickDelta);
        }
        @Override public List<? extends GuiEventListener> children() { return Collections.singletonList(button); }
        @Override
        public List<? extends net.minecraft.client.gui.narration.NarratableEntry> narratables() {
            return Collections.emptyList();
        }
    }
    
    private static class StyledSliderWidget extends AbstractSliderButton {
        private final boolean showValue;
        private final boolean soundOnPress;
        private final boolean soundOnRelease;
        private boolean pendingReleaseSound;
        private double valueBeforeInteraction;
        private boolean isDragging = false;

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

        @Override
        public void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
            this.defaultButtonNarrationText(narrationElementOutput);
        }

        private void playConfiguredClickSound() {
            if (Minecraft.getInstance().screen instanceof DamageConfigScreen s) {
                s.playClickSound();
            } else {
                super.playDownSound(Minecraft.getInstance().getSoundManager());
            }
        }
        
        @Override
        public void playDownSound(net.minecraft.client.sounds.SoundManager handler) {
             if (!soundOnPress) return;
             playConfiguredClickSound();
        }
        
        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
            boolean handled = super.mouseClicked(event, bl);
            if (handled) {
                isDragging = true;
                if (soundOnRelease) {
                    pendingReleaseSound = true;
                    valueBeforeInteraction = this.value;
                }
            }
            return handled;
        }
        
        @Override
        public boolean mouseReleased(MouseButtonEvent event) {
            isDragging = false;
            boolean handled = super.mouseReleased(event);
            if (soundOnRelease && pendingReleaseSound) {
                pendingReleaseSound = false;
                if (Math.abs(this.value - valueBeforeInteraction) > 1.0E-9) {
                    playConfiguredClickSound();
                }
            }
            return handled;
        }
        
        @Override
        public void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
            int x = getX();
            int y = getY();
            int w = getWidth();
            int h = getHeight();
            
            context.fill(x, y, x + w, y + h, 0x20000000);
            
            int borderColor = isHovered() ? 0xFFFFFFFF : 0xFFA0A0A0;
            context.fill(x, y, x + w, y + 1, borderColor); 
            context.fill(x, y + h - 1, x + w, y + h, borderColor); 
            context.fill(x, y, x + 1, y + h, borderColor); 
            context.fill(x + w - 1, y, x + w, y + h, borderColor); 
            
            int handleWidth = 8;
            int handleX = x + (int)(this.value * (double)(w - handleWidth));
            int handleY = y;
            
            int handleBorderColor = (isHovered() || isFocused()) ? 0xFFFFFFFF : 0xFFCCCCCC;
            
            context.fill(handleX, handleY, handleX + handleWidth, handleY + h, 0xFF000000); 
            
            context.fill(handleX, handleY, handleX + handleWidth, handleY + 1, handleBorderColor);
            context.fill(handleX, handleY + h - 1, handleX + handleWidth, handleY + h, handleBorderColor);
            context.fill(handleX, handleY, handleX + 1, handleY + h, handleBorderColor);
            context.fill(handleX + handleWidth - 1, handleY, handleX + handleWidth, handleY + h, handleBorderColor);
            
            if (showValue) {
                context.drawCenteredString(Minecraft.getInstance().font, getMessage(), x + w / 2, y + (h - 8) / 2, 0xFFFFFFFF);
            }
            
            // Cursor support
            if (isDragging) {
                context.requestCursor(DamageConfigScreen.CURSOR_H_RESIZE);
            } else if (isHovered()) {
                context.requestCursor(DamageConfigScreen.CURSOR_HAND);
            }
        }
    }

    private static class IntegerSliderEntry extends OptionEntry {
        private final StyledSliderWidget slider;
        private final Component label;
        private final Minecraft minecraft = Minecraft.getInstance();

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
        public void renderContent(GuiGraphics context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            context.drawString(minecraft.font, label, x, y + 8, 0xFFFFFFFF, true);
            slider.setX(x + entryWidth - 110);
            slider.setY(y + 2);
            slider.render(context, mouseX, mouseY, tickDelta);
        }
        @Override public List<? extends GuiEventListener> children() { return Collections.singletonList(slider); }
        @Override
        public List<? extends net.minecraft.client.gui.narration.NarratableEntry> narratables() {
            return Collections.emptyList();
        }
    }
    
    private class ResetButtonEntry extends OptionEntry {
        private final StyledButton button;
        private final Runnable onReset;
        private final Component label;
        private final Minecraft minecraft = Minecraft.getInstance();

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
        public void renderContent(GuiGraphics context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
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
            
            context.drawString(minecraft.font, label, x, y + 8, 0xFFFC887E, true);
            
            button.setX(x + entryWidth - 110);
            button.setY(y + 2);
            button.setFocused(false);
            button.render(context, mouseX, mouseY, tickDelta);
        }
        @Override public List<? extends GuiEventListener> children() { return Collections.singletonList(button); }
        @Override
        public List<? extends net.minecraft.client.gui.narration.NarratableEntry> narratables() {
            return Collections.emptyList();
        }
    }
    
    private static class ExpandableHeaderEntry extends OptionEntry {
        private final PlainTextButton button;
        private final Component label;
        private final Minecraft minecraft = Minecraft.getInstance();
        private boolean expanded;
        private final Consumer<Boolean> onToggle;
        
        public ExpandableHeaderEntry(String key, boolean expanded, Consumer<Boolean> onToggle) {
            this.label = Component.translatable(key);
            this.expanded = expanded;
            this.onToggle = onToggle;
            
            this.button = new PlainTextButton(0, 0, 20, 20, Component.literal(expanded ? "-" : "+"), () -> onToggle.accept(!expanded), 0xFFFFFFFF, 0xFFFBFB54);
        }
        
        @Override
        public void renderContent(GuiGraphics context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            boolean isHovered = hovered || (mouseX >= x && mouseX <= x + entryWidth && mouseY >= y && mouseY <= y + entryHeight);
            int color = isHovered ? 0xFFFBFB54 : 0xFFFFFFFF;
            
            context.drawString(minecraft.font, label, x, y + 8, color, true);
            
            button.setX(x + entryWidth - 25);
            button.setY(y + 2);
            button.setFocused(false);
            button.setForceHover(isHovered);
            button.render(context, mouseX, mouseY, tickDelta);
        }
        
        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
             if (this.button.mouseClicked(event, bl)) return true;
             
             if (this.onToggle != null) {
                 this.onToggle.accept(!expanded);
                 if (Minecraft.getInstance().screen instanceof DamageConfigScreen s) {
                     s.playClickSound();
                 }
                 return true;
             }
             return false;
        }
        
        @Override public List<? extends GuiEventListener> children() { return Collections.singletonList(button); }
        @Override
        public List<? extends net.minecraft.client.gui.narration.NarratableEntry> narratables() {
            return Collections.emptyList();
        }
    }

    private static class DamageThresholdEntry extends OptionEntry {
        private final EditBox valField;
        private final EditBox colField;
        private final PlainTextButton delBtn;
        private final Minecraft minecraft = Minecraft.getInstance();
        private int currentColor;
        
        public DamageThresholdEntry(DamageEngineConfig.DamageThreshold dt, Runnable onDelete) {
            this.currentColor = dt.color;
            
            this.valField = new EditBox(minecraft.font, 0, 0, 40, 20, Component.empty());
            this.valField.setBordered(false);
            this.valField.setValue(String.format("%.0f", dt.threshold));
            this.valField.setResponder(s -> {
                try { dt.threshold = Float.parseFloat(s); } catch(Exception ignored){}
            });
            
            this.colField = new EditBox(minecraft.font, 0, 0, 55, 20, Component.empty());
            this.colField.setBordered(false);
            this.colField.setMaxLength(7);
            this.colField.setValue("#" + String.format("%06X", dt.color & 0xFFFFFF));
            this.colField.setResponder(s -> {
                try { 
                    String hex = s.startsWith("#") ? s.substring(1) : s;
                    int v = (int)Long.parseLong(hex, 16);
                    dt.color = v;
                    this.currentColor = v;
                } catch(Exception ignored){}
            });
            
            this.delBtn = new PlainTextButton(0, 0, 20, 20, Component.literal("-"), onDelete, 0xFFFFFFFF, 0xFFFBFB54);
        }
        
        @Override
        public void renderContent(GuiGraphics context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
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
            
            context.drawString(minecraft.font, label, x + 10, y + 8, labelColor, true);
            
            context.fill(valBoxX, valBoxY, valBoxX + valBoxW, valBoxY + valBoxH, 0x20000000);
            int vx = valBoxX, vy = valBoxY, vw = valBoxW, vh = valBoxH;
            int valBorderColor = (valField.isFocused() || valField.isMouseOver(mouseX, mouseY)) ? 0xFFFFFFFF : 0xFFA0A0A0;
            context.fill(vx, vy, vx + vw, vy + 1, valBorderColor);
            context.fill(vx, vy + vh - 1, vx + vw, vy + vh, valBorderColor);
            context.fill(vx, vy, vx + 1, vy + vh, valBorderColor);
            context.fill(vx + vw - 1, vy, vx + vw, vy + vh, valBorderColor);
            
            context.fill(colBoxX, colBoxY, colBoxX + colBoxW, colBoxY + colBoxH, 0x20000000);
            int cx = colBoxX, cy = colBoxY, cw = colBoxW, ch = colBoxH;
            int colBorderColor = (colField.isFocused() || colField.isMouseOver(mouseX, mouseY)) ? 0xFFFFFFFF : 0xFFA0A0A0;
            context.fill(cx, cy, cx + cw, cy + 1, colBorderColor);
            context.fill(cx, cy + ch - 1, cx + cw, cy + ch, colBorderColor);
            context.fill(cx, cy, cx + 1, cy + ch, colBorderColor);
            context.fill(cx + cw - 1, cy, cx + cw, cy + ch, colBorderColor);
            
            valField.render(context, mouseX, mouseY, tickDelta);
            colField.render(context, mouseX, mouseY, tickDelta);
            delBtn.render(context, mouseX, mouseY, tickDelta);
            
            context.fill(previewX - 1, previewY - 1, previewX + previewSize + 1, previewY + previewSize + 1, 0xFFFFFFFF);
            context.fill(previewX, previewY, previewX + previewSize, previewY + previewSize, 0xFF000000 | (currentColor & 0xFFFFFF));
        }
        
        @Override
        public List<? extends GuiEventListener> children() {
            return java.util.Arrays.asList(valField, colField, delBtn);
        }
        @Override
        public List<? extends net.minecraft.client.gui.narration.NarratableEntry> narratables() {
            return Collections.emptyList();
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
        public void renderContent(GuiGraphics context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            boolean rowHovered = mouseX >= x && mouseX <= x + entryWidth && mouseY >= y && mouseY <= y + entryHeight;
            button.setX(x + entryWidth - 25);
            button.setY(y + 2);
            button.setFocused(false);
            button.setForceHover(rowHovered);
            
            button.render(context, mouseX, mouseY, tickDelta);
        }
        
        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
            if (isMouseOver(event.x(), event.y())) {
                action.run();
                return true;
            }
            return false;
        }
        
        @Override public List<? extends GuiEventListener> children() { return Collections.singletonList(button); }
        @Override
        public List<? extends net.minecraft.client.gui.narration.NarratableEntry> narratables() {
            return Collections.emptyList();
        }
    }
    
    private static class ButtonActionEntry extends OptionEntry {
        private final StyledButton button;
        private final Component label;
        private final Minecraft minecraft = Minecraft.getInstance();
        public ButtonActionEntry(String labelKey, String buttonKey, Runnable action) {
            this.label = Component.translatable(labelKey); 
            this.button = new StyledButton(0, 0, 100, 20, Component.translatable(buttonKey), action);
        }
        @Override
        public void renderContent(GuiGraphics context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            context.drawString(minecraft.font, label, x, y + 8, 0xFFFFFFFF, true);
            button.setX(x + entryWidth - 110);
            button.setY(y + 2);
            button.setFocused(false);
            button.render(context, mouseX, mouseY, tickDelta);
        }
        @Override public List<? extends GuiEventListener> children() { return Collections.singletonList(button); }
        @Override
        public List<? extends net.minecraft.client.gui.narration.NarratableEntry> narratables() {
            return Collections.emptyList();
        }
    }
    
    private static class NumericEntry extends OptionEntry {
        private final EditBox field;
        private final Component label;
        private final Minecraft minecraft = Minecraft.getInstance();
        public NumericEntry(String key, float initial, Consumer<Float> onChange) {
            this.label = Component.translatable(key);
            this.field = new EditBox(minecraft.font, 0, 0, 100, 20, Component.empty());
            this.field.setBordered(false);
            this.field.setValue(String.valueOf(initial));
            this.field.setResponder(s -> { try { onChange.accept(Float.parseFloat(s)); } catch(Exception ignored){} });
        }
        @Override
        public void renderContent(GuiGraphics context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            context.drawString(minecraft.font, label, x, y + 8, 0xFFFFFFFF, true);
            
            int boxX = x + entryWidth - 110;
            int boxY = y + 2;
            int boxW = 100;
            int boxH = 20;
            
            field.setX(boxX + 4);
            field.setY(boxY + 6);
            field.setWidth(boxW - 8);
            field.setHeight(12);
            
            context.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0x20000000);
            int fx = boxX, fy = boxY, fw = boxW, fh = boxH;
            int borderColor = (field.isFocused() || field.isMouseOver(mouseX, mouseY)) ? 0xFFFFFFFF : 0xFFA0A0A0;
            context.fill(fx, fy, fx + fw, fy + 1, borderColor);
            context.fill(fx, fy + fh - 1, fx + fw, fy + fh, borderColor);
            context.fill(fx, fy, fx + 1, fy + fh, borderColor);
            context.fill(fx + fw - 1, fy, fx + fw, fy + fh, borderColor);
            
            field.render(context, mouseX, mouseY, tickDelta);
        }
        @Override public List<? extends GuiEventListener> children() { return Collections.singletonList(field); }
        @Override
        public List<? extends net.minecraft.client.gui.narration.NarratableEntry> narratables() {
            return Collections.emptyList();
        }
    }
    
    private static class HexColorEntry extends OptionEntry {
        private final EditBox field;
        private final Component label;
        private final Minecraft minecraft = Minecraft.getInstance();
        private int currentColor;

        public HexColorEntry(String key, int initial, Consumer<Integer> onChange) {
            this.label = Component.translatable(key);
            this.currentColor = initial;
            this.field = new EditBox(minecraft.font, 0, 0, 75, 20, Component.empty());
            this.field.setBordered(false);
            this.field.setMaxLength(7);
            this.field.setValue("#" + String.format("%06X", initial & 0xFFFFFF));
            this.field.setResponder(s -> { 
                try { 
                    String hex = s.startsWith("#") ? s.substring(1) : s;
                    int val = (int)Long.parseLong(hex, 16);
                    this.currentColor = val;
                    onChange.accept(val); 
                } catch(Exception ignored){} 
            });
        }
        @Override
        public void renderContent(GuiGraphics context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            context.drawString(minecraft.font, label, x, y + 8, 0xFFFFFFFF, true);
            
            int startX = x + entryWidth - 110;
            int boxX = startX;
            int boxY = y + 2;
            int boxW = 75;
            int boxH = 20;
            
            field.setX(boxX + 4);
            field.setY(boxY + 6);
            field.setWidth(boxW - 8);
            field.setHeight(12);
            
            context.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0x20000000);
            int fx = boxX, fy = boxY, fw = boxW, fh = boxH;
            int borderColor = (field.isFocused() || field.isMouseOver(mouseX, mouseY)) ? 0xFFFFFFFF : 0xFFA0A0A0;
            context.fill(fx, fy, fx + fw, fy + 1, borderColor);
            context.fill(fx, fy + fh - 1, fx + fw, fy + fh, borderColor);
            context.fill(fx, fy, fx + 1, fy + fh, borderColor);
            context.fill(fx + fw - 1, fy, fx + fw, fy + fh, borderColor);
            
            field.render(context, mouseX, mouseY, tickDelta);
            
            int previewSize = 17;
            int previewX = startX + 80;
            
            int previewY = y + 2 + 1; 
            
            context.fill(previewX - 1, previewY - 1, previewX + previewSize + 1, previewY + previewSize + 1, 0xFFFFFFFF);
            
            context.fill(previewX, previewY, previewX + previewSize, previewY + previewSize, 0xFF000000 | (currentColor & 0xFFFFFF));
        }
        @Override public List<? extends GuiEventListener> children() { return Collections.singletonList(field); }
        @Override
        public List<? extends net.minecraft.client.gui.narration.NarratableEntry> narratables() {
            return Collections.emptyList();
        }
    }

    private static class SpacerEntry extends OptionEntry {
        public SpacerEntry(int height) { }
        @Override
        protected boolean shouldHighlight() { return false; }
        @Override
        public void renderContent(GuiGraphics context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
        }
        @Override public List<? extends GuiEventListener> children() { return Collections.emptyList(); }
        @Override
        public List<? extends net.minecraft.client.gui.narration.NarratableEntry> narratables() {
            return Collections.emptyList();
        }
    }
    
    private static class KeybindEntry extends OptionEntry {
        private final Component label;
        private final BindingButton button;
        private final KeyMapping keyBinding;
        
        private class BindingButton extends AbstractWidget {
            private final Runnable onPress;

            public BindingButton(int x, int y, int width, int height, Component message, Runnable onPress) {
                super(x, y, width, height, message);
                this.onPress = onPress;
            }
            
            @Override
            public void playDownSound(net.minecraft.client.sounds.SoundManager handler) {
                if (Minecraft.getInstance().screen instanceof DamageConfigScreen s) {
                    s.playClickSound();
                } else {
                    super.playDownSound(handler);
                }
            }
            
            @Override
            public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
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
            protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
                context.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0x20000000);
                
                int borderColor = isHovered() ? 0xFFFFFFFF : 0xFFA0A0A0;
                int x = getX(); int y = getY(); int w = getWidth(); int h = getHeight();
                context.fill(x, y, x + w, y + 1, borderColor); 
                context.fill(x, y + h - 1, x + w, y + h, borderColor); 
                context.fill(x, y, x + 1, y + h, borderColor); 
                context.fill(x + w - 1, y, x + w, y + h, borderColor); 
                
                if (isHovered()) {
                    context.requestCursor(DamageConfigScreen.CURSOR_HAND);
                }
                
                context.drawCenteredString(Minecraft.getInstance().font, getMessage(), getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, 0xFFFFFFFF);
            }
            
            @Override
            protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
                this.defaultButtonNarrationText(narrationElementOutput);
            }
            
            @Override
            public boolean keyPressed(KeyEvent event) {
                if (isBinding()) {
                    if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
                        keyBinding.setKey(InputConstants.UNKNOWN);
                    } else {
                        keyBinding.setKey(InputConstants.Type.KEYSYM.getOrCreate(event.key()));
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
        }
        
        public void bindMouse(int button) {
            keyBinding.setKey(InputConstants.Type.MOUSE.getOrCreate(button));
            finishBinding();
        }
        
        private void updateMessage() {
            Component text;
            List<KeyMapping> conflicts = new ArrayList<>();
            
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
                    
                    // Check for key conflicts
                    if (!keyBinding.isUnbound()) {
                        for (KeyMapping other : Minecraft.getInstance().options.keyMappings) {
                            if (other != keyBinding && !other.isUnbound() && other.same(keyBinding)) {
                                conflicts.add(other);
                            }
                        }
                    }
                    
                    if (!conflicts.isEmpty()) {
                        text = Component.literal("[ ").withColor(0xFFFFFF55)
                            .append(keyText.withColor(0xFFFFFFFF))
                            .append(Component.literal(" ]").withColor(0xFFFFFF55));
                    } else {
                        text = keyText;
                    }
                }
            }
            
            button.setMessage(text);
            
            if (!conflicts.isEmpty()) {
                MutableComponent tooltipText = Component.literal("该按键也用于：\n");
                for (int i = 0; i < conflicts.size(); i++) {
                    if (i > 0) {
                        tooltipText.append(Component.literal(", "));
                    }
                    tooltipText.append(Component.translatable(conflicts.get(i).getName()));
                }
                button.setTooltip(Tooltip.create(tooltipText.withColor(0xFFFFFFFF)));
            } else {
                button.setTooltip(null);
            }
        }
        
        @Override
        public void renderContent(GuiGraphics context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            context.drawString(Minecraft.getInstance().font, label, x, y + 8, 0xFFFFFFFF, true);
            
            button.setX(x + entryWidth - 110);
            button.setY(y + 2);
            button.setWidth(100); 
            button.setFocused(false);
            
            button.render(context, mouseX, mouseY, tickDelta);
        }
        
        @Override public List<? extends GuiEventListener> children() { return Collections.singletonList(button); }
        @Override
        public List<? extends net.minecraft.client.gui.narration.NarratableEntry> narratables() {
            return Collections.emptyList();
        }
    }
    
    private static class WeightEntry extends OptionEntry {
        private final EditBox input;
        private final Component label;
        private final Component hint;
        private final Minecraft minecraft = Minecraft.getInstance();
        private final float[] valueRef;
        
        public WeightEntry(String key, float current, Consumer<Float> onChange, String hintKey) {
            this.label = Component.translatable(key);
            this.hint = hintKey != null ? Component.translatable(hintKey).withColor(0xFFAAAAAA) : null;
            this.valueRef = new float[]{current};
            this.input = new EditBox(minecraft.font, 0, 0, 50, 20, Component.empty());
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
        public void renderContent(GuiGraphics context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            context.drawString(minecraft.font, label, x, y + 8, 0xFFFFFFFF, true);
            if (hint != null) {
                int lw = minecraft.font.width(label);
                context.drawString(minecraft.font, hint, x + lw + 5, y + 8, 0xFFAAAAAA, true);
            }
            int boxW = 100;
            int boxX = x + entryWidth - 110;
            int boxY = y + 2;
            context.fill(boxX, boxY, boxX + boxW, boxY + 18, 0x20000000);
            int bc = (input.isFocused() || input.isMouseOver(mouseX, mouseY)) ? 0xFFFFFFFF : 0xFFA0A0A0;
            context.fill(boxX, boxY, boxX + boxW, boxY + 1, bc);
            context.fill(boxX, boxY + 17, boxX + boxW, boxY + 18, bc);
            context.fill(boxX, boxY, boxX + 1, boxY + 18, bc);
            context.fill(boxX + boxW - 1, boxY, boxX + boxW, boxY + 18, bc);
            input.setX(boxX + 3);
            input.setY(boxY + 4);
            input.setWidth(boxW - 6);
            input.setHeight(12);
            input.render(context, mouseX, mouseY, tickDelta);
        }
        @Override public List<? extends GuiEventListener> children() { return Collections.singletonList(input); }
        @Override
        public List<? extends net.minecraft.client.gui.narration.NarratableEntry> narratables() {
            return Collections.emptyList();
        }
    }
    
    private static class RatingGradeEntry extends OptionEntry {
        private final EditBox scoreField;
        private final EditBox textField;
        private final PlainTextButton delBtn;
        private final Minecraft minecraft = Minecraft.getInstance();
        
        public RatingGradeEntry(DamageEngineConfig.RatingGrade g, Runnable onDelete) {
            this.scoreField = new EditBox(minecraft.font, 0, 0, 60, 20, Component.empty());
            this.scoreField.setBordered(false);
            this.scoreField.setValue(String.format("%.0f", g.minScore));
            this.scoreField.setResponder(s -> {
                try { g.minScore = Float.parseFloat(s); } catch(Exception ignored){}
            });
            
            this.textField = new EditBox(minecraft.font, 0, 0, 60, 20, Component.empty());
            this.textField.setBordered(false);
            this.textField.setMaxLength(4);
            this.textField.setValue(g.text);
            this.textField.setResponder(s -> {
                g.text = s;
            });
            
            this.delBtn = new PlainTextButton(0, 0, 20, 20, Component.literal("-"), onDelete, 0xFFFFFFFF, 0xFFFBFB54);
        }
        
        @Override
        public void renderContent(GuiGraphics context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
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
            textField.setY(boxY + 5);
            textField.setWidth(boxW - 8);
            textField.setHeight(12);
            
            int scoreBoxX = textBoxX - 3 - boxW;
            
            scoreField.setX(scoreBoxX + 4);
            scoreField.setY(boxY + 5);
            scoreField.setWidth(boxW - 8);
            scoreField.setHeight(12);
            
            Component label = Component.literal("≥");
            context.drawString(minecraft.font, label, x + 10, y + 8, 0xFFFFFFFF, true);
            
            drawBox(context, scoreBoxX, boxY, boxW, boxH, scoreField, mouseX, mouseY);
            drawBox(context, textBoxX, boxY, boxW, boxH, textField, mouseX, mouseY);
            
            scoreField.render(context, mouseX, mouseY, tickDelta);
            textField.render(context, mouseX, mouseY, tickDelta);
            delBtn.render(context, mouseX, mouseY, tickDelta);
        }
        
        private void drawBox(GuiGraphics context, int bx, int by, int bw, int bh, EditBox field, int mouseX, int mouseY) {
            context.fill(bx, by, bx + bw, by + bh, 0x20000000);
            int bc = (field.isFocused() || field.isMouseOver(mouseX, mouseY)) ? 0xFFFFFFFF : 0xFFA0A0A0;
            context.fill(bx, by, bx + bw, by + 1, bc);
            context.fill(bx, by + bh - 1, bx + bw, by + bh, bc);
            context.fill(bx, by, bx + 1, by + bh, bc);
            context.fill(bx + bw - 1, by, bx + bw, by + bh, bc);
        }
        
        @Override
        public List<? extends GuiEventListener> children() {
            return java.util.Arrays.asList(scoreField, textField, delBtn);
        }
        @Override
        public List<? extends net.minecraft.client.gui.narration.NarratableEntry> narratables() {
            return Collections.emptyList();
        }
    }
    
    private static class RatingGradeAppearanceEntry extends OptionEntry {
        private final DamageEngineConfig.RatingGrade grade;
        private final DamageEngineConfig config;
        private final EditBox colField;
        private final StyledButton selectImageBtn;
        private final PlainTextButton resetImageBtn;
        private final Minecraft minecraft = Minecraft.getInstance();
        private int currentColor;
        
        public RatingGradeAppearanceEntry(DamageEngineConfig.RatingGrade g, DamageEngineConfig config) {
            this.grade = g;
            this.config = config;
            this.currentColor = g.color;
            
            this.colField = new EditBox(minecraft.font, 0, 0, 75, 20, Component.empty());
            this.colField.setBordered(false);
            this.colField.setMaxLength(7);
            this.colField.setValue("#" + String.format("%06X", g.color & 0xFFFFFF));
            this.colField.setResponder(s -> {
                try {
                    String hex = s.startsWith("#") ? s.substring(1) : s;
                    int v = (int)Long.parseLong(hex, 16);
                    g.color = v;
                    this.currentColor = v;
                } catch(Exception ignored){}
            });
            
            this.selectImageBtn = new StyledButton(0, 0, 100, 20, Component.translatable("option.damage-engine.select_image"), this::onSelectImage);
            this.resetImageBtn = new PlainTextButton(0, 0, 20, 20, Component.literal("×"), this::onResetImage, 0xFFFC887E, 0xFFFBFB54);
            
            java.io.File configDir = new java.io.File(minecraft.gameDirectory, "config/damage-engine/images");
            if (!configDir.exists()) {
                configDir.mkdirs();
            }
        }
        
        private void onSelectImage() {
            try {
                java.io.File imagesDir = new java.io.File(minecraft.gameDirectory, "config/damage-engine/images");
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
        public void renderContent(GuiGraphics context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
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
                
                String pathText = grade.imagePath != null && !grade.imagePath.isEmpty() ? grade.imagePath : "未设置";
                
                if (pathText.contains("/")) {
                    pathText = pathText.substring(pathText.lastIndexOf("/") + 1);
                }
                
                int maxWidth = 80;
                int textWidth = minecraft.font.width(pathText);
                
                if (textWidth > maxWidth) {
                    while (textWidth > maxWidth - 20 && pathText.length() > 4) {
                        pathText = pathText.substring(0, pathText.length() - 1);
                        textWidth = minecraft.font.width(pathText + "...");
                    }
                    pathText = pathText + "...";
                }
                
                int pathX = rightX - 110 - 5 - textWidth - resetBtnW - 5;
                if (pathX < x + 10) pathX = x + 10;
                
                Component label = Component.literal(grade.text);
                context.drawString(minecraft.font, label, x + 10, y + 8, 0xFFFFFFFF, true);
                
                context.drawString(minecraft.font, Component.literal(pathText), pathX, y + 8, 0xFFFFFFFF, true);
                resetImageBtn.render(context, mouseX, mouseY, tickDelta);
                selectImageBtn.render(context, mouseX, mouseY, tickDelta);
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
                colField.setFocused(false);
                
                Component label = Component.literal(grade.text);
                context.drawString(minecraft.font, label, x + 10, y + 8, 0xFFFFFFFF, true);
                
                drawBox(context, colBoxX, boxY, colBoxW, 20, colField, mouseX, mouseY);
                colField.render(context, mouseX, mouseY, tickDelta);
                
                context.fill(previewX - 1, previewY - 1, previewX + previewSize + 1, previewY + previewSize + 1, 0xFFFFFFFF);
                context.fill(previewX, previewY, previewX + previewSize, previewY + previewSize, 0xFF000000 | (currentColor & 0xFFFFFF));
            }
        }
        
        private void drawBox(GuiGraphics context, int bx, int by, int bw, int bh, EditBox field, int mouseX, int mouseY) {
            context.fill(bx, by, bx + bw, by + bh, 0x20000000);
            int bc = (field.isFocused() || field.isMouseOver(mouseX, mouseY)) ? 0xFFFFFFFF : 0xFFA0A0A0;
            context.fill(bx, by, bx + bw, by + 1, bc);
            context.fill(bx, by + bh - 1, bx + bw, by + bh, bc);
            context.fill(bx, by, bx + 1, by + bh, bc);
            context.fill(bx + bw - 1, by, bx + bw, by + bh, bc);
        }
        
        @Override
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
        @Override
        public List<? extends net.minecraft.client.gui.narration.NarratableEntry> narratables() {
            return Collections.emptyList();
        }
    }
}
