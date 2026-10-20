package damage.engine.client.gui;

import anima.api.AnimaApi;
import damage.engine.ClientKeybindings;
import damage.engine.DamageEngineClient;
import damage.engine.DamageEngineConfig;
import com.google.gson.GsonBuilder;
import damage.engine.client.gui.config.*;
import damage.engine.client.gui.config.PlainTextButton;
import damage.engine.hud.DamageHud;
import damage.engine.hud.indicator.DamageIndicator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

import java.util.*;
import java.util.function.Consumer;

public class DamageConfigScreen extends Screen {
    // 光标样式：标准光标取 vanilla CursorTypes；对角缩放没有 vanilla 对应，用 GLFW 标准箭头。
    public static final com.mojang.blaze3d.platform.cursor.CursorType CURSOR_HAND =
        com.mojang.blaze3d.platform.cursor.CursorTypes.POINTING_HAND;
    public static final com.mojang.blaze3d.platform.cursor.CursorType CURSOR_EW =
        com.mojang.blaze3d.platform.cursor.CursorTypes.RESIZE_EW;
    public static final com.mojang.blaze3d.platform.cursor.CursorType CURSOR_NS =
        com.mojang.blaze3d.platform.cursor.CursorTypes.RESIZE_NS;
    public static final com.mojang.blaze3d.platform.cursor.CursorType CURSOR_MOVE =
        com.mojang.blaze3d.platform.cursor.CursorTypes.RESIZE_ALL;
    public static final com.mojang.blaze3d.platform.cursor.CursorType CURSOR_NWSE = createCursor(0x00036007, "nwseresize");

    private static com.mojang.blaze3d.platform.cursor.CursorType createCursor(int handle, String name) {
        try {
            return com.mojang.blaze3d.platform.cursor.CursorType.createStandardCursor(
                handle, name, com.mojang.blaze3d.platform.cursor.CursorType.DEFAULT);
        } catch (Exception e) {
            return com.mojang.blaze3d.platform.cursor.CursorType.DEFAULT;
        }
    }

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
    private static final int MIN_TAB_WIDTH = 62;
    private static final int TOP_BAR_HEIGHT = 22;
    private static final int SEARCH_HEIGHT = 13;

    private int selectedTab = 0;
    private OptionListWidget optionList;
    private EditBox searchBox;
    private String searchQuery = "";
    private double tabScroll = 0;
    private double tabScrollTarget = 0;
    private int tabsLeft = 0;
    private double selectedVisual = 0;
    private long lastFrameMs = 0;
    private final List<OptionEntry> allEntries = new ArrayList<>();
    private double lastScroll = 0;
    private boolean hasUnsavedChanges = false;
    
    // Expand state trackers
    private final Map<String, Boolean> expandStates = new HashMap<>();
    
    private int resetButtonState = 0;
    private long resetButtonActionTime = 0;
    private boolean isBinding = false;
    private ColorPickerPopup colorPicker = null;
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
            
            int footerY = this.height - 35;
            int topY = TOP_BAR_HEIGHT + 4;
            int listHeight = footerY - topY;
            
            optionList = new OptionListWidget(this.minecraft, this.width, listHeight, topY, 26);
            this.addWidget(optionList);

            // 顶部左侧搜索框（无边框嵌入样式）
            int searchWidth = Math.min(120, Math.max(80, this.width / 7));
            searchBox = new EditBox(this.font, 6, 8, searchWidth, SEARCH_HEIGHT,
                Component.translatable("text.damage-engine.search"));
            searchBox.setBordered(false);
            searchBox.setMaxLength(50);
            searchBox.setHint(Component.translatable("text.damage-engine.search").withColor(0xFF808080));
            searchBox.setValue(searchQuery);
            searchBox.setResponder(text -> {
                searchQuery = text == null ? "" : text.trim();
                refreshOptions();
            });
            this.addRenderableWidget(searchBox);
            tabsLeft = 6 + searchWidth + 4;

            int tabsRight = this.width - 6;
            double maxTabScroll = Math.max(0, getTotalTabWidth() - (tabsRight - tabsLeft));
            tabScroll = Mth.clamp(tabScroll, 0, maxTabScroll);
            tabScrollTarget = Mth.clamp(tabScrollTarget, 0, maxTabScroll);
            initOptionEntries();
            
            if (optionList != null && lastScroll > 0) optionList.setScrollAmount(lastScroll);
            
            // Footer buttons
            int buttonWidth = 80;
            int buttonY = footerY + 5;
            
            // Save & Close (green)
            this.addRenderableWidget(new StyledButton(this.width - buttonWidth - 10, buttonY, buttonWidth, 20,
                Component.translatable("button.damage-engine.save_close").withColor(0xFFB7F3C8), () -> {
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
        allEntries.clear();

        String query = searchQuery.toLowerCase(Locale.ROOT);
        if (!query.isEmpty()) {
            // 全局搜索：遍历所有分类，命中的分类下面列出匹配项
            for (int tab = 0; tab < TAB_COUNT; tab++) {
                allEntries.clear();
                buildTab(tab);
                List<OptionEntry> matched = new ArrayList<>();
                for (OptionEntry entry : allEntries) {
                    String text = entry.getSearchText();
                    if (!text.isEmpty() && text.toLowerCase(Locale.ROOT).contains(query)) {
                        matched.add(entry);
                    }
                }
                if (!matched.isEmpty()) {
                    optionList.addEntryPublic(new CategoryEntry(TAB_KEYS[tab]));
                    for (OptionEntry entry : matched) optionList.addEntryPublic(entry);
                }
            }
            return;
        }

        buildTab(selectedTab);

        // Spacer at bottom
        addOption(new SpacerEntry(30));
        addOption(new SpacerEntry(30));

        for (OptionEntry entry : allEntries) {
            optionList.addEntryPublic(entry);
        }
    }

    private void buildTab(int tab) {
        switch (tab) {
            case 0 -> initGeneralTab();
            case 1 -> initDamageInterfaceTab();
            case 2 -> initDamageNumbersTab();
            case 3 -> initEntityInfoTab();
            case 4 -> initRatingTab();
            case 5 -> initKeybindsTab();
            case 6 -> initOtherTab();
        }
    }

    private void initGeneralTab() {
        addOption(new BooleanOptionEntry("option.damage-engine.showDamage", config.showDamage, v -> { config.showDamage = v; markChanged(); }));
        addOption(new BooleanOptionEntry("option.damage-engine.reduceAnimations", config.reduceAnimations,
            v -> { config.reduceAnimations = v; markChanged(); },
            Component.translatable("hint.damage-engine.reduceAnimations")));
        // 整个 HUD 的独立帧率上限：输入框，0 = 跟随游戏帧率
        addOption(new NumericEntry("option.damage-engine.hudMaxFps", config.hudMaxFps,
            v -> { config.hudMaxFps = Mth.clamp(Math.round(v), 0, 480); markChanged(); },
            Component.translatable("hint.damage-engine.hudMaxFps")));
        addOption(new ButtonActionEntry("option.damage-engine.edit_pos_label", "button.damage-engine.adjust", () -> {
            playClickSound();
            this.minecraft.setScreen(new HudEditorScreen(this));
        }));
        addOption(new ModeSelectorEntry("option.damage-engine.hudInertia", config.hudInertiaMode,
            new String[]{"off", "de_only", "all"},
            Component.translatable("hint.damage-engine.hudInertia"),
            v -> { config.hudInertiaMode = v; markChanged(); }));
        addOption(new IntegerSliderEntry("option.damage-engine.hudInertiaStrength", config.hudInertiaStrength,
            10, 200, v -> { config.hudInertiaStrength = v; markChanged(); }, true, "%"));
        addOption(new SeparatorToggleEntry("option.damage-engine.numberSeparator", config.numberSeparator, v -> { config.numberSeparator = v; markChanged(); }));
        addOption(new BooleanOptionEntry("option.damage-engine.abbreviateNumbers", config.abbreviateNumbers,
            v -> { config.abbreviateNumbers = v; markChanged(); },
            Component.translatable("hint.damage-engine.abbreviateNumbers")));
        addOption(new BooleanOptionEntry("option.damage-engine.showDamageOverflow", config.showDamageOverflow,
            v -> { config.showDamageOverflow = v; markChanged(); },
            Component.translatable("hint.damage-engine.showDamageOverflow")));
        addOption(new BooleanOptionEntry("option.damage-engine.hideOnF1", config.hideOnF1, v -> { config.hideOnF1 = v; markChanged(); }));
    }

    private void initDamageInterfaceTab() {
        addOption(new BooleanOptionEntry("option.damage-engine.showDamageDisplay", config.showDamageDisplay, v -> { config.showDamageDisplay = v; markChanged(); }));
        addOption(new ModeSelectorEntry("option.damage-engine.alignMode", config.alignMode,
            new String[]{"right", "left"},
            Component.translatable("hint.damage-engine.alignMode"),
            v -> { config.alignMode = v; markChanged(); }));
        addOption(new IntegerSliderEntry("option.damage-engine.decimalPlaces", config.decimalPlaces, 0, 10, v -> { config.decimalPlaces = v; markChanged(); }, true));
        addOption(new HexColorEntry("option.damage-engine.normalColor", config.normalColor, v -> { config.normalColor = v; markChanged(); }));
        addOption(new HexColorEntry("option.damage-engine.critColor", config.critColor, v -> { config.critColor = v; markChanged(); }));
        // Total damage colors by reached-damage thresholds (damage-reach value + color)
        addOption(new ExpandableHeaderEntry("option.damage-engine.total_damage_colors", "total_damage_colors", v -> refreshOptions()));
        if (isExpanded("total_damage_colors")) {
            config.damageThresholds.sort((a, b) -> Float.compare(a.threshold, b.threshold));
            if (config.damageThresholds.isEmpty()) {
                addOption(new InfoEntry("text.damage-engine.empty_list"));
            }
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
        // 跳字的独立帧率上限：输入框，0 = 跟随游戏帧率
        addOption(new NumericEntry("option.damage-engine.indicatorMaxFps", config.indicatorMaxFps,
            v -> { config.indicatorMaxFps = Mth.clamp(Math.round(v), 0, 480); markChanged(); },
            Component.translatable("hint.damage-engine.indicatorMaxFps")));
        // 跳字数量上限：输入框，0 = 无限制
        addOption(new NumericEntry("option.damage-engine.indicatorMaxCount", config.indicatorMaxCount,
            v -> { config.indicatorMaxCount = Mth.clamp(Math.round(v), 0, 1000); markChanged(); },
            Component.translatable("hint.damage-engine.indicatorMaxCount")));
        // 合并跳字（默认关，悬停提示建议配合追踪模式：实体）；追踪模式：关闭/实体/准心
        addOption(new BooleanOptionEntry("option.damage-engine.indicator_merge", config.indicatorMerge,
            v -> { config.indicatorMerge = v; markChanged(); },
            Component.translatable("hint.damage-engine.indicator_merge")));
        addOption(new ModeSelectorEntry("option.damage-engine.indicator_track_mode", config.indicatorTrackMode,
            new String[]{"off", "entity", "crosshair"},
            Component.translatable("hint.damage-engine.indicator_track_mode"),
            v -> { config.indicatorTrackMode = v; markChanged(); }));
        addOption(new IntegerSliderEntry("option.damage-engine.indicatorDecimalPlaces", config.indicatorDecimalPlaces, 0, 10, v -> { config.indicatorDecimalPlaces = v; markChanged(); }, true));
        
        addOption(new ModeSelectorEntry("option.damage-engine.indicatorMode", config.indicatorMode, v -> { config.indicatorMode = v; markChanged(); }));
        
        // 动画编辑器：载入跳字当前剪辑与文本属性，预览用 DE 自己的示例数字；关闭时两者一起回写配置
        addOption(new ButtonActionEntry("option.damage-engine.animationEditor", "button.damage-engine.open", () -> {
            playClickSound();
            // 世界预览只在 Anima 的专用配置世界里生效，而那个世界只能从主菜单进
            // （ConfigWorldLauncher.launch 在世界内是空操作）。所以：
            //   不在世界内（主菜单）→ 先给提示页，进去后再开一次配置界面
            //   已经在普通存档里 → 直接开编辑器，用 2D 界面，不做进不去的死路提示
            Minecraft mc = Minecraft.getInstance();
            if (!AnimaApi.isConfigWorld() && mc.level == null) {
                mc.setScreen(new ConfigWorldNeededScreen(this));
                return;
            }
            AnimaApi.openClipEditor(this, DamageIndicator.charClipsJson(), DamageIndicator.textPropsJson(),
                DamageIndicator.PREVIEW_TEXT, result -> {
                    config.indicatorCharClips = result.clips().toString();
                    config.indicatorTextProps = result.textProps().toString();
                    markChanged();
                });
        }));
        
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
            // (1.5) 感知过滤:看不见且听不见该生物时隐藏其受到的伤害跳字
            addOption(new BooleanOptionEntry("option.damage-engine.global_smart_hide", config.globalIndicatorSmartHide,
                v -> { config.globalIndicatorSmartHide = v; markChanged(); },
                Component.translatable("hint.damage-engine.global_smart_hide")));
            // (2) 非玩家实体(可展开;悬停提示:通过添加实体注册名来显示/屏蔽)
            addOption(new ExpandableHeaderEntry("option.damage-engine.non_player_entities", "non_player_entities", v -> refreshOptions(),
                Component.translatable("hint.damage-engine.non_player_entities")));
            if (isExpanded("non_player_entities")) {
                // 整体模式:屏蔽/显示
                addOption(new ModeSelectorEntry("option.damage-engine.entity_mode", config.globalEntityBlockMode,
                    v -> { config.globalEntityBlockMode = v; markChanged(); }));
                if (config.globalEntityRules.isEmpty()) {
                    addOption(new InfoEntry("text.damage-engine.empty_list"));
                }
                for (DamageEngineConfig.GlobalEntityRule r : new ArrayList<>(config.globalEntityRules)) {
                    if (r == null) continue;
                    addOption(new GlobalEntityRuleEntry(r, () -> {
                        config.globalEntityRules.remove(r);
                        markChanged();
                        refreshOptions();
                    }));
                }
                // 添加项注册名默认空(由用户输入);不再因已存在 All 而禁用添加
                addOption(new AddButtonEntry(() -> {
                    config.globalEntityRules.add(new DamageEngineConfig.GlobalEntityRule("", 0f));
                    markChanged();
                    refreshOptions();
                }));
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
        addOption(new BooleanOptionEntry("option.damage-engine.entity_render", config.entityRenderEnabled,
            v -> { config.entityRenderEnabled = v; markChanged(); },
            Component.translatable("hint.damage-engine.entity_render")));
        addOption(new ModeSelectorEntry("option.damage-engine.entity_render_rotation", config.entityRenderRotation,
            new String[]{"follow", "custom"}, Component.translatable("hint.damage-engine.entity_render_rotation"),
            v -> { config.entityRenderRotation = v; markChanged(); refreshOptions(); }));
        // 自定义模式才需要角度;切换模式后靠 refreshOptions 让这一行出现/消失
        if ("custom".equals(config.entityRenderRotation)) {
            addOption(new IntegerSliderEntry("option.damage-engine.entity_render_rotation_angle", config.entityRenderRotationAngle,
                0, 360, v -> { config.entityRenderRotationAngle = v; markChanged(); }, true));
        }
        addOption(new BooleanOptionEntry("option.damage-engine.crosshair_entity_info", config.crosshairEntityInfo,
            v -> { config.crosshairEntityInfo = v; markChanged(); },
            Component.translatable("hint.damage-engine.crosshair_entity_info")));
        addOption(new BooleanOptionEntry("option.damage-engine.infoNoRoundedBorder", config.infoNoRoundedBorder, v -> { config.infoNoRoundedBorder = v; markChanged(); }));
        addOption(new HexColorEntry("option.damage-engine.infoBackgroundColor", config.infoBackgroundColor, v -> { config.infoBackgroundColor = v; markChanged(); }));
        addOption(new IntegerSliderEntry("option.damage-engine.infoBackgroundOpacity", config.infoBackgroundOpacity, 0, 100, v -> { config.infoBackgroundOpacity = v; markChanged(); }, true));
        addOption(new HexColorEntry("option.damage-engine.infoBarColor", config.infoBarColor, v -> { config.infoBarColor = v; markChanged(); }));
        addOption(new HexColorEntry("option.damage-engine.infoBarHealColor", config.infoBarHealColor, v -> { config.infoBarHealColor = v; markChanged(); }));
        addOption(new HexColorEntry("option.damage-engine.infoBarDamageColor", config.infoBarDamageColor, v -> { config.infoBarDamageColor = v; markChanged(); }));
    }

    private void initRatingTab() {
        addOption(new BooleanOptionEntry("option.damage-engine.showRating", config.showRating, v -> { config.showRating = v; markChanged(); }));
        addOption(new BooleanOptionEntry("option.damage-engine.showRatingScore", config.showRatingScore, v -> { config.showRatingScore = v; markChanged(); }));
        addOption(new BooleanOptionEntry("option.damage-engine.showRatingScoreSource", config.showRatingScoreSource, v -> { config.showRatingScoreSource = v; markChanged(); }));
        addOption(new HexColorEntry("option.damage-engine.ratingScoreColor", config.ratingScoreColor, v -> { config.ratingScoreColor = v; markChanged(); }));
        addOption(new IntegerSliderEntry("option.damage-engine.ratingScoreMaxGains", config.ratingScoreMaxGains, 1, 10, v -> { config.ratingScoreMaxGains = v; markChanged(); }, true));
        addOption(new NumericEntry("option.damage-engine.ratingResetTime", config.ratingResetTime, v -> { config.ratingResetTime = v; markChanged(); }));
        addOption(new NumericEntry("option.damage-engine.comboResetTime", config.comboResetTime, v -> { config.comboResetTime = v; markChanged(); }));
        
        // 加分项：进入独立的加分页面
        addOption(new ButtonActionEntry("option.damage-engine.rating_points",
            Component.literal(">"), () -> {
                playClickSound();
                this.minecraft.setScreen(new BonusConfigScreen(this, config));
            }));
        
        // Grades
        addOption(new ExpandableHeaderEntry("option.damage-engine.rating_grades", "rating_grades", v -> refreshOptions()));
        if (isExpanded("rating_grades")) {
            config.ratingGrades.sort((a, b) -> Float.compare(b.minScore, a.minScore));
            for (int i = 0; i < config.ratingGrades.size(); i++) {
                config.ratingGrades.get(i).index = i + 1;
            }
            if (config.ratingGrades.isEmpty()) {
                addOption(new InfoEntry("text.damage-engine.empty_list"));
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
            addOption(new BooleanOptionEntry("option.damage-engine.debugShowPerformance", config.debugShowPerformance,
                v -> { config.debugShowPerformance = v; markChanged(); }));
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
        // 搜索时展开所有折叠分组，保证全局搜索能搜到分组里的选项
        if (!searchQuery.isEmpty()) return true;
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
        allEntries.add(entry);
    }

    private void markChanged() {
        // Compare current config JSON with snapshot to detect real changes
        String current = new GsonBuilder().setPrettyPrinting().create().toJson(config);
        hasUnsavedChanges = !current.equals(configSnapshot);
    }

    public void setBinding(boolean binding) {
        this.isBinding = binding;
    }

    public void onBonusConfigChanged() {
        markChanged();
        refreshOptions();
    }

    public boolean hasOptionList() {
        return optionList != null;
    }

    public int getOptionListLeft() {
        return optionList == null ? 0 : optionList.getX();
    }

    public int getOptionListRight() {
        return optionList == null ? 0 : optionList.getRight();
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

    /** 在指定位置打开悬浮颜色选择器（叠加在本界面之上，不新开窗口）。 */
    public void openColorPicker(int anchorX, int anchorY, int color, Consumer<Integer> onChange) {
        closeColorPicker();
        colorPicker = new ColorPickerPopup(this, anchorX, anchorY, color, onChange, this::closeColorPicker);
    }

    public void closeColorPicker() {
        colorPicker = null;
    }

    private int getTabWidth(int index) {
        String text = Component.translatable(TAB_KEYS[index]).getString();
        return Math.max(MIN_TAB_WIDTH, this.font.width(text) + 16);
    }

    private int tabStart(int index) {
        int x = 0;
        for (int i = 0; i < index; i++) x += getTabWidth(i);
        return x;
    }

    private int getTotalTabWidth() {
        return tabStart(TAB_COUNT);
    }

    private void ensureTabVisible(int index) {
        int view = Math.max(1, (this.width - 6) - tabsLeft);
        int start = tabStart(index);
        int end = start + getTabWidth(index);
        double max = Math.max(0, getTotalTabWidth() - view);
        if (start < tabScrollTarget) tabScrollTarget = start;
        else if (end > tabScrollTarget + view) tabScrollTarget = end - view;
        tabScrollTarget = Mth.clamp(tabScrollTarget, 0, max);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (colorPicker != null && colorPicker.keyPressed(event)) return true;
        if (isBinding) {
            if (optionList != null) {
                for (GuiEventListener widget : optionList.children()) {
                    if (widget instanceof KeybindEntry ke && ke.isBinding()) {
                        if (ke.getButton().keyPressed(event)) return true;
                    }
                }
            }
            if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                setBinding(false);
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (colorPicker != null && colorPicker.keyReleased(event)) return true;
        return super.keyReleased(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (colorPicker != null && colorPicker.charTyped(event)) return true;
        return super.charTyped(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int tabsRight = this.width - 6;
        if (mouseY >= 0 && mouseY <= TOP_BAR_HEIGHT && mouseX >= tabsLeft && mouseX < tabsRight) {
            int view = Math.max(1, tabsRight - tabsLeft);
            double maxTabScroll = Math.max(0, getTotalTabWidth() - view);
            tabScrollTarget = Mth.clamp(tabScrollTarget - scrollY * 40.0, 0, maxTabScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();

        // 悬浮颜色选择器优先处理
        if (colorPicker != null) {
            if (colorPicker.mouseClicked(event, bl)) return true;
            closeColorPicker();
            return true;
        }

        // Tab clicks（顶部搜索框右侧，可横向平滑滚动）
        int tabY = 0;
        int tabHeight = TOP_BAR_HEIGHT;
        if (mouseY >= tabY && mouseY <= tabY + tabHeight && mouseX >= tabsLeft && mouseX < this.width - 6) {
            double localX = mouseX - tabsLeft + tabScroll;
            int cursor = 0;
            for (int i = 0; i < TAB_COUNT; i++) {
                int w = getTabWidth(i);
                if (localX >= cursor && localX < cursor + w) {
                    selectedTab = i;
                    ensureTabVisible(i);
                    playClickSound();
                    init();
                    return true;
                }
                cursor += w;
            }
        }
        
        // Unfocus text fields
        if (searchBox != null) searchBox.setFocused(false);
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
        return super.mouseClicked(event, bl);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (colorPicker != null && colorPicker.mouseDragged(event, dx, dy)) return true;
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (colorPicker != null) colorPicker.mouseReleased(event);
        return super.mouseReleased(event);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float delta) {
        // 游戏内背景模糊由渲染管线统一处理，这里只处理主菜单的全景背景
        if (this.minecraft != null && this.minecraft.level == null) {
            this.extractPanorama(guiGraphics, delta);
        }
        
        // 顶部条：搜索框在最左，tab 在右侧平滑滚动
        int tabsRight = this.width - 6;
        int view = Math.max(1, tabsRight - tabsLeft);
        double maxTabScroll = Math.max(0, getTotalTabWidth() - view);
        tabScrollTarget = Mth.clamp(tabScrollTarget, 0, maxTabScroll);

        // 用真实时间计算平滑系数，避免 delta 单位不同导致瞬间跳到位
        long nowMs = System.currentTimeMillis();
        float dtSec = lastFrameMs == 0 ? 0.016f
            : Mth.clamp((nowMs - lastFrameMs) / 1000f, 0.001f, 0.1f);
        lastFrameMs = nowMs;
        float smooth = 1f - (float) Math.exp(-18f * dtSec);

        tabScroll = Mth.lerp(smooth, tabScroll, tabScrollTarget);
        if (Math.abs(tabScroll - tabScrollTarget) < 0.4) tabScroll = tabScrollTarget;

        selectedVisual = Mth.lerp(smooth, selectedVisual, selectedTab);
        if (Math.abs(selectedVisual - selectedTab) < 0.01) selectedVisual = selectedTab;

        guiGraphics.enableScissor(tabsLeft, 0, tabsRight, TOP_BAR_HEIGHT);
        int cursorX = 0;
        int hoveredTab = -1;
        for (int i = 0; i < TAB_COUNT; i++) {
            int tabWidth = getTabWidth(i);
            int tabX = tabsLeft + cursorX - (int) Math.round(tabScroll);
            if (mouseX >= tabX && mouseX < tabX + tabWidth
                && mouseY >= 0 && mouseY < TOP_BAR_HEIGHT) {
                hoveredTab = i;
            }
            int bgColor = i == hoveredTab ? 0x20000000 : 0x10000000;
            guiGraphics.fill(tabX, 0, tabX + tabWidth, TOP_BAR_HEIGHT, bgColor);
            cursorX += tabWidth;
        }

        // 选中高亮的滑动动画
        int selIndex = Mth.clamp((int) Math.floor(selectedVisual), 0, TAB_COUNT - 1);
        float selFrac = (float) (selectedVisual - selIndex);
        int nextIndex = Math.min(selIndex + 1, TAB_COUNT - 1);
        float highlightX = tabsLeft
            + Mth.lerp(selFrac, (float) tabStart(selIndex), (float) tabStart(nextIndex))
            - (float) tabScroll;
        float highlightW = Mth.lerp(selFrac, (float) getTabWidth(selIndex), (float) getTabWidth(nextIndex));
        guiGraphics.fill((int) highlightX, 0, (int) (highlightX + highlightW), TOP_BAR_HEIGHT, 0x30000000);
        guiGraphics.fill((int) highlightX, TOP_BAR_HEIGHT - 2,
            (int) (highlightX + highlightW), TOP_BAR_HEIGHT, 0xFFB5F0C6);

        cursorX = 0;
        for (int i = 0; i < TAB_COUNT; i++) {
            int tabWidth = getTabWidth(i);
            int tabX = tabsLeft + cursorX - (int) Math.round(tabScroll);
            String tabText = Component.translatable(TAB_KEYS[i]).getString();
            int textColor = i == selectedTab ? 0xFFFFFFFF : (i == hoveredTab ? 0xFFD8D8D8 : 0xFFA0A0A0);
            guiGraphics.centeredText(this.font, tabText, tabX + tabWidth / 2, 7, textColor);
            cursorX += tabWidth;
        }
        guiGraphics.disableScissor();

        // Separator below tabs
        guiGraphics.fill(0, TOP_BAR_HEIGHT, this.width, TOP_BAR_HEIGHT + 1, 0xFF555555);

        // 页面内容不再做滑动动画（顶部 tab 高亮仍保留动画）
        if (optionList != null) {
            int listMx = mouseX, listMy = mouseY;
            if (colorPicker != null && colorPicker.contains(mouseX, mouseY)) {
                // 悬浮窗遮挡区域内的鼠标坐标置为远离，禁用背后列表条目的悬停高亮/光标
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
        
        // Preview
        if (config.previewEnabled) {
            new DamageHud().renderPreview(guiGraphics, 30, this.height - 30);
        }
        
        // Render footer widgets (avoid super.render() blur)
        for (GuiEventListener child : this.children()) {
            if (child != optionList && child instanceof AbstractWidget w) {
                w.extractRenderState(guiGraphics, mouseX, mouseY, delta);
            }
        }

        // 自定义提示框（不使用原版紫底样式）；最后绘制，保证盖在预览 HUD 之上
        if (optionList != null) {
            Component tip = optionList.getTooltipAt(mouseX, mouseY);
            if (tip != null) {
                ConfigTooltips.draw(guiGraphics, this.font, tip, mouseX, mouseY, this.width, this.height);
            }
        }

        // 悬浮颜色选择器画在最上层
        if (colorPicker != null) {
            colorPicker.render(guiGraphics, mouseX, mouseY, delta);
        }
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

    // ========== Option Entry Types ==========

    /** 全局搜索结果里的分类标题。 */
    private static class CategoryEntry extends OptionEntry {
        private final Component label;

        CategoryEntry(String key) {
            this.label = Component.translatable(key);
        }

        @Override protected boolean shouldHighlight() { return false; }

        @Override
        public void renderContent(GuiGraphicsExtractor g, int idx, int y, int x, int ew, int eh,
                                  int mx, int my, boolean hovered, float dt) {
            g.text(Minecraft.getInstance().font, label, x, y + 8, 0xFFB5F0C6);
            int w = Minecraft.getInstance().font.width(label);
            int lineX = x + w + 8;
            if (lineX < x + ew) {
                g.fill(lineX, y + 13, x + ew, y + 14, 0x50B5F0C6);
            }
        }
    }

    private static class HexColorEntry extends OptionEntry {
        private final EditBox field;
        private final Component label;
        private final Consumer<Integer> onChange;
        private int currentColor;
        private int swatchX, swatchY, swatchSize;
        public HexColorEntry(String key, int initial, Consumer<Integer> onChange) {
            this.label = Component.translatable(key);
            setSearchText(this.label);
            this.onChange = onChange;
            this.currentColor = initial;
            this.field = new EditBox(Minecraft.getInstance().font, 0, 0, 75, 20, Component.empty());
            this.field.setBordered(false); this.field.setMaxLength(6);
            this.field.setValue(String.format("%06X", initial & 0xFFFFFF));
            this.field.setResponder(s -> {
                String filtered = s.replaceAll("[^0-9a-fA-F]", "");
                if (!filtered.equals(s)) { this.field.setValue(filtered); return; }
                try { String hex = s.startsWith("#") ? s.substring(1) : s;
                    currentColor = (int)Long.parseLong(hex, 16) | 0xFF000000;
                    onChange.accept(currentColor); } catch(Exception ignored){}
            });
        }
        @Override public void renderContent(GuiGraphicsExtractor g, int idx, int y, int x, int ew, int eh, int mx, int my, boolean hovered, float dt) {
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
            g.fill(swatchX, swatchY, swatchX + swatchSize, swatchY + swatchSize, over ? 0xFFB5F0C6 : 0xFFFFFFFF);
            g.fill(px, py, px + ps, py + ps, 0xFF000000 | (currentColor & 0xFFFFFF));
        }
        @Override public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
            double mx = event.x(), my = event.y();
            int btn = event.button();
            if (btn == 0 && swatchSize > 0
                && mx >= swatchX && mx <= swatchX + swatchSize
                && my >= swatchY && my <= swatchY + swatchSize) {
                if (Minecraft.getInstance().screen instanceof DamageConfigScreen s) {
                    s.playClickSound();
                    s.openColorPicker(swatchX, swatchY, currentColor, c -> {
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

    private class ExpandableHeaderEntry extends OptionEntry {
        private final PlainTextButton button;
        private final Component label;
        private final String expandKey;
        public ExpandableHeaderEntry(String key, String expandKey, Consumer<Boolean> onToggle) {
            this(key, expandKey, onToggle, null);
        }
        public ExpandableHeaderEntry(String key, String expandKey, Consumer<Boolean> onToggle, Component tooltip) {
            this.label = Component.translatable(key);
            setSearchText(this.label);
            this.expandKey = expandKey;
            boolean expanded = isExpanded(expandKey);
            this.button = new PlainTextButton(0, 0, 20, 20, Component.literal(expanded ? "-" : "+"), () -> {
                expandStates.put(expandKey, !isExpanded(expandKey));
                onToggle.accept(isExpanded(expandKey));
            }, 0xFFFFFFFF, 0xFFFBFB54);
            if (tooltip != null) {
                setHoverTooltip(tooltip);
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
            setSearchText(this.label);
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
            this.colField.setBordered(false); this.colField.setMaxLength(6);
            this.colField.setValue(String.format("%06X", dt.color & 0xFFFFFF));
            this.colField.setResponder(s -> { String filtered = s.replaceAll("[^0-9a-fA-F]", "");
                if (!filtered.equals(s)) { this.colField.setValue(filtered); return; }
                try { String hex = s.startsWith("#") ? s.substring(1) : s;
                currentColor = (int)Long.parseLong(hex, 16) | 0xFF000000; dt.color = currentColor; } catch(Exception ignored){} });
            this.delBtn = new PlainTextButton(0, 0, 20, 20, Component.literal("-"), onDelete, 0xFFFFFFFF, 0xFFFBFB54);
        }
        @Override public void renderContent(GuiGraphicsExtractor g, int idx, int y, int x, int ew, int eh, int mx, int my, boolean hovered, float dt) {
            int rx = x + ew;
            delBtn.setX(rx - 25); delBtn.setY(y + 2); delBtn.setWidth(20); delBtn.setForceHover(hovered);
            int ps = 18, px = rx - 25 - 5 - ps, py = y + 3;
            swatchX = px - 1; swatchY = py - 1; swatchSize = ps + 2;
            boolean overSwatch = mx >= swatchX && mx <= swatchX + swatchSize && my >= swatchY && my <= swatchY + swatchSize;
            int cbw = 55, cbx = px - 5 - cbw, cby = y + 2, cbh = 20;
            colField.setX(cbx + 4); colField.setY(cby + 6); colField.setWidth(cbw - 8); colField.setHeight(12);
            int vbw = 40, vbx = cbx - 5 - vbw, vby = y + 2, vbh = 20;
            valField.setX(vbx + 4); valField.setY(vby + 6); valField.setWidth(vbw - 8); valField.setHeight(12);
            g.text(Minecraft.getInstance().font, Component.translatable("text.damage-engine.damage_reach"), x + 10, y + 8, 0xFFFFFFFF);
            drawTextBox(g, vbx, vby, vbw, vbh, valField, mx, my);
            drawTextBox(g, cbx, cby, cbw, cbh, colField, mx, my);
            valField.extractRenderState(g, mx, my, dt); colField.extractRenderState(g, mx, my, dt);
            delBtn.extractRenderState(g, mx, my, dt);
            g.fill(swatchX, swatchY, swatchX + swatchSize, swatchY + swatchSize, overSwatch ? 0xFFB5F0C6 : 0xFFFFFFFF);
            g.fill(px, py, px + ps, py + ps, 0xFF000000 | (currentColor & 0xFFFFFF));
        }
        @Override public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
            double mx = event.x(), my = event.y();
            int btn = event.button();
            if (btn == 0 && swatchSize > 0
                && mx >= swatchX && mx <= swatchX + swatchSize
                && my >= swatchY && my <= swatchY + swatchSize) {
                if (Minecraft.getInstance().screen instanceof DamageConfigScreen s) {
                    s.playClickSound();
                    s.openColorPicker(swatchX, swatchY, currentColor, c -> {
                        currentColor = c | 0xFF000000;
                        dt.color = currentColor;
                        colField.setValue(String.format("%06X", currentColor & 0xFFFFFF));
                    });
                }
                return true;
            }
            return super.mouseClicked(event, bl);
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

    private static class GlobalEntityRuleEntry extends OptionEntry {
        private final DamageEngineConfig.GlobalEntityRule rule;
        private final EditBox nameField, distField;
        private final PlainTextButton delBtn;
        public GlobalEntityRuleEntry(DamageEngineConfig.GlobalEntityRule rule, Runnable onDelete) {
            this.rule = rule;
            this.nameField = new EditBox(Minecraft.getInstance().font, 0, 0, 110, 20, Component.empty());
            this.nameField.setBordered(false); this.nameField.setMaxLength(64);
            this.nameField.setValue(rule.registryName != null ? rule.registryName : "");
            this.nameField.setResponder(s -> { rule.registryName = s; });
            this.distField = new EditBox(Minecraft.getInstance().font, 0, 0, 45, 20, Component.empty());
            this.distField.setBordered(false); this.distField.setMaxLength(7);
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
            int nx = x + 10 + nameLblW + 4;
            int dlx = nx + nbw + 12;
            int dbx = dlx + distLblW + 4;
            g.text(Minecraft.getInstance().font, Component.translatable("text.damage-engine.registry_name"), x + 10, y + 8, 0xFFFFFFFF);
            nameField.setX(nx + 4); nameField.setY(by + 6); nameField.setWidth(nbw - 8); nameField.setHeight(12);
            g.text(Minecraft.getInstance().font, Component.translatable("text.damage-engine.distance"), dlx, y + 8, 0xFFFFFFFF);
            distField.setX(dbx + 4); distField.setY(by + 6); distField.setWidth(dbw - 8); distField.setHeight(12);
            drawBox(g, nx, by, nbw, bh, nameField, mx, my);
            drawBox(g, dbx, by, dbw, bh, distField, mx, my);
            nameField.extractRenderState(g, mx, my, dt); distField.extractRenderState(g, mx, my, dt); delBtn.extractRenderState(g, mx, my, dt);
        }
        private void drawBox(GuiGraphicsExtractor g, int bx, int by, int bw, int bh, EditBox f, int mx, int my) {
            g.fill(bx, by, bx + bw, by + bh, 0x20000000);
            int bc = (f.isFocused() || f.isMouseOver(mx, my)) ? 0xFFFFFFFF : 0xFFA0A0A0;
            g.fill(bx, by, bx + bw, by + 1, bc); g.fill(bx, by + bh - 1, bx + bw, by + bh, bc);
            g.fill(bx, by, bx + 1, by + bh, bc); g.fill(bx + bw - 1, by, bx + bw, by + bh, bc);
        }
        @Override public Component getTooltipAt(int mx, int my) {
            if (nameField.isMouseOver(mx, my)) {
                return Component.translatable("hint.damage-engine.entity_registry_name");
            }
            if (distField.isMouseOver(mx, my)) {
                return Component.translatable("hint.damage-engine.globalIndicatorMaxDistance");
            }
            return null;
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
            g.text(Minecraft.getInstance().font, Component.literal("≥"), x + 10, y + 8, 0xFFFFFFFF);
            drawBox(g, sbx, by, bw, bh, scoreField, mx, my);
            drawBox(g, tbx, by, bw, bh, textField, mx, my);
            scoreField.extractRenderState(g, mx, my, dt); textField.extractRenderState(g, mx, my, dt); delBtn.extractRenderState(g, mx, my, dt);
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
            this.colField.setBordered(false); this.colField.setMaxLength(6);
            this.colField.setValue(String.format("%06X", g.color & 0xFFFFFF));
            this.colField.setResponder(s -> { String filtered = s.replaceAll("[^0-9a-fA-F]", "");
                if (!filtered.equals(s)) { this.colField.setValue(filtered); return; }
                try { String hex = s.startsWith("#") ? s.substring(1) : s;
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
        @Override public void renderContent(GuiGraphicsExtractor g, int idx, int y, int x, int ew, int eh, int mx, int my, boolean hovered, float dt) {
            int rx = x + ew, by = y + 2;
            if (config.ratingUseImages) {
                boolean hasImg = grade.imagePath != null && !grade.imagePath.isEmpty();
                selectImageBtn.setX(rx - 110); selectImageBtn.setY(by); selectImageBtn.setWidth(100); selectImageBtn.visible = true;
                resetImageBtn.visible = hasImg;
                if (hasImg) { resetImageBtn.setX(rx - 110 - 25); resetImageBtn.setY(by); resetImageBtn.setWidth(20); }
                String pt = hasImg ? grade.imagePath : "Not set";
                if (pt.contains("/")) pt = pt.substring(pt.lastIndexOf("/") + 1);
                g.text(Minecraft.getInstance().font, Component.literal(grade.text), x + 10, y + 8, 0xFFFFFFFF);
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
                colField.setX(sx + 4); colField.setY(by + 6); colField.setWidth(75 - 8); colField.setHeight(12); colField.visible = true;
                g.text(Minecraft.getInstance().font, Component.literal(grade.text), x + 10, y + 8, 0xFFFFFFFF);
                drawBox(g, sx, by, 75, 20, colField, mx, my);
                colField.extractRenderState(g, mx, my, dt);
                g.fill(swatchX, swatchY, swatchX + swatchSize, swatchY + swatchSize, overSwatch ? 0xFFB5F0C6 : 0xFFFFFFFF);
                g.fill(px, py, px + ps, py + ps, 0xFF000000 | (currentColor & 0xFFFFFF));
            }
        }
        @Override public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
            double mx = event.x(), my = event.y();
            int btn = event.button();
            if (!config.ratingUseImages && btn == 0 && swatchSize > 0
                && mx >= swatchX && mx <= swatchX + swatchSize
                && my >= swatchY && my <= swatchY + swatchSize) {
                if (Minecraft.getInstance().screen instanceof DamageConfigScreen s) {
                    s.playClickSound();
                    s.openColorPicker(swatchX, swatchY, currentColor, c -> {
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
            setSearchText(this.label);
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

}