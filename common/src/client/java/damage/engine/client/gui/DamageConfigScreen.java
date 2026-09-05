package damage.engine.client.gui;

import damage.engine.ClientKeybindings;
import damage.engine.DamageEngineClient;
import damage.engine.DamageEngineConfig;
import com.google.gson.GsonBuilder;
import damage.engine.hud.DamageHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

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
    
    // Cursor types for 1.21.11 mouse cursor style support
    public static final com.mojang.blaze3d.platform.cursor.CursorType CURSOR_HAND = createCursor(0x00036004, "hand");
    public static final com.mojang.blaze3d.platform.cursor.CursorType CURSOR_EW = createCursor(0x00036005, "ewresize");
    public static final com.mojang.blaze3d.platform.cursor.CursorType CURSOR_NS = createCursor(0x00036006, "nsresize");
    public static final com.mojang.blaze3d.platform.cursor.CursorType CURSOR_MOVE = createCursor(0x00036009, "move");
    public static final com.mojang.blaze3d.platform.cursor.CursorType CURSOR_NWSE = createCursor(0x00036007, "nwseresize");

    private static com.mojang.blaze3d.platform.cursor.CursorType createCursor(int handle, String name) {
        try {
            return com.mojang.blaze3d.platform.cursor.CursorType.createStandardCursor(handle, name, com.mojang.blaze3d.platform.cursor.CursorType.DEFAULT);
        } catch (Exception e) {
            return com.mojang.blaze3d.platform.cursor.CursorType.DEFAULT;
        }
    }

    private int selectedTab = 0;
    private ConfigOptionListWidget optionList;
    private double lastScroll = 0;
    private boolean hasUnsavedChanges = false;
    private String validationError = null;
    private ColorPickerPopup colorPicker = null;
    /** 预览 HUD 复用单例,避免每帧 new DamageHud() 的开销 */
    private final DamageHud previewHud = new DamageHud();
    
    // Expand state trackers
    private final Map<String, Boolean> expandStates = new HashMap<>();
    
    private int resetButtonState = 0;
    private long resetButtonActionTime = 0;
    /** 当前处于绑定状态的键位条目(单一数据源,避免多个条目同时进入绑定) */
    private KeybindEntry bindingEntry = null;
    private String configSnapshot;

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
            if (optionList != null) {
                lastScroll = optionList.scrollAmount();
            }
            closeColorPicker(); // 重建界面时关闭悬浮颜色选择器
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
                Component.translatable("button.damage-engine.save_close").withColor(0xFFB7F3C8), () -> {
                    List<String> invalids = validateAllTabs();
                    if (!invalids.isEmpty()) {
                        StringBuilder sb = new StringBuilder();
                        for (String s : invalids) {
                            if (sb.length() > 0) sb.append("\n");
                            sb.append(Component.translatable("text.damage-engine.invalid_config", s).getString());
                        }
                        validationError = sb.toString();
                        return; // 阻止保存
                    }
                    validationError = null;
                    config.save();
                    hasUnsavedChanges = false;
                    this.minecraft.setScreen(parent);
                }));
            
            // Cancel (red)
            this.addRenderableWidget(new StyledButton(this.width - buttonWidth * 2 - 20, buttonY, buttonWidth, 20,
                Component.translatable("gui.cancel").withColor(0xFFFC887E), () -> {
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
                        .withColor(config.previewEnabled ? 0xFFB5F0C6 : 0xFFFC887E)), () -> {
                config.previewEnabled = !config.previewEnabled;
                previewBtnRef[0].setMessage(Component.translatable("text.damage-engine.preview").append(": ").append(
                    Component.translatable(config.previewEnabled ? "options.on" : "options.off")
                        .withColor(config.previewEnabled ? 0xFFB5F0C6 : 0xFFFC887E)));
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
        addOption(new SpacerEntry());
        addOption(new SpacerEntry());
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
        // Total damage colors by reached-damage thresholds (damage-reach value + color)
        addOption(new ExpandableHeaderEntry("option.damage-engine.total_damage_colors", "total_damage_colors", v -> refreshOptions()));
        if (isExpanded("total_damage_colors")) {
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

        // 全局伤害跳字 - 分项设置
        addOption(new ExpandableHeaderEntry("option.damage-engine.global_damage_numbers", "global_damage_numbers", v -> refreshOptions()));
        if (isExpanded("global_damage_numbers")) {
            addOption(new BooleanOptionEntry("option.damage-engine.showGlobalDamageIndicator", config.showGlobalDamageIndicator, v -> { config.showGlobalDamageIndicator = v; markChanged(); }));
            // (1) 玩家 显示距离
            addOption(new NumericEntry("option.damage-engine.global_player_distance", config.globalIndicatorMaxDistance,
                v -> { config.globalIndicatorMaxDistance = v; markChanged(); },
                Component.translatable("hint.damage-engine.globalIndicatorMaxDistance")));
            // (2) 非玩家实体(可展开;悬停提示:通过添加实体注册名来显示/屏蔽)
            addOption(new ExpandableHeaderEntry("option.damage-engine.non_player_entities", "non_player_entities", v -> refreshOptions(),
                Component.translatable("hint.damage-engine.non_player_entities")));
            if (isExpanded("non_player_entities")) {
                // 整体模式:屏蔽/显示
                addOption(new ModeSelectorEntry("option.damage-engine.entity_mode", config.globalEntityBlockMode,
                    v -> { config.globalEntityBlockMode = v; markChanged(); }));
                for (DamageEngineConfig.GlobalEntityRule r : new ArrayList<>(config.globalEntityRules)) {
                    if (r == null) continue;
                    addOption(new GlobalEntityRuleEntry(r, () -> {
                        config.globalEntityRules.remove(r);
                        markChanged();
                        refreshOptions();
                    }));
                }
                // 添加项注册名默认空(由用户输入);不做 All 检查限制,存在 All 时仍可继续添加(处理时以 All 规则为准,不会出错)
                AddButtonEntry addBtn = new AddButtonEntry(() -> {
                    config.globalEntityRules.add(new DamageEngineConfig.GlobalEntityRule("", 0f));
                    markChanged();
                    refreshOptions();
                });
                addOption(addBtn);
            }
        }
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
            addOption(new WeightEntry("option.damage-engine.damageScoreMultiplier", config.damageScoreMultiplier, v -> { config.damageScoreMultiplier = v; markChanged(); }, "hint.damage-engine.damageScoreMultiplier"));

            // 单次伤害数值加分(可添加项,默认空项)
            addOption(new ExpandableHeaderEntry("option.damage-engine.damage_size_bonus", "damage_size_bonus", v -> refreshOptions()));
            if (isExpanded("damage_size_bonus")) {
                for (DamageEngineConfig.DamageBonus b : new ArrayList<>(config.damageBonuses)) {
                    addOption(new DamageBonusEntry(b, () -> {
                        config.damageBonuses.remove(b);
                        markChanged();
                        refreshOptions();
                    }));
                }
                addOption(new AddButtonEntry(() -> {
                    config.damageBonuses.add(new DamageEngineConfig.DamageBonus(10f, 0f)); // 伤害数值默认 10,增加分数默认空
                    markChanged();
                    refreshOptions();
                }));
            }
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
        addOption(new ExpandableHeaderEntry("option.damage-engine.debugMode", "debugMode", v -> refreshOptions()));
        if (isExpanded("debugMode")) {
            addOption(new BooleanOptionEntry("option.damage-engine.debugShowDamageInfo", config.debugShowDamageInfo, v -> { config.debugShowDamageInfo = v; markChanged(); }));
            addOption(new BooleanOptionEntry("option.damage-engine.debugShowRating", config.debugShowRating, v -> { config.debugShowRating = v; markChanged(); }));
        }
        
        addOption(new ResetButtonEntry("option.damage-engine.reset", () -> {
            config.resetToDefaults();
            // Reset mod keybindings to their default (unbound) state
            ClientKeybindings.configKeyBinding.setKey(com.mojang.blaze3d.platform.InputConstants.UNKNOWN);
            ClientKeybindings.toggleHudKeyBinding.setKey(com.mojang.blaze3d.platform.InputConstants.UNKNOWN);
            ClientKeybindings.clearDamageKeyBinding.setKey(com.mojang.blaze3d.platform.InputConstants.UNKNOWN);
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
        double scroll = optionList.scrollAmount();
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

    /**
     * 校验当前标签页所有输入项,返回全部无效项名(可为多个)。
     */
    private List<String> validateAllInputs() {
        List<String> errors = new ArrayList<>();
        if (optionList == null) return errors;
        for (GuiEventListener widget : optionList.children()) {
            if (widget instanceof OptionEntry entry) {
                String invalid = entry.validate();
                if (invalid != null) {
                    errors.add(invalid);
                }
            }
        }
        return errors;
    }

    /** 所有可展开分组 key(校验时强制全部展开,避免漏检折叠项) */
    private static final String[] ALL_EXPAND_KEYS = {
        "total_damage_colors", "rating_points", "damage_size_bonus", "rating_grades",
        "rating_appearance", "debugMode", "global_damage_numbers", "non_player_entities"
    };

    /**
     * 配置级文本校验:检查不受 UI 展开/重建影响的 String 字段是否为空,返回全部无效项名。
     */
    private List<String> validateConfigValues() {
        List<String> errors = new ArrayList<>();
        if (config.killText == null || config.killText.trim().isEmpty()) {
            errors.add(Component.translatable("option.damage-engine.killText").getString());
        }
        if (config.ratingGrades != null) {
            for (DamageEngineConfig.RatingGrade g : config.ratingGrades) {
                if (g.text == null || g.text.trim().isEmpty()) {
                    errors.add(Component.translatable("option.damage-engine.rating_grades").getString());
                }
            }
        }
        if (config.globalEntityRules != null) {
            for (DamageEngineConfig.GlobalEntityRule r : config.globalEntityRules) {
                if (r.registryName == null || r.registryName.trim().isEmpty()) {
                    errors.add(Component.translatable("option.damage-engine.non_player_entities").getString());
                }
            }
        }
        return errors;
    }

    /**
     * 保存前遍历校验所有标签页的输入项(含展开项),返回全部无效项名(去重)。
     * 校验使用各页暂存的输入文本,不重置用户编辑状态。
     */
    private List<String> validateAllTabs() {
        List<String> errors = new ArrayList<>();
        if (optionList == null) return errors;
        // 1) 当前可见输入框(不重建,保留用户正在编辑的文本状态)
        errors.addAll(validateAllInputs());
        // 2) 配置级文本校验(不受折叠/重建影响)
        errors.addAll(validateConfigValues());
        int originalTab = selectedTab;
        double scroll = optionList.scrollAmount();
        Map<String, Boolean> savedExpands = new HashMap<>(expandStates);
        try {
            // 强制展开所有分组,确保折叠中的输入项也被校验
            for (String k : ALL_EXPAND_KEYS) {
                expandStates.put(k, true);
            }
            for (int tab = 0; tab < TAB_COUNT; tab++) {
                selectedTab = tab;
                optionList.clearEntriesPublic();
                initOptionEntries();
                errors.addAll(validateAllInputs());
            }
            // 去重(保持顺序)
            List<String> dedup = new ArrayList<>();
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (String e : errors) {
                if (seen.add(e)) {
                    dedup.add(e);
                }
            }
            return dedup;
        } finally {
            expandStates.clear();
            expandStates.putAll(savedExpands);
            selectedTab = originalTab;
            optionList.clearEntriesPublic();
            initOptionEntries();
            optionList.setScrollAmount(Math.min(scroll, optionList.maxScrollAmount()));
        }
    }

    public boolean isBinding() {
        return bindingEntry != null;
    }

    /** 将指定条目设为绑定中;若其他条目正在绑定则先取消,保证同时只有一个绑定项 */
    public void setBindingEntry(KeybindEntry e) {
        if (bindingEntry != null && bindingEntry != e) {
            bindingEntry.binding = false;
            bindingEntry.updateMessage();
        }
        bindingEntry = e;
    }

    // ===== 悬浮颜色选择器 =====

    public void openColorPicker(int anchorX, int anchorY, int color, java.util.function.Consumer<Integer> onChange) {
        closeColorPicker();
        colorPicker = new ColorPickerPopup(this, anchorX, anchorY, color, onChange, this::closeColorPicker);
    }

    public void closeColorPicker() {
        if (colorPicker != null) {
            colorPicker = null;
        }
    }

    public boolean isColorPickerOpen() {
        return colorPicker != null;
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
    public boolean keyPressed(KeyEvent event) {
        if (colorPicker != null) {
            if (colorPicker.keyPressed(event)) return true;
            if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                closeColorPicker();
                return true;
            }
        }
        if (bindingEntry != null) {
            if (bindingEntry.getButton().keyPressed(event)) return true;
            if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                bindingEntry.setBinding(false);
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        double mouseX = event.x();
        double mouseY = event.y();

        // 悬浮颜色选择器优先处理
        if (colorPicker != null) {
            if (colorPicker.mouseClicked(event, bl)) {
                return true;
            }
            // 点击面板外:关闭
            closeColorPicker();
            return true;
        }

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
        
        if (bindingEntry != null) {
            bindingEntry.bindMouse(event.button());
            return true;
        }
        return super.mouseClicked(event, bl);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (colorPicker != null) {
            if (colorPicker.mouseDragged(event, dx, dy)) return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (colorPicker != null) {
            colorPicker.mouseReleased(event);
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (colorPicker != null) {
            if (colorPicker.charTyped(event)) return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (colorPicker != null) {
            if (colorPicker.keyReleased(event)) return true;
        }
        return super.keyReleased(event);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float delta) {
        // 1.21.11: in-game background blur is applied once by the render pipeline;
        // calling renderBackground() here would blur twice and crash.
        if (this.minecraft != null && this.minecraft.level == null) {
            this.extractPanorama(guiGraphics, delta);
        }
        
        // Render tabs
        int tabHeight = 24;
        int tabWidth = this.width / TAB_COUNT;
        for (int i = 0; i < TAB_COUNT; i++) {
            int tabX = i * tabWidth;
            int tabY = 0;
            
            boolean isSelected = i == selectedTab;
            boolean isHovered = mouseX >= tabX && mouseX < tabX + tabWidth && mouseY >= tabY && mouseY < tabY + tabHeight;
            if (isHovered) guiGraphics.requestCursor(CURSOR_HAND);
            
            int bgColor = isSelected ? 0x30000000 : (isHovered ? 0x20000000 : 0x10000000);
            guiGraphics.fill(tabX, tabY, tabX + tabWidth, tabY + tabHeight, bgColor);
            
            String tabText = Component.translatable(TAB_KEYS[i]).getString();
            int textColor = isSelected ? 0xFFFFFFFF : 0xFFA0A0A0;
            guiGraphics.centeredText(this.font, tabText, tabX + tabWidth / 2, tabY + 8, textColor);
            
            // Green bottom border for selected tab
            if (isSelected) {
                guiGraphics.fill(tabX, tabY + tabHeight - 2, tabX + tabWidth, tabY + tabHeight, 0xFFB5F0C6);
            }
        }
        
        // Separator below tabs
        guiGraphics.fill(0, tabHeight, this.width, tabHeight + 1, 0xFF555555);
        
        if (optionList != null) {
            int listMx = mouseX, listMy = mouseY;
            if (colorPicker != null && colorPicker.contains(mouseX, mouseY)) {
                // 悬浮窗遮挡区域内的鼠标坐标置为远离,禁用背后列表条目的悬停高亮/光标
                listMx = -100000;
                listMy = -100000;
            }
            optionList.extractWidgetRenderState(guiGraphics, listMx, listMy, delta);
            optionList.updateSmoothScroll(delta);
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
                guiGraphics.text(this.font, Component.literal(lines[i]).withColor(0xFFFC887E), hintX, hintY + i * 12, 0xFFFC887E);
            }
        }

        // 输入校验失败提示(阻止保存,支持多行)
        if (validationError != null) {
            String[] lines = validationError.split("\n");
            for (int i = 0; i < lines.length; i++) {
                guiGraphics.centeredText(this.font, lines[i], this.width / 2, footerY - 22 - i * 12, 0xFFFC887E);
            }
        }
        
        // Preview
        if (config.previewEnabled) {
            previewHud.renderPreview(guiGraphics, 30, this.height - 30);
        }
        
        // Render footer widgets (avoid super.render() blur)
        for (GuiEventListener child : this.children()) {
            if (child != optionList && child instanceof AbstractWidget w) {
                w.extractRenderState(guiGraphics, mouseX, mouseY, delta);
            }
        }
        // 悬浮颜色选择器(绘制在最上层)
        if (colorPicker != null) {
            colorPicker.render(guiGraphics, mouseX, mouseY, delta);
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
        public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
            if (this.active && this.visible && event.button() == 0 && this.isMouseOver(event.x(), event.y())) {
                this.playDownSound(Minecraft.getInstance().getSoundManager());
                this.onPress.run();
                return true;
            }
            return false;
        }
        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float d) {
            if (isHovered()) g.requestCursor(CURSOR_HAND);
            g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0x20000000);
            int bc = isHovered() ? 0xFFFFFFFF : 0xFFA0A0A0;
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            g.fill(x, y, x + w, y + 1, bc);
            g.fill(x, y + h - 1, x + w, y + h, bc);
            g.fill(x, y, x + 1, y + h, bc);
            g.fill(x + w - 1, y, x + w, y + h, bc);
            g.centeredText(Minecraft.getInstance().font, getMessage(), x + w / 2, y + (h - 8) / 2, 0xFFFFFFFF);
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
        public void setDefaultColor(int c) { this.defaultColor = c; }
        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
            if (this.active && this.visible && event.button() == 0 && this.isMouseOver(event.x(), event.y())) {
                this.playDownSound(Minecraft.getInstance().getSoundManager());
                this.onPress.run();
                return true;
            }
            return false;
        }
        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float d) {
            // 仅真实悬停时请求手型光标;forceHover(行级高亮)不影响光标,避免输入框上出现手型
            if (isHovered()) g.requestCursor(CURSOR_HAND);
            int c = (isHovered() || forceHover) ? hoverColor : defaultColor;
            g.centeredText(Minecraft.getInstance().font, getMessage(), getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, c);
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

    abstract static class OptionEntry extends ContainerObjectSelectionList.Entry<OptionEntry> {
        private double hoverAnim = 0;
        public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float dt) {
            DamageConfigScreen screen = (DamageConfigScreen) Minecraft.getInstance().screen;
            if (screen == null || screen.optionList == null) return;
            int y = this.getY();
            int x = this.getX();
            int ew = screen.optionList.getRowWidth();
            int eh = 24;
            int ll = screen.optionList.getX(), lr = screen.optionList.getRight();
            boolean isHov = mouseX >= ll && mouseX <= lr && mouseY >= y && mouseY < y + eh;
            if (isHov && shouldHighlight()) hoverAnim = Mth.lerp(0.1f, hoverAnim, 1.0f);
            else hoverAnim = Mth.lerp(0.1f, hoverAnim, 0.0f);
            if (hoverAnim > 0.01 && shouldHighlight()) {
                g.fill(ll, y, lr, y + eh, ((int)(26 * hoverAnim) << 24) | 0xFFFFFF);
            }
            renderContent(g, 0, y, x, ew, eh, mouseX, mouseY, isHov, dt);
        }
        public abstract void renderContent(GuiGraphicsExtractor g, int idx, int y, int x, int ew, int eh, int mx, int my, boolean hovered, float dt);
        @Override
        public void extractContent(GuiGraphicsExtractor g, int i, int j, boolean bl, float f) {
            this.render(g, i, j, bl, f);
        }
        public List<? extends GuiEventListener> children() { return Collections.emptyList(); }
        public List<? extends NarratableEntry> narratables() { return Collections.emptyList(); }
        protected boolean shouldHighlight() { return true; }
        /**
         * 校验该项输入是否有效。返回 null 表示有效;否则返回无效项名(用于提示 "<项名>配置无效")。
         */
        public String validate() { return null; }
        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
            for (GuiEventListener c : children()) {
                if (c instanceof EditBox tf) {
                    if (tf.isMouseOver(event.x(), event.y())) { tf.setFocused(true); return true; }
                    else tf.setFocused(false);
                } else if (c.mouseClicked(event, bl)) return true;
            }
            return false;
        }
        @Override public boolean mouseReleased(MouseButtonEvent event) { for (GuiEventListener c : children()) if (c.mouseReleased(event)) return true; return false; }
        @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) { for (GuiEventListener c : children()) if (c.mouseDragged(event, dx, dy)) return true; return false; }
        @Override public boolean keyPressed(KeyEvent event) { for (GuiEventListener c : children()) if (c.keyPressed(event)) return true; return false; }
        @Override public boolean charTyped(CharacterEvent event) { for (GuiEventListener c : children()) if (c.charTyped(event)) return true; return false; }
        @Override public boolean keyReleased(KeyEvent event) { for (GuiEventListener c : children()) if (c.keyReleased(event)) return true; return false; }
    }

    private class ConfigOptionListWidget extends ContainerObjectSelectionList<OptionEntry> {
        private double targetScroll = 0;
        private boolean isSmoothScrolling = false;
        private boolean scrolling = false;

        public ConfigOptionListWidget(Minecraft mc, int w, int h, int y, int ih) { super(mc, w, h, y, ih); }
        @Override public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
            if (event.x() >= getScrollbarX() && event.x() <= getScrollbarX() + 6) { scrolling = true; return true; }
            return super.mouseClicked(event, bl);
        }
        @Override public boolean mouseReleased(MouseButtonEvent event) { scrolling = false; return super.mouseReleased(event); }
        @Override protected void extractListBackground(GuiGraphicsExtractor g) {}
        public void clearEntriesPublic() { this.clearEntries(); }
        @Override public int getRowWidth() { return this.width - 20; }
        public void addEntryPublic(OptionEntry e) { this.addEntry(e); }
        @Override public int getRowLeft() { return this.getX() + 10; }
        public int getScrollbarX() { return this.getRight() - 6; }
        @Override public void updateWidgetNarration(NarrationElementOutput n) {}
        @Override protected void extractListSeparators(GuiGraphicsExtractor g) {}
        @Override protected void extractSelection(GuiGraphicsExtractor g, OptionEntry entry, int i) {}
        @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
            if (scrolling) {
                int bh = this.getHeight(), ch = this.maxScrollAmount() + this.getHeight();
                if (ch > bh) {
                    int sbh = Math.max(32, (int)((float)(bh * bh) / (float)ch));
                    if (sbh > bh) sbh = bh;
                    double d = Math.max(1, this.maxScrollAmount() / (double)(bh - sbh));
                    this.setScrollAmount(this.scrollAmount() + dy * d);
                }
                return true;
            }
            return super.mouseDragged(event, dx, dy);
        }
        @Override
        public void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
            this.enableScissor(g);
            this.extractListItems(g, mx, my, delta);
            g.disableScissor();
            int sx = this.getScrollbarX(), sy = this.getY(), sh = this.getHeight();
            // 1.21.11 cursor: while dragging the scrollbar show vertical resize; on hover show hand
            if (scrolling) {
                g.requestCursor(DamageConfigScreen.CURSOR_NS);
            } else if (mx >= sx && mx <= sx + 6 && my >= sy && my <= sy + sh) {
                g.requestCursor(DamageConfigScreen.CURSOR_HAND);
            }
            int ch = this.maxScrollAmount() + this.getHeight();
            if (ch > this.getHeight()) {
                int bh = Math.max(32, (int)((float)(this.getHeight() * this.getHeight()) / (float)ch));
                if (bh > this.getHeight()) bh = this.getHeight();
                int bt = (int)this.scrollAmount() * (this.getHeight() - bh) / this.maxScrollAmount() + this.getY();
                if (bt < this.getY()) bt = this.getY();
                g.fill(sx, sy, sx + 6, sy + sh, 0x80000000);
                g.fill(sx, bt, sx + 6, bt + bh, 0xA0FFFFFF);
            }
        }
        @Override public boolean mouseScrolled(double mx, double my, double hAmt, double vAmt) {
            this.targetScroll = this.scrollAmount() - vAmt * 80.0;
            this.targetScroll = Math.max(0, Math.min(this.targetScroll, this.maxScrollAmount()));
            this.isSmoothScrolling = true;
            return true;
        }
        public void updateSmoothScroll(float delta) {
            if (isSmoothScrolling) {
                double cur = this.scrollAmount();
                if (Math.abs(cur - targetScroll) < 0.5) {
                    this.setScrollAmount(targetScroll);
                    isSmoothScrolling = false;
                } else {
                    this.setScrollAmount(Mth.lerp(0.5f * delta, cur, targetScroll));
                }
            } else {
                targetScroll = this.scrollAmount();
            }
        }
    }

    // ========== Option Entry Types ==========

    private static class SpacerEntry extends OptionEntry {
        public SpacerEntry() {} // 列表条目高度固定,占位间距无需高度参数
        @Override protected boolean shouldHighlight() { return false; }
        @Override public void renderContent(GuiGraphicsExtractor g, int idx, int y, int x, int ew, int eh, int mx, int my, boolean hovered, float dt) {}
    }

    // 只读提示行
    private static class InfoEntry extends OptionEntry {
        private final Component text;
        public InfoEntry(String key) {
            this.text = Component.translatable(key);
        }
        @Override protected boolean shouldHighlight() { return false; }
        @Override public void renderContent(GuiGraphicsExtractor g, int idx, int y, int x, int ew, int eh, int mx, int my, boolean hovered, float dt) {
            g.text(Minecraft.getInstance().font, text, x, y + 8, 0xFFFFFFFF);
        }
    }

    private static class BooleanOptionEntry extends OptionEntry {
        private final StyledButton button;
        private final Component label;
        private boolean state;
        public BooleanOptionEntry(String key, boolean initial, Consumer<Boolean> onToggle) {
            this.state = initial;
            this.label = Component.translatable(key);
            final StyledButton[] ref = new StyledButton[1];
            ref[0] = new StyledButton(0, 0, 100, 20, Component.translatable(state ? "options.on" : "options.off").withColor(state ? 0xFFB5F0C6 : 0xFFFC887E), () -> {
                state = !state;
                ref[0].setMessage(Component.translatable(state ? "options.on" : "options.off").withColor(state ? 0xFFB5F0C6 : 0xFFFC887E));
                onToggle.accept(state);
            });
            this.button = ref[0];
        }
        @Override
        public void renderContent(GuiGraphicsExtractor g, int idx, int y, int x, int ew, int eh, int mx, int my, boolean hovered, float dt) {
            g.text(Minecraft.getInstance().font, label, x, y + 8, 0xFFFFFFFF);
            button.setX(x + ew - 110); button.setY(y + 2); button.setFocused(false);
            button.extractRenderState(g, mx, my, dt);
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
        @Override public void renderContent(GuiGraphicsExtractor g, int idx, int y, int x, int ew, int eh, int mx, int my, boolean hovered, float dt) {
            g.text(Minecraft.getInstance().font, label, x, y + 8, 0xFFFFFFFF);
            slider.setX(x + ew - 110); slider.setY(y + 2);
            slider.extractRenderState(g, mx, my, dt);
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
            this.field = new EditBox(Minecraft.getInstance().font, 0, 0, 100, 20, Component.empty());
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
        @Override public String validate() {
            if (field.getValue() == null || field.getValue().trim().isEmpty()) {
                return label.getString();
            }
            try {
                Float.parseFloat(field.getValue().trim());
                return null;
            } catch (NumberFormatException e) {
                return label.getString();
            }
        }
        @Override public void renderContent(GuiGraphicsExtractor g, int idx, int y, int x, int ew, int eh, int mx, int my, boolean hovered, float dt) {
            g.text(Minecraft.getInstance().font, label, x, y + 8, 0xFFFFFFFF);
            int bx = x + ew - 110, by = y + 2, bw = 100, bh = 20;
            field.setX(bx + 4); field.setY(by + 6); field.setWidth(bw - 8); field.setHeight(12);
            g.fill(bx, by, bx + bw, by + bh, 0x20000000);
            int bc = (field.isFocused() || field.isMouseOver(mx, my)) ? 0xFFFFFFFF : 0xFFA0A0A0;
            g.fill(bx, by, bx + bw, by + 1, bc); g.fill(bx, by + bh - 1, bx + bw, by + bh, bc);
            g.fill(bx, by, bx + 1, by + bh, bc); g.fill(bx + bw - 1, by, bx + bw, by + bh, bc);
            field.extractRenderState(g, mx, my, dt);
        }
        public List<? extends GuiEventListener> children() { return Collections.singletonList(field); }
        public List<? extends NarratableEntry> narratables() { return Collections.singletonList(field); }
    }

    private static class TextEntry extends OptionEntry {
        private final EditBox field;
        private final Component label;
        public TextEntry(String key, String initial, Consumer<String> onChange) {
            this.label = Component.translatable(key);
            this.field = new EditBox(Minecraft.getInstance().font, 0, 0, 100, 20, Component.empty());
            this.field.setBordered(false);
            this.field.setMaxLength(1000); // 无字数上限
            this.field.setValue(initial);
            this.field.setResponder(onChange);
        }
        @Override public String validate() {
            if (field.getValue() == null || field.getValue().trim().isEmpty()) {
                return label.getString();
            }
            return null;
        }
        @Override public void renderContent(GuiGraphicsExtractor g, int idx, int y, int x, int ew, int eh, int mx, int my, boolean hovered, float dt) {
            g.text(Minecraft.getInstance().font, label, x, y + 8, 0xFFFFFFFF);
            int bx = x + ew - 110, by = y + 2, bw = 100, bh = 20;
            field.setX(bx + 4); field.setY(by + 6); field.setWidth(bw - 8); field.setHeight(12);
            g.fill(bx, by, bx + bw, by + bh, 0x20000000);
            int bc = (field.isFocused() || field.isMouseOver(mx, my)) ? 0xFFFFFFFF : 0xFFA0A0A0;
            g.fill(bx, by, bx + bw, by + 1, bc); g.fill(bx, by + bh - 1, bx + bw, by + bh, bc);
            g.fill(bx, by, bx + 1, by + bh, bc); g.fill(bx + bw - 1, by, bx + bw, by + bh, bc);
            field.extractRenderState(g, mx, my, dt);
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
        // 布尔模式选择(屏蔽/显示)
        public ModeSelectorEntry(String key, boolean initial, Consumer<Boolean> onChange) {
            this.label = Component.translatable(key);
            this.mode = initial ? "block" : "show";
            final StyledButton[] ref = new StyledButton[1];
            ref[0] = new StyledButton(0, 0, 100, 20,
                Component.translatable("option.damage-engine.mode." + mode)
                    .withColor(initial ? 0xFFFC887E : 0xFFB5F0C6), () -> {
                boolean v = "show".equals(mode);
                mode = v ? "block" : "show";
                ref[0].setMessage(Component.translatable("option.damage-engine.mode." + mode)
                    .withColor(v ? 0xFFFC887E : 0xFFB5F0C6));
                onChange.accept(v);
            });
            this.button = ref[0];
        }
        @Override public void renderContent(GuiGraphicsExtractor g, int idx, int y, int x, int ew, int eh, int mx, int my, boolean hovered, float dt) {
            g.text(Minecraft.getInstance().font, label, x, y + 8, 0xFFFFFFFF);
            button.setX(x + ew - 110); button.setY(y + 2); button.setFocused(false);
            button.extractRenderState(g, mx, my, dt);
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
        @Override public void renderContent(GuiGraphicsExtractor g, int idx, int y, int x, int ew, int eh, int mx, int my, boolean hovered, float dt) {
            g.text(Minecraft.getInstance().font, label, x, y + 8, 0xFFFFFFFF);
            button.setX(x + ew - 110); button.setY(y + 2); button.setFocused(false);
            button.extractRenderState(g, mx, my, dt);
        }
        public List<? extends GuiEventListener> children() { return Collections.singletonList(button); }
        public List<? extends NarratableEntry> narratables() { return Collections.singletonList(button); }
    }

    private static class HexColorEntry extends OptionEntry {
        private final EditBox field;
        private final Component label;
        private final Consumer<Integer> onChange;
        private int currentColor;
        private int swatchX, swatchY, swatchSize;
        public HexColorEntry(String key, int initial, Consumer<Integer> onChange) {
            this.label = Component.translatable(key);
            this.onChange = onChange;
            this.currentColor = initial;
            this.field = new EditBox(Minecraft.getInstance().font, 0, 0, 75, 20, Component.empty());
            this.field.setBordered(false); this.field.setMaxLength(7);
            this.field.setValue(String.format("%06X", initial & 0xFFFFFF));
            this.field.setResponder(s -> {
                String filtered = s.replaceAll("[^0-9a-fA-F#]", "");
                if (filtered.indexOf('#') > 0) filtered = filtered.replace("#", ""); // # 仅允许位于开头
                if (!filtered.equals(s)) { this.field.setValue(filtered); return; }
                try { String hex = s.startsWith("#") ? s.substring(1) : s;
                    // 仅完整 6 位十六进制才应用,避免 "8"/"10" 等短输入被存成近似黑色(000008 等)
                    if (hex.length() == 6) {
                        currentColor = (int)Long.parseLong(hex, 16) | 0xFF000000;
                        onChange.accept(currentColor);
                    } } catch(Exception ignored){}
            });
        }
        @Override public void renderContent(GuiGraphicsExtractor g, int idx, int y, int x, int ew, int eh, int mx, int my, boolean hovered, float dt) {
            // 未聚焦且输入不是完整 6 位色值时,回填为实际颜色(避免残留 "02"/"25" 等短输入)
            if (!field.isFocused() && (field.getValue() == null || !field.getValue().matches("#?[0-9a-fA-F]{6}"))) {
                String hex = String.format("%06X", currentColor & 0xFFFFFF);
                if (!hex.equals(field.getValue())) field.setValue(hex);
            }
            g.text(Minecraft.getInstance().font, label, x, y + 8, 0xFFFFFFFF);
            int sx = x + ew - 110, bx = sx, by = y + 2, bw = 75, bh = 20;
            field.setX(bx + 4); field.setY(by + 6); field.setWidth(bw - 8); field.setHeight(12);
            g.fill(bx, by, bx + bw, by + bh, 0x20000000);
            int bc = (field.isFocused() || field.isMouseOver(mx, my)) ? 0xFFFFFFFF : 0xFFA0A0A0;
            g.fill(bx, by, bx + bw, by + 1, bc); g.fill(bx, by + bh - 1, bx + bw, by + bh, bc);
            g.fill(bx, by, bx + 1, by + bh, bc); g.fill(bx + bw - 1, by, bx + bw, by + bh, bc);
            field.extractRenderState(g, mx, my, dt);
            int ps = 17, px = sx + 80, py = by + 1;
            swatchX = px - 1; swatchY = py - 1; swatchSize = ps + 2;
            boolean over = mx >= swatchX && mx <= swatchX + swatchSize && my >= swatchY && my <= swatchY + swatchSize;
            if (over) g.requestCursor(DamageConfigScreen.CURSOR_HAND);
            g.fill(swatchX, swatchY, swatchX + swatchSize, swatchY + swatchSize, over ? 0xFFB5F0C6 : 0xFFFFFFFF);
            g.fill(px, py, px + ps, py + ps, 0xFF000000 | (currentColor & 0xFFFFFF));
        }
        @Override public String validate() {
            if (field.getValue() == null || field.getValue().trim().isEmpty()) {
                return label.getString();
            }
            try {
                String hex = field.getValue().trim().startsWith("#") ? field.getValue().trim().substring(1) : field.getValue().trim();
                if (hex.length() > 6) return label.getString();
                Long.parseLong(hex, 16);
                return null;
            } catch (NumberFormatException e) {
                return label.getString();
            }
        }
        @Override public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
            if (event.button() == 0 && swatchSize > 0
                && event.x() >= swatchX && event.x() <= swatchX + swatchSize
                && event.y() >= swatchY && event.y() <= swatchY + swatchSize) {
                if (Minecraft.getInstance().screen instanceof DamageConfigScreen s) {
                    s.playClickSound();
                    s.openColorPicker(swatchX + swatchSize / 2, swatchY, currentColor, c -> {
                        currentColor = c | 0xFF000000;
                        field.setValue(String.format("%06X", currentColor & 0xFFFFFF));
                        onChange.accept(currentColor);
                    });
                }
                return true;
            }
            return super.mouseClicked(event, bl);
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
        @Override public void renderContent(GuiGraphicsExtractor g, int idx, int y, int x, int ew, int eh, int mx, int my, boolean hovered, float dt) {
            g.text(Minecraft.getInstance().font, label, x, y + 8, 0xFFFFFFFF);
            button.setX(x + ew - 110); button.setY(y + 2); button.setFocused(false);
            button.extractRenderState(g, mx, my, dt);
        }
        public List<? extends GuiEventListener> children() { return Collections.singletonList(button); }
        public List<? extends NarratableEntry> narratables() { return Collections.singletonList(button); }
    }

    private class ExpandableHeaderEntry extends OptionEntry {
        private final PlainTextButton button;
        private final Component label;
        private final String expandKey;
        public ExpandableHeaderEntry(String key, String expandKey, Consumer<Boolean> onToggle) {
            this(key, expandKey, onToggle, null);
        }
        public ExpandableHeaderEntry(String key, String expandKey, Consumer<Boolean> onToggle, Component tooltip) {
            this.label = Component.translatable(key);
            this.expandKey = expandKey;
            boolean expanded = isExpanded(expandKey);
            this.button = new PlainTextButton(0, 0, 20, 20, Component.literal(expanded ? "-" : "+"), () -> {
                expandStates.put(expandKey, !isExpanded(expandKey));
                onToggle.accept(isExpanded(expandKey));
            }, 0xFFFFFFFF, 0xFFFBFB54);
            if (tooltip != null) {
                this.button.setTooltip(Tooltip.create(tooltip));
            }
        }
        @Override public void renderContent(GuiGraphicsExtractor g, int idx, int y, int x, int ew, int eh, int mx, int my, boolean hovered, float dt) {
            boolean isHov = hovered || (mx >= x && mx <= x + ew && my >= y && my <= y + eh);
            int color = isHov ? 0xFFFBFB54 : 0xFFFFFFFF;
            g.text(Minecraft.getInstance().font, label, x, y + 8, color);
            button.setX(x + ew - 25); button.setY(y + 2); button.setFocused(false); button.setForceHover(isHov);
            button.extractRenderState(g, mx, my, dt);
        }
        @Override public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
            if (this.button.mouseClicked(event, bl)) return true;
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
            int color = 0xFFFC887E;
            Component btnText = Component.translatable("gui.reset");
            if (resetButtonState == 1) btnText = Component.translatable("gui.confirm").append("?");
            else if (resetButtonState == 2) { color = 0xFFB5F0C6; btnText = Component.translatable("text.damage-engine.reset_done"); }
            this.button = new StyledButton(0, 0, 100, 20, btnText.copy().withColor(color), () -> handleClick(onReset));
        }
        private void handleClick(Runnable onReset) {
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
        @Override public void renderContent(GuiGraphicsExtractor g, int idx, int y, int x, int ew, int eh, int mx, int my, boolean hovered, float dt) {
            if (resetButtonState == 2 && System.currentTimeMillis() - resetButtonActionTime > 3000) {
                resetButtonState = 0;
                button.setMessage(Component.translatable("gui.reset").withColor(0xFFFC887E));
            } else if (resetButtonState == 1 && System.currentTimeMillis() - resetButtonActionTime > 5000) {
                resetButtonState = 0;
                button.setMessage(Component.translatable("gui.reset").withColor(0xFFFC887E));
            }
            g.text(Minecraft.getInstance().font, label, x, y + 8, 0xFFFC887E);
            button.setX(x + ew - 110); button.setY(y + 2); button.setFocused(false);
            button.extractRenderState(g, mx, my, dt);
        }
        public List<? extends GuiEventListener> children() { return Collections.singletonList(button); }
        public List<? extends NarratableEntry> narratables() { return Collections.singletonList(button); }
    }

    private static class DamageThresholdEntry extends OptionEntry {
        private final EditBox valField, colField;
        private final PlainTextButton delBtn;
        private final DamageEngineConfig.DamageThreshold dt;
        private int currentColor;
        private int swatchX, swatchY, swatchSize;
        public DamageThresholdEntry(DamageEngineConfig.DamageThreshold dt, Runnable onDelete) {
            this.dt = dt;
            this.currentColor = dt.color;
            this.valField = new EditBox(Minecraft.getInstance().font, 0, 0, 40, 20, Component.empty());
            this.valField.setBordered(false); this.valField.setValue(String.format("%.0f", dt.threshold));
            this.valField.setResponder(s -> { try { dt.threshold = Float.parseFloat(s); } catch(Exception ignored){} });
            this.colField = new EditBox(Minecraft.getInstance().font, 0, 0, 55, 20, Component.empty());
            this.colField.setBordered(false); this.colField.setMaxLength(7);
            this.colField.setValue(String.format("%06X", dt.color & 0xFFFFFF));
            this.colField.setResponder(s -> { String filtered = s.replaceAll("[^0-9a-fA-F#]", "");
                if (filtered.indexOf('#') > 0) filtered = filtered.replace("#", ""); // # 仅允许位于开头
                if (!filtered.equals(s)) { this.colField.setValue(filtered); return; }
                try { String hex = s.startsWith("#") ? s.substring(1) : s;
                // 仅完整 6 位十六进制才应用,避免短输入被存成近似黑色
                if (hex.length() == 6) {
                    currentColor = (int)Long.parseLong(hex, 16) | 0xFF000000; dt.color = currentColor;
                } } catch(Exception ignored){} });
            this.delBtn = new PlainTextButton(0, 0, 20, 20, Component.literal("-"), onDelete, 0xFFFFFFFF, 0xFFFBFB54);
        }
        @Override public void renderContent(GuiGraphicsExtractor g, int idx, int y, int x, int ew, int eh, int mx, int my, boolean hovered, float dt) {
            // 未聚焦且颜色输入不是完整 6 位色值时,回填为实际颜色(避免残留短输入)
            if (!colField.isFocused() && (colField.getValue() == null || !colField.getValue().matches("#?[0-9a-fA-F]{6}"))) {
                String hex = String.format("%06X", currentColor & 0xFFFFFF);
                if (!hex.equals(colField.getValue())) colField.setValue(hex);
            }
            int rx = x + ew;
            delBtn.setX(rx - 25); delBtn.setY(y + 2); delBtn.setWidth(20); delBtn.setForceHover(hovered);
            int ps = 18, px = rx - 25 - 5 - ps, py = y + 3;
            swatchX = px - 1; swatchY = py - 1; swatchSize = ps + 2;
            boolean overSwatch = mx >= swatchX && mx <= swatchX + swatchSize && my >= swatchY && my <= swatchY + swatchSize;
            if (overSwatch) g.requestCursor(DamageConfigScreen.CURSOR_HAND);
            int cbw = 55, cbx = px - 5 - cbw, cby = y + 2, cbh = 20;
            colField.setX(cbx + 4); colField.setY(cby + 6); colField.setWidth(cbw - 8); colField.setHeight(12);
            int vbw = 40, vbx = cbx - 5 - vbw, vby = y + 2, vbh = 20;
            valField.setX(vbx + 4); valField.setY(vby + 6); valField.setWidth(vbw - 8); valField.setHeight(12);
            g.text(Minecraft.getInstance().font, Component.translatable("text.damage-engine.damage_reach"), x, y + 8, 0xFFFFFFFF);
            drawTextBox(g, vbx, vby, vbw, vbh, valField, mx, my);
            drawTextBox(g, cbx, cby, cbw, cbh, colField, mx, my);
            valField.extractRenderState(g, mx, my, dt); colField.extractRenderState(g, mx, my, dt);
            delBtn.extractRenderState(g, mx, my, dt);
            g.fill(swatchX, swatchY, swatchX + swatchSize, swatchY + swatchSize, overSwatch ? 0xFFB5F0C6 : 0xFFFFFFFF);
            g.fill(px, py, px + ps, py + ps, 0xFF000000 | (currentColor & 0xFFFFFF));
        }
        @Override public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
            if (event.button() == 0 && swatchSize > 0
                && event.x() >= swatchX && event.x() <= swatchX + swatchSize
                && event.y() >= swatchY && event.y() <= swatchY + swatchSize) {
                if (Minecraft.getInstance().screen instanceof DamageConfigScreen s) {
                    s.playClickSound();
                    s.openColorPicker(swatchX + swatchSize / 2, swatchY, currentColor, c -> {
                        currentColor = c | 0xFF000000;
                        dt.color = currentColor;
                        colField.setValue(String.format("%06X", currentColor & 0xFFFFFF));
                    });
                }
                return true;
            }
            return super.mouseClicked(event, bl);
        }
        @Override public String validate() {
            if (valField.getValue() == null || valField.getValue().trim().isEmpty()
                || colField.getValue() == null || colField.getValue().trim().isEmpty()) {
                return Component.translatable("text.damage-engine.damage_reach").getString();
            }
            try {
                Float.parseFloat(valField.getValue().trim());
                String hex = colField.getValue().trim().startsWith("#") ? colField.getValue().trim().substring(1) : colField.getValue().trim();
                if (hex.length() > 6) return Component.translatable("text.damage-engine.damage_reach").getString();
                Long.parseLong(hex, 16);
                return null;
            } catch (NumberFormatException e) {
                return Component.translatable("text.damage-engine.damage_reach").getString();
            }
        }
        private void drawTextBox(GuiGraphicsExtractor g, int bx, int by, int bw, int bh, EditBox f, int mx, int my) {
            g.fill(bx, by, bx + bw, by + bh, 0x20000000);
            int bc = (f.isFocused() || f.isMouseOver(mx, my)) ? 0xFFFFFFFF : 0xFFA0A0A0;
            g.fill(bx, by, bx + bw, by + 1, bc); g.fill(bx, by + bh - 1, bx + bw, by + bh, bc);
            g.fill(bx, by, bx + 1, by + bh, bc); g.fill(bx + bw - 1, by, bx + bw, by + bh, bc);
        }
        public List<? extends GuiEventListener> children() { return Arrays.asList(valField, colField, delBtn); }
        public List<? extends NarratableEntry> narratables() { return Arrays.asList(valField, colField, delBtn); }
    }

    private static class DamageBonusEntry extends OptionEntry {
        private final EditBox sizeField, pointsField;
        private final PlainTextButton delBtn;
        public DamageBonusEntry(DamageEngineConfig.DamageBonus b, Runnable onDelete) {
            this.sizeField = new EditBox(Minecraft.getInstance().font, 0, 0, 60, 20, Component.empty());
            this.sizeField.setBordered(false); this.sizeField.setMaxLength(10);
            this.sizeField.setValue(formatValue(b.damageSize));
            this.sizeField.setResponder(s -> { String filtered = s.replaceAll("[^0-9.]", "");
                if (!filtered.equals(s)) { this.sizeField.setValue(filtered); return; }
                try { b.damageSize = Float.parseFloat(s); } catch(Exception ignored){} });
            this.pointsField = new EditBox(Minecraft.getInstance().font, 0, 0, 60, 20, Component.empty());
            this.pointsField.setBordered(false); this.pointsField.setMaxLength(10);
            this.pointsField.setValue(formatValue(b.points));
            this.pointsField.setResponder(s -> { String filtered = s.replaceAll("[^0-9.]", "");
                if (!filtered.equals(s)) { this.pointsField.setValue(filtered); return; }
                try { b.points = Float.parseFloat(s); } catch(Exception ignored){} });
            this.delBtn = new PlainTextButton(0, 0, 20, 20, Component.literal("-"), onDelete, 0xFFFFFFFF, 0xFFFBFB54);
        }
        private static String formatValue(float v) { if (v == (int)v) return String.valueOf((int)v); return String.format("%.1f", v); }
        @Override public void renderContent(GuiGraphicsExtractor g, int idx, int y, int x, int ew, int eh, int mx, int my, boolean hovered, float dt) {
            int rx = x + ew;
            delBtn.setX(rx - 25); delBtn.setY(y + 2); delBtn.setWidth(20); delBtn.setForceHover(hovered);
            int bw = 60, bh = 20, by = y + (eh - bh) / 2;
            int sizeLblW = Minecraft.getInstance().font.width(Component.translatable("text.damage-engine.damage_size"));
            int ptsLblW = Minecraft.getInstance().font.width(Component.translatable("text.damage-engine.bonus_points"));
            int sx = x;
            g.text(Minecraft.getInstance().font, Component.translatable("text.damage-engine.damage_size"), sx, y + 8, 0xFFFFFFFF);
            int sxb = sx + sizeLblW + 4;
            sizeField.setX(sxb + 4); sizeField.setY(by + 6); sizeField.setWidth(bw - 8); sizeField.setHeight(12);
            int pxl = sxb + bw + 12;
            g.text(Minecraft.getInstance().font, Component.translatable("text.damage-engine.bonus_points"), pxl, y + 8, 0xFFFFFFFF);
            int pxb = pxl + ptsLblW + 4;
            pointsField.setX(pxb + 4); pointsField.setY(by + 6); pointsField.setWidth(bw - 8); pointsField.setHeight(12);
            drawBox(g, sxb, by, bw, bh, sizeField, mx, my);
            drawBox(g, pxb, by, bw, bh, pointsField, mx, my);
            sizeField.extractRenderState(g, mx, my, dt); pointsField.extractRenderState(g, mx, my, dt); delBtn.extractRenderState(g, mx, my, dt);
        }
        @Override public String validate() {
            String name = Component.translatable("option.damage-engine.damage_size_bonus").getString();
            if (sizeField.getValue() == null || sizeField.getValue().trim().isEmpty()
                || pointsField.getValue() == null || pointsField.getValue().trim().isEmpty()) {
                return name;
            }
            try {
                Float.parseFloat(sizeField.getValue().trim());
                Float.parseFloat(pointsField.getValue().trim());
                return null;
            } catch (NumberFormatException e) {
                return name;
            }
        }
        private void drawBox(GuiGraphicsExtractor g, int bx, int by, int bw, int bh, EditBox f, int mx, int my) {
            g.fill(bx, by, bx + bw, by + bh, 0x20000000);
            int bc = (f.isFocused() || f.isMouseOver(mx, my)) ? 0xFFFFFFFF : 0xFFA0A0A0;
            g.fill(bx, by, bx + bw, by + 1, bc); g.fill(bx, by + bh - 1, bx + bw, by + bh, bc);
            g.fill(bx, by, bx + 1, by + bh, bc); g.fill(bx + bw - 1, by, bx + bw, by + bh, bc);
        }
        public List<? extends GuiEventListener> children() { return Arrays.asList(sizeField, pointsField, delBtn); }
        public List<? extends NarratableEntry> narratables() { return Arrays.asList(sizeField, pointsField, delBtn); }
    }

    private static class GlobalEntityRuleEntry extends OptionEntry {
        private final DamageEngineConfig.GlobalEntityRule rule;
        private final EditBox nameField, distField;
        private final PlainTextButton delBtn;
        public GlobalEntityRuleEntry(DamageEngineConfig.GlobalEntityRule rule, Runnable onDelete) {
            this.rule = rule;
            this.nameField = new EditBox(Minecraft.getInstance().font, 0, 0, 110, 20, Component.empty());
            this.nameField.setBordered(false); this.nameField.setMaxLength(64);
            this.nameField.setTooltip(Tooltip.create(Component.translatable("hint.damage-engine.entity_registry_name")));
            this.nameField.setValue(rule.registryName != null ? rule.registryName : "");
            this.nameField.setResponder(s -> { rule.registryName = s; });
            this.distField = new EditBox(Minecraft.getInstance().font, 0, 0, 45, 20, Component.empty());
            this.distField.setBordered(false); this.distField.setMaxLength(7);
            this.distField.setTooltip(Tooltip.create(Component.translatable("hint.damage-engine.globalIndicatorMaxDistance")));
            this.distField.setValue(String.format("%.0f", rule.distance));
            this.distField.setResponder(s -> { String filtered = s.replaceAll("[^0-9.]", "");
                if (!filtered.equals(s)) { this.distField.setValue(filtered); return; }
                try { rule.distance = Float.parseFloat(s); } catch(Exception ignored){} });
            this.delBtn = new PlainTextButton(0, 0, 20, 20, Component.literal("-"), onDelete, 0xFFFFFFFF, 0xFFFBFB54);
        }
        @Override public void renderContent(GuiGraphicsExtractor g, int idx, int y, int x, int ew, int eh, int mx, int my, boolean hovered, float dt) {
            int rx = x + ew;
            delBtn.setX(rx - 25); delBtn.setY(y + 2); delBtn.setWidth(20); delBtn.setForceHover(hovered);
            int bh = 20, by = y + (eh - bh) / 2;
            int nbw = 110, dbw = 45;
            int nameLblW = Minecraft.getInstance().font.width(Component.translatable("text.damage-engine.registry_name"));
            int distLblW = Minecraft.getInstance().font.width(Component.translatable("text.damage-engine.distance"));
            // 从左到右: 注册名标签 | nameField | 距离标签 | distField
            int nx = x + nameLblW + 4;
            int dlx = nx + nbw + 12;
            int dbx = dlx + distLblW + 4;
            g.text(Minecraft.getInstance().font, Component.translatable("text.damage-engine.registry_name"), x, y + 8, 0xFFFFFFFF);
            nameField.setX(nx + 4); nameField.setY(by + 6); nameField.setWidth(nbw - 8); nameField.setHeight(12);
            g.text(Minecraft.getInstance().font, Component.translatable("text.damage-engine.distance"), dlx, y + 8, 0xFFFFFFFF);
            distField.setX(dbx + 4); distField.setY(by + 6); distField.setWidth(dbw - 8); distField.setHeight(12);
            drawBox(g, nx, by, nbw, bh, nameField, mx, my);
            drawBox(g, dbx, by, dbw, bh, distField, mx, my);
            nameField.extractRenderState(g, mx, my, dt); distField.extractRenderState(g, mx, my, dt); delBtn.extractRenderState(g, mx, my, dt);
        }
        @Override public String validate() {
            String name = Component.translatable("option.damage-engine.non_player_entities").getString();
            if (nameField.getValue() == null || nameField.getValue().trim().isEmpty()) {
                return name;
            }
            if (distField.getValue() == null || distField.getValue().trim().isEmpty()) {
                return name;
            }
            try {
                Float.parseFloat(distField.getValue().trim());
                return null;
            } catch (NumberFormatException e) {
                return name;
            }
        }
        private void drawBox(GuiGraphicsExtractor g, int bx, int by, int bw, int bh, EditBox f, int mx, int my) {
            g.fill(bx, by, bx + bw, by + bh, 0x20000000);
            int bc = (f.isFocused() || f.isMouseOver(mx, my)) ? 0xFFFFFFFF : 0xFFA0A0A0;
            g.fill(bx, by, bx + bw, by + 1, bc); g.fill(bx, by + bh - 1, bx + bw, by + bh, bc);
            g.fill(bx, by, bx + 1, by + bh, bc); g.fill(bx + bw - 1, by, bx + bw, by + bh, bc);
        }
        public List<? extends GuiEventListener> children() { return Arrays.asList(nameField, distField, delBtn); }
        public List<? extends NarratableEntry> narratables() { return Arrays.asList(nameField, distField, delBtn); }
    }

    private static class AddButtonEntry extends OptionEntry {
        private final PlainTextButton button;
        private final Runnable action;
        private boolean disabled = false;
        public AddButtonEntry(Runnable action) {
            this.action = action;
            this.button = new PlainTextButton(0, 0, 20, 20, Component.literal("+"), action, 0xFFFFFFFF, 0xFFFBFB54);
        }
        /** 禁用添加:点击被阻止,悬停显示 tooltip 说明原因 */
        public void setDisabled(Component tooltip) {
            this.disabled = tooltip != null;
            this.button.setTooltip(tooltip == null ? null : Tooltip.create(tooltip));
            this.button.setDefaultColor(disabled ? 0xFF808080 : 0xFFFFFFFF);
        }
        @Override public void renderContent(GuiGraphicsExtractor g, int idx, int y, int x, int ew, int eh, int mx, int my, boolean hovered, float dt) {
            boolean rowHov = mx >= x && mx <= x + ew && my >= y && my <= y + eh;
            button.setX(x + ew - 25); button.setY(y + 2); button.setFocused(false); button.setForceHover(rowHov && !disabled);
            button.extractRenderState(g, mx, my, dt);
        }
        @Override public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
            if (isMouseOver(event.x(), event.y())) {
                if (Minecraft.getInstance().screen instanceof DamageConfigScreen s) s.playClickSound();
                if (!disabled) action.run();
                return true;
            }
            return false;
        }
        public List<? extends GuiEventListener> children() { return Collections.singletonList(button); }
        public List<? extends NarratableEntry> narratables() { return Collections.singletonList(button); }
    }

    private static class RatingGradeEntry extends OptionEntry {
        private final EditBox scoreField, textField;
        private final PlainTextButton delBtn;
        public RatingGradeEntry(DamageEngineConfig.RatingGrade g, Runnable onDelete) {
            this.scoreField = new EditBox(Minecraft.getInstance().font, 0, 0, 60, 20, Component.empty());
            this.scoreField.setBordered(false); this.scoreField.setValue(String.format("%.0f", g.minScore));
            this.scoreField.setResponder(s -> { try { g.minScore = Float.parseFloat(s); } catch(Exception ignored){} });
            this.textField = new EditBox(Minecraft.getInstance().font, 0, 0, 60, 20, Component.empty());
            this.textField.setBordered(false); this.textField.setMaxLength(1000); this.textField.setValue(g.text); // 无字数上限
            this.textField.setResponder(s -> { g.text = s; });
            this.delBtn = new PlainTextButton(0, 0, 20, 20, Component.literal("-"), onDelete, 0xFFFFFFFF, 0xFFFBFB54);
        }
        @Override public void renderContent(GuiGraphicsExtractor g, int idx, int y, int x, int ew, int eh, int mx, int my, boolean hovered, float dt) {
            int rx = x + ew, bw = 60, bh = 20, by = y + (eh - bh) / 2;
            delBtn.setX(rx - 25); delBtn.setY(by); delBtn.setWidth(20); delBtn.setForceHover(hovered);
            int tbx = rx - 25 - 3 - bw;
            textField.setX(tbx + 4); textField.setY(by + 6); textField.setWidth(bw - 8); textField.setHeight(12);
            int sbx = tbx - 3 - bw;
            scoreField.setX(sbx + 4); scoreField.setY(by + 6); scoreField.setWidth(bw - 8); scoreField.setHeight(12);
            g.text(Minecraft.getInstance().font, Component.literal("≥"), x, y + 8, 0xFFFFFFFF);
            drawBox(g, sbx, by, bw, bh, scoreField, mx, my);
            drawBox(g, tbx, by, bw, bh, textField, mx, my);
            scoreField.extractRenderState(g, mx, my, dt); textField.extractRenderState(g, mx, my, dt); delBtn.extractRenderState(g, mx, my, dt);
        }
        @Override public String validate() {
            String gradeName = Component.translatable("option.damage-engine.rating_grades").getString();
            if (scoreField.getValue() == null || scoreField.getValue().trim().isEmpty()
                || textField.getValue() == null || textField.getValue().trim().isEmpty()) {
                return gradeName;
            }
            try {
                Float.parseFloat(scoreField.getValue().trim());
                return null;
            } catch (NumberFormatException e) {
                return gradeName;
            }
        }
        private void drawBox(GuiGraphicsExtractor g, int bx, int by, int bw, int bh, EditBox f, int mx, int my) {
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
        private int swatchX, swatchY, swatchSize;
        public RatingGradeAppearanceEntry(DamageEngineConfig.RatingGrade g, DamageEngineConfig config) {
            this.grade = g; this.config = config; this.currentColor = g.color;
            this.colField = new EditBox(Minecraft.getInstance().font, 0, 0, 75, 20, Component.empty());
            this.colField.setBordered(false); this.colField.setMaxLength(7);
            this.colField.setValue(String.format("%06X", g.color & 0xFFFFFF));
            this.colField.setResponder(s -> { String filtered = s.replaceAll("[^0-9a-fA-F#]", "");
                if (filtered.indexOf('#') > 0) filtered = filtered.replace("#", ""); // # 仅允许位于开头
                if (!filtered.equals(s)) { this.colField.setValue(filtered); return; }
                try { String hex = s.startsWith("#") ? s.substring(1) : s;
                // 仅完整 6 位十六进制才应用,避免短输入被存成近似黑色
                if (hex.length() == 6) {
                    currentColor = (int)Long.parseLong(hex, 16) | 0xFF000000; g.color = currentColor;
                } } catch(Exception ignored){} });
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
        @Override public void renderContent(GuiGraphicsExtractor g, int idx, int y, int x, int ew, int eh, int mx, int my, boolean hovered, float dt) {
            // 未聚焦且颜色输入不是完整 6 位色值时,回填为实际颜色(避免残留短输入)
            if (!config.ratingUseImages && !colField.isFocused() && (colField.getValue() == null || !colField.getValue().matches("#?[0-9a-fA-F]{6}"))) {
                String hex = String.format("%06X", currentColor & 0xFFFFFF);
                if (!hex.equals(colField.getValue())) colField.setValue(hex);
            }
            int rx = x + ew, by = y + 2;
            if (config.ratingUseImages) {
                boolean hasImg = grade.imagePath != null && !grade.imagePath.isEmpty();
                selectImageBtn.setX(rx - 110); selectImageBtn.setY(by); selectImageBtn.setWidth(100); selectImageBtn.visible = true;
                resetImageBtn.visible = hasImg;
                if (hasImg) { resetImageBtn.setX(rx - 110 - 25); resetImageBtn.setY(by); resetImageBtn.setWidth(20); }
                String pt = hasImg ? grade.imagePath : "Not set";
                if (pt.contains("/")) pt = pt.substring(pt.lastIndexOf("/") + 1);
                g.text(Minecraft.getInstance().font, Component.literal(grade.text), x, y + 8, 0xFFFFFFFF);
                int mw = 80, tw = Minecraft.getInstance().font.width(pt);
                if (tw > mw) { while (tw > mw - 20 && pt.length() > 4) { pt = pt.substring(0, pt.length() - 1); tw = Minecraft.getInstance().font.width(pt + "..."); } pt += "..."; }
                int px = rx - 110 - 5 - tw - (hasImg ? 25 : 0) - 5;
                if (px < x + 10) px = x + 10;
                g.text(Minecraft.getInstance().font, Component.literal(pt), px, y + 8, 0xFFFFFFFF);
                resetImageBtn.extractRenderState(g, mx, my, dt); selectImageBtn.extractRenderState(g, mx, my, dt);
            } else {
                selectImageBtn.visible = false;
                int sx = x + ew - 110, ps = 17, px = sx + 80, py = by + 1;
                swatchX = px - 1; swatchY = py - 1; swatchSize = ps + 2;
                boolean overSwatch = mx >= swatchX && mx <= swatchX + swatchSize && my >= swatchY && my <= swatchY + swatchSize;
                if (overSwatch) g.requestCursor(DamageConfigScreen.CURSOR_HAND);
                colField.setX(sx + 4); colField.setY(by + 6); colField.setWidth(75 - 8); colField.setHeight(12); colField.visible = true;
                g.text(Minecraft.getInstance().font, Component.literal(grade.text), x, y + 8, 0xFFFFFFFF);
                drawBox(g, sx, by, 75, 20, colField, mx, my);
                colField.extractRenderState(g, mx, my, dt);
                g.fill(swatchX, swatchY, swatchX + swatchSize, swatchY + swatchSize, overSwatch ? 0xFFB5F0C6 : 0xFFFFFFFF);
                g.fill(px, py, px + ps, py + ps, 0xFF000000 | (currentColor & 0xFFFFFF));
            }
        }
        @Override public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
            if (!config.ratingUseImages && event.button() == 0 && swatchSize > 0
                && event.x() >= swatchX && event.x() <= swatchX + swatchSize
                && event.y() >= swatchY && event.y() <= swatchY + swatchSize) {
                if (Minecraft.getInstance().screen instanceof DamageConfigScreen s) {
                    s.playClickSound();
                    s.openColorPicker(swatchX + swatchSize / 2, swatchY, currentColor, c -> {
                        currentColor = c | 0xFF000000;
                        grade.color = currentColor;
                        colField.setValue(String.format("%06X", currentColor & 0xFFFFFF));
                    });
                }
                return true;
            }
            return super.mouseClicked(event, bl);
        }
        private void drawBox(GuiGraphicsExtractor g, int bx, int by, int bw, int bh, EditBox f, int mx, int my) {
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
            this.hint = hintKey != null ? Component.translatable(hintKey).withColor(0xFFAAAAAA) : null;
            this.valueRef = new float[]{current};
            this.input = new EditBox(Minecraft.getInstance().font, 0, 0, 50, 20, Component.empty());
            this.input.setBordered(false);
            this.input.setValue(formatValue(current));
            this.input.setResponder(s -> { try { float v = Float.parseFloat(s); valueRef[0] = v; onChange.accept(v); } catch(Exception ignored){} });
        }
        private static String formatValue(float v) { if (v == (int)v) return String.valueOf((int)v); return String.format("%.1f", v); }
        @Override public String validate() {
            if (input.getValue() == null || input.getValue().trim().isEmpty()) {
                return label.getString();
            }
            try {
                Float.parseFloat(input.getValue().trim());
                return null;
            } catch (NumberFormatException e) {
                return label.getString();
            }
        }
        @Override public void renderContent(GuiGraphicsExtractor g, int idx, int y, int x, int ew, int eh, int mx, int my, boolean hovered, float dt) {
            if (hint != null) { g.text(Minecraft.getInstance().font, label, x, y + 4, 0xFFFFFFFF); g.text(Minecraft.getInstance().font, hint, x, y + 14, 0xFFA0A0A0); }
            else g.text(Minecraft.getInstance().font, label, x, y + 8, 0xFFFFFFFF);
            int bw = 100, bx = x + ew - 110, by = y + 2, bh = 20;
            g.fill(bx, by, bx + bw, by + bh, 0x20000000);
            int bc = (input.isFocused() || input.isMouseOver(mx, my)) ? 0xFFFFFFFF : 0xFFA0A0A0;
            g.fill(bx, by, bx + bw, by + 1, bc); g.fill(bx, by + bh - 1, bx + bw, by + bh, bc);
            g.fill(bx, by, bx + 1, by + bh, bc); g.fill(bx + bw - 1, by, bx + bw, by + bh, bc);
            input.setX(bx + 4); input.setY(by + 6); input.setWidth(bw - 8); input.setHeight(12);
            input.extractRenderState(g, mx, my, dt);
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
            @Override public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
                if (this.active && this.visible && event.button() == 0 && this.isMouseOver(event.x(), event.y())) {
                    this.playDownSound(Minecraft.getInstance().getSoundManager()); this.onPress.run(); return true;
                }
                return false;
            }
            @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float d) {
                if (isHovered() || isBinding()) g.requestCursor(CURSOR_HAND);
                int bc = isHovered() || isBinding() ? 0xFFFFFFFF : 0xFFA0A0A0;
                int x = getX(), y = getY(), w = getWidth(), h = getHeight();
                g.fill(x, y, x + w, y + 1, bc); g.fill(x, y + h - 1, x + w, y + h, bc);
                g.fill(x, y, x + 1, y + h, bc); g.fill(x + w - 1, y, x + w, y + h, bc);
                g.centeredText(Minecraft.getInstance().font, getMessage(), x + w / 2, y + (h - 8) / 2, 0xFFFFFFFF);
            }
            @Override protected void updateWidgetNarration(NarrationElementOutput b) { this.defaultButtonNarrationText(b); }
            @Override public boolean keyPressed(KeyEvent event) {
                if (isBinding()) {
                    if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) keyBinding.setKey(com.mojang.blaze3d.platform.InputConstants.UNKNOWN);
                    else keyBinding.setKey(com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(event.key()));
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
        private void setBinding(boolean v) {
            DamageConfigScreen s = Minecraft.getInstance().screen instanceof DamageConfigScreen ss ? ss : null;
            if (v) {
                this.binding = true;
                if (s != null) s.setBindingEntry(this); // 开始绑定:独占并自动取消其他条目的绑定
            } else {
                this.binding = false;
                if (s != null && s.bindingEntry == this) s.bindingEntry = null;
            }
            updateMessage();
        }
        public boolean isBinding() { return binding; }
        public BindingButton getButton() { return button; }
        private void finishBinding() { setBinding(false); Minecraft.getInstance().options.save(); KeyMapping.resetMapping(); }
        public void bindMouse(int btn) { keyBinding.setKey(com.mojang.blaze3d.platform.InputConstants.Type.MOUSE.getOrCreate(btn)); finishBinding(); }
        private void updateMessage() {
            Component text;
            List<KeyMapping> foundConflicts = new ArrayList<>();
            if (binding) {
                text = Component.literal("> ").withColor(0xFFFFFF55)
                    .append((keyBinding.isUnbound()
                        ? Component.translatable("key.damage_engine.not_bound")
                        : keyBinding.getTranslatedKeyMessage().copy())
                        .withStyle(net.minecraft.ChatFormatting.UNDERLINE).withColor(0xFFFFFFFF))
                    .append(Component.literal(" <").withColor(0xFFFFFF55));
            } else {
                if (keyBinding.isUnbound()) text = Component.translatable("key.damage_engine.not_bound").withColor(0xFFFFFFFF);
                else {
                    for (KeyMapping k : Minecraft.getInstance().options.keyMappings) if (k != keyBinding && k.same(keyBinding)) foundConflicts.add(k);
                    text = foundConflicts.isEmpty() ? keyBinding.getTranslatedKeyMessage().copy() :
                        Component.literal("[ ").withColor(0xFFFFFF55).append(keyBinding.getTranslatedKeyMessage().copy().withColor(0xFFFFFFFF)).append(Component.literal(" ]").withColor(0xFFFFFF55));
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
        @Override public void renderContent(GuiGraphicsExtractor g, int idx, int y, int x, int ew, int eh, int mx, int my, boolean hovered, float dt) {
            if (!binding) updateMessage();
            g.text(Minecraft.getInstance().font, label, x, y + 8, 0xFFFFFFFF);
            button.setX(x + ew - 110); button.setY(y + 2); button.setWidth(100); button.setFocused(false);
            button.extractRenderState(g, mx, my, dt);
        }
        public List<? extends GuiEventListener> children() { return Collections.singletonList(button); }
        public List<? extends NarratableEntry> narratables() { return Collections.singletonList(button); }
    }

    // Slider widget
    private static class StyledSliderWidget extends AbstractSliderButton {
        private final boolean showValue, soundOnPress, soundOnRelease;
        private boolean pendingReleaseSound;
        private boolean dragging = false;
        private double valueBeforeInteraction;
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
        @Override public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
            boolean h = super.mouseClicked(event, bl);
            this.dragging = h;
            if (h && soundOnRelease) { pendingReleaseSound = true; valueBeforeInteraction = this.value; }
            return h;
        }
        @Override public boolean mouseReleased(MouseButtonEvent event) {
            boolean h = super.mouseReleased(event);
            this.dragging = false;
            if (soundOnRelease && pendingReleaseSound) { pendingReleaseSound = false; if (Math.abs(this.value - valueBeforeInteraction) > 1.0E-9) playClick(); }
            return h;
        }
        @Override public void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float d) {
            // While dragging show the horizontal resize cursor; otherwise the hand cursor on hover
            if (dragging) g.requestCursor(CURSOR_EW);
            else if (isHovered()) g.requestCursor(CURSOR_HAND);
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
            if (showValue) g.centeredText(Minecraft.getInstance().font, getMessage(), x + w / 2, y + (h - 8) / 2, 0xFFFFFFFF);
        }
    }
}