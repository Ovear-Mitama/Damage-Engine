package damage.engine.client.gui;

import anima.api.AnimaApi;
import com.google.gson.GsonBuilder;
import damage.engine.ClientKeybindings;
import damage.engine.DamageEngineConfig;
import damage.engine.hud.DamageHud;
import damage.engine.hud.indicator.DamageIndicator;
import mitama.configlib.client.gui.*;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class DamageConfigScreen extends ConfigScreen {
    private final DamageEngineConfig config;
    private String configSnapshot;

    private static final String[] TAB_KEYS = {
        "tab.damage-engine.general",
        "tab.damage-engine.damage_interface",
        "tab.damage-engine.damage_numbers",
        "tab.damage-engine.entity_info",
        "tab.damage-engine.rating",
        "tab.damage-engine.keybinds",
        "tab.damage-engine.other"
    };

    public DamageConfigScreen(Screen parent) {
        super(parent, Component.translatable("title.damage-engine.config"));
        this.config = DamageEngineConfig.getInstance();
        this.config.load();
        this.configSnapshot = snapshot();
    }

    private String snapshot() {
        return new GsonBuilder().setPrettyPrinting().create().toJson(config);
    }

    @Override
    protected String[] tabKeys() {
        return TAB_KEYS;
    }

    @Override
    protected void buildTab(int tab) {
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

    @Override
    protected void saveConfig() {
        config.save();
    }

    @Override
    protected void reloadConfig() {
        config.load();
    }

    @Override
    protected boolean computeDirty() {
        return !snapshot().equals(configSnapshot);
    }

    @Override
    protected Component saveButtonText() {
        return Component.translatable("button.damage-engine.save_close");
    }

    @Override
    protected Component searchHint() {
        return Component.translatable("text.damage-engine.search");
    }

    @Override
    protected Component unsavedChangesTitle() {
        return Component.translatable("text.damage-engine.unsaved_changes_title");
    }

    @Override
    protected Component unsavedChangesMessage() {
        return Component.translatable("text.damage-engine.unsaved_changes");
    }

    @Override
    protected void addFooterWidgets(int footerY) {
        int buttonY = footerY + 5;
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
    }

    @Override
    protected void renderFooterOverlay(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        if (config.previewEnabled) {
            new DamageHud().renderPreview(guiGraphics, 30, this.height - 30);
        }
    }

    public void onBonusConfigChanged() {
        markChanged();
        refreshOptions();
    }

    private static Component modeText(String mode) {
        boolean off = "off".equals(mode) || "block".equals(mode);
        return Component.translatable("option.damage-engine.mode." + mode)
            .withColor(off ? 0xFFFC887E : 0xFFB5F0C6);
    }

    private static HexColorEntry colorEntry(String key, int initial, Consumer<Integer> onChange) {
        return new HexColorEntry(Component.translatable(key), initial,
            Component.translatable("title.damage-engine.color_picker"), onChange);
    }

    private void initGeneralTab() {
        addOption(new BooleanOptionEntry("option.damage-engine.showDamage", config.showDamage, v -> { config.showDamage = v; markChanged(); }));
        addOption(new BooleanOptionEntry("option.damage-engine.client_downgrade_warning",
            config.showClientDowngradeWarning,
            v -> { config.showClientDowngradeWarning = v; markChanged(); },
            Component.translatable("hint.damage-engine.client_downgrade_warning")));
        addOption(new BooleanOptionEntry("option.damage-engine.reduceAnimations", config.reduceAnimations,
            v -> { config.reduceAnimations = v; markChanged(); },
            Component.translatable("hint.damage-engine.reduceAnimations")));
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
            v -> { config.hudInertiaMode = v; markChanged(); }, DamageConfigScreen::modeText));
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
            v -> { config.alignMode = v; markChanged(); }, DamageConfigScreen::modeText));
        addOption(new IntegerSliderEntry("option.damage-engine.decimalPlaces", config.decimalPlaces, 0, 10, v -> { config.decimalPlaces = v; markChanged(); }, true));
        addOption(colorEntry("option.damage-engine.normalColor", config.normalColor, v -> { config.normalColor = v; markChanged(); }));
        addOption(colorEntry("option.damage-engine.critColor", config.critColor, v -> { config.critColor = v; markChanged(); }));
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
        addOption(colorEntry("option.damage-engine.progressBarColor", config.progressBarColor, v -> { config.progressBarColor = v; markChanged(); }));
        addOption(new BooleanOptionEntry("option.damage-engine.showCombo", config.showCombo, v -> { config.showCombo = v; markChanged(); }));
        addOption(colorEntry("option.damage-engine.comboColor", config.comboColor, v -> { config.comboColor = v; markChanged(); }));
        addOption(new BooleanOptionEntry("option.damage-engine.showDamageHistory", config.showDamageHistory, v -> { config.showDamageHistory = v; markChanged(); }));
        addOption(new BooleanOptionEntry("option.damage-engine.recordOtherPlayers", config.recordOtherPlayers, v -> { config.recordOtherPlayers = v; markChanged(); }));
        addOption(new NumericEntry("option.damage-engine.historyDisappearanceTime", config.historyDisappearanceTime, v -> { config.historyDisappearanceTime = v; markChanged(); }));
        addOption(new IntegerSliderEntry("option.damage-engine.historyLimit", config.historyLimit, 1, 50, v -> { config.historyLimit = v; markChanged(); }, true));
        addOption(new IntegerSliderEntry("option.damage-engine.historyDecimalPlaces", config.historyDecimalPlaces, 0, 10, v -> { config.historyDecimalPlaces = v; markChanged(); }, true));
    }

    private void initDamageNumbersTab() {
        addOption(new BooleanOptionEntry("option.damage-engine.showDamageIndicator", config.showDamageIndicator, v -> { config.showDamageIndicator = v; markChanged(); }));
        addOption(new NumericEntry("option.damage-engine.indicatorMaxFps", config.indicatorMaxFps,
            v -> { config.indicatorMaxFps = Mth.clamp(Math.round(v), 0, 480); markChanged(); },
            Component.translatable("hint.damage-engine.indicatorMaxFps")));
        addOption(new NumericEntry("option.damage-engine.indicatorMaxCount", config.indicatorMaxCount,
            v -> { config.indicatorMaxCount = Mth.clamp(Math.round(v), 0, 1000); markChanged(); },
            Component.translatable("hint.damage-engine.indicatorMaxCount")));
        addOption(new BooleanOptionEntry("option.damage-engine.indicator_merge", config.indicatorMerge,
            v -> { config.indicatorMerge = v; markChanged(); },
            Component.translatable("hint.damage-engine.indicator_merge")));
        addOption(new ModeSelectorEntry("option.damage-engine.indicator_track_mode", config.indicatorTrackMode,
            new String[]{"off", "entity", "crosshair"},
            Component.translatable("hint.damage-engine.indicator_track_mode"),
            v -> { config.indicatorTrackMode = v; markChanged(); }, DamageConfigScreen::modeText));
        addOption(new IntegerSliderEntry("option.damage-engine.indicatorDecimalPlaces", config.indicatorDecimalPlaces, 0, 10, v -> { config.indicatorDecimalPlaces = v; markChanged(); }, true));

        addOption(new ModeSelectorEntry("option.damage-engine.indicatorMode", config.indicatorMode,
            new String[]{"enhanced", "cloud"},
            Component.translatable("hint.damage-engine.indicatorMode"),
            v -> { config.indicatorMode = v; markChanged(); }, DamageConfigScreen::modeText));

        addOption(new ButtonActionEntry("option.damage-engine.animationEditor", "button.damage-engine.open", () -> {
            playClickSound();
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

        addOption(colorEntry("option.damage-engine.damageIndicatorNormalColor", config.damageIndicatorNormalColor, v -> { config.damageIndicatorNormalColor = v; markChanged(); }));
        addOption(colorEntry("option.damage-engine.damageIndicatorCritColor", config.damageIndicatorCritColor, v -> { config.damageIndicatorCritColor = v; markChanged(); }));
        addOption(new BooleanOptionEntry("option.damage-engine.indicatorBold", config.indicatorBold, v -> { config.indicatorBold = v; markChanged(); }));
        addOption(new BooleanOptionEntry("option.damage-engine.showHealIndicator", config.showHealIndicator, v -> { config.showHealIndicator = v; markChanged(); }));
        addOption(colorEntry("option.damage-engine.healIndicatorColor", config.healIndicatorColor, v -> { config.healIndicatorColor = v; markChanged(); }));

        addOption(new ExpandableHeaderEntry("option.damage-engine.global_damage_numbers", "global_damage_numbers", v -> refreshOptions()));
        if (isExpanded("global_damage_numbers")) {
            addOption(new BooleanOptionEntry("option.damage-engine.showGlobalDamageIndicator", config.showGlobalDamageIndicator, v -> { config.showGlobalDamageIndicator = v; markChanged(); }));
            addOption(new NumericEntry("option.damage-engine.global_player_distance", config.globalIndicatorMaxDistance,
                v -> { config.globalIndicatorMaxDistance = v; markChanged(); },
                Component.translatable("hint.damage-engine.globalIndicatorMaxDistance")));
            addOption(new BooleanOptionEntry("option.damage-engine.global_smart_hide", config.globalIndicatorSmartHide,
                v -> { config.globalIndicatorSmartHide = v; markChanged(); },
                Component.translatable("hint.damage-engine.global_smart_hide")));
            addOption(new ExpandableHeaderEntry("option.damage-engine.non_player_entities", "non_player_entities", v -> refreshOptions(),
                Component.translatable("hint.damage-engine.non_player_entities")));
            if (isExpanded("non_player_entities")) {
                addOption(new ModeSelectorEntry("option.damage-engine.entity_mode",
                    config.globalEntityBlockMode ? "block" : "show",
                    new String[]{"show", "block"}, null,
                    v -> { config.globalEntityBlockMode = "block".equals(v); markChanged(); }, DamageConfigScreen::modeText));
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
                addOption(new AddButtonEntry(() -> {
                    config.globalEntityRules.add(new DamageEngineConfig.GlobalEntityRule("", 0f));
                    markChanged();
                    refreshOptions();
                }));
            }
        }
        addOption(new BooleanOptionEntry("option.damage-engine.indicatorPrefixSign", config.indicatorPrefixSign, v -> { config.indicatorPrefixSign = v; markChanged(); }));
        addOption(new BooleanOptionEntry("option.damage-engine.showKillIndicator", config.showKillIndicator, v -> { config.showKillIndicator = v; markChanged(); }));
        addOption(new ExpandableHeaderEntry("option.damage-engine.killText", "kill_texts", v -> refreshOptions(),
            Component.translatable("hint.damage-engine.kill_texts")));
        if (isExpanded("kill_texts")) {
            if (config.killTexts == null) config.killTexts = new ArrayList<>();
            if (config.killTexts.isEmpty()) {
                addOption(new InfoEntry("text.damage-engine.empty_list"));
            }
            for (DamageEngineConfig.KillText kt : new ArrayList<>(config.killTexts)) {
                if (kt == null) continue;
                addOption(new KillTextEntry(kt, () -> {
                    config.killTexts.remove(kt);
                    markChanged();
                    refreshOptions();
                }));
            }
            addOption(new AddButtonEntry(() -> {
                config.killTexts.add(new DamageEngineConfig.KillText("Kill!", 1f, config.killTextColor));
                markChanged();
                refreshOptions();
            }));
        }
    }

    private void initEntityInfoTab() {
        addOption(new BooleanOptionEntry("option.damage-engine.showInfo", config.showInfo, v -> { config.showInfo = v; markChanged(); }));
        addOption(new NumericEntry("option.damage-engine.infoTrackTime", config.infoTrackTime, v -> { config.infoTrackTime = v; markChanged(); }));
        addOption(new BooleanOptionEntry("option.damage-engine.entity_render", config.entityRenderEnabled,
            v -> { config.entityRenderEnabled = v; markChanged(); },
            Component.translatable("hint.damage-engine.entity_render")));
        addOption(new ModeSelectorEntry("option.damage-engine.entity_render_rotation", config.entityRenderRotation,
            new String[]{"follow", "custom"}, Component.translatable("hint.damage-engine.entity_render_rotation"),
            v -> { config.entityRenderRotation = v; markChanged(); refreshOptions(); }, DamageConfigScreen::modeText));
        if ("custom".equals(config.entityRenderRotation)) {
            addOption(new IntegerSliderEntry("option.damage-engine.entity_render_rotation_angle", config.entityRenderRotationAngle,
                0, 360, v -> { config.entityRenderRotationAngle = v; markChanged(); }, true));
        }
        addOption(new BooleanOptionEntry("option.damage-engine.crosshair_entity_info", config.crosshairEntityInfo,
            v -> { config.crosshairEntityInfo = v; markChanged(); },
            Component.translatable("hint.damage-engine.crosshair_entity_info")));
        addOption(new BooleanOptionEntry("option.damage-engine.infoNoRoundedBorder", config.infoNoRoundedBorder, v -> { config.infoNoRoundedBorder = v; markChanged(); }));
        addOption(colorEntry("option.damage-engine.infoBackgroundColor", config.infoBackgroundColor, v -> { config.infoBackgroundColor = v; markChanged(); }));
        addOption(new IntegerSliderEntry("option.damage-engine.infoBackgroundOpacity", config.infoBackgroundOpacity, 0, 100, v -> { config.infoBackgroundOpacity = v; markChanged(); }, true));
        addOption(colorEntry("option.damage-engine.infoBarColor", config.infoBarColor, v -> { config.infoBarColor = v; markChanged(); }));
        addOption(colorEntry("option.damage-engine.infoBarHealColor", config.infoBarHealColor, v -> { config.infoBarHealColor = v; markChanged(); }));
        addOption(colorEntry("option.damage-engine.infoBarDamageColor", config.infoBarDamageColor, v -> { config.infoBarDamageColor = v; markChanged(); }));
    }

    private void initRatingTab() {
        addOption(new BooleanOptionEntry("option.damage-engine.showRating", config.showRating, v -> { config.showRating = v; markChanged(); }));
        addOption(new BooleanOptionEntry("option.damage-engine.showRatingScore", config.showRatingScore, v -> { config.showRatingScore = v; markChanged(); }));
        addOption(new BooleanOptionEntry("option.damage-engine.showRatingScoreSource", config.showRatingScoreSource, v -> { config.showRatingScoreSource = v; markChanged(); }));
        addOption(colorEntry("option.damage-engine.ratingScoreColor", config.ratingScoreColor, v -> { config.ratingScoreColor = v; markChanged(); }));
        addOption(new IntegerSliderEntry("option.damage-engine.ratingScoreMaxGains", config.ratingScoreMaxGains, 1, 10, v -> { config.ratingScoreMaxGains = v; markChanged(); }, true));
        addOption(new NumericEntry("option.damage-engine.ratingResetTime", config.ratingResetTime, v -> { config.ratingResetTime = v; markChanged(); }));
        addOption(new NumericEntry("option.damage-engine.comboResetTime", config.comboResetTime, v -> { config.comboResetTime = v; markChanged(); }));

        addOption(new ButtonActionEntry("option.damage-engine.rating_points",
            Component.literal(">"), () -> {
            playClickSound();
            this.minecraft.setScreen(new BonusConfigScreen(this, config));
        }));

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
        Component unbound = Component.translatable("key.damage_engine.not_bound");
        Component conflict = Component.translatable("text.damage-engine.key_conflict");
        if (ClientKeybindings.configKeyBinding != null) {
            addOption(new KeybindEntry("key.damage_engine.config", ClientKeybindings.configKeyBinding, unbound, conflict));
        }
        if (ClientKeybindings.toggleHudKeyBinding != null) {
            addOption(new KeybindEntry("key.damage_engine.toggle_hud", ClientKeybindings.toggleHudKeyBinding, unbound, conflict));
        }
        if (ClientKeybindings.clearDamageKeyBinding != null) {
            addOption(new KeybindEntry("key.damage_engine.clear_damage", ClientKeybindings.clearDamageKeyBinding, unbound, conflict));
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

        addOption(new ResetButtonEntry("option.damage-engine.reset",
            Component.translatable("gui.reset"),
            Component.translatable("gui.confirm"),
            Component.translatable("text.damage-engine.reset_done"),
            () -> {
                config.resetToDefaults();
                KeyMapping.resetMapping();
                this.minecraft.options.save();
                clearDirty();
                this.init();
            }));
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
            this.valField.setBordered(false);
            this.valField.setValue(String.format("%.0f", dt.threshold));
            this.valField.setResponder(s -> {
                try {
                    dt.threshold = Float.parseFloat(s);
                } catch (Exception ignored) {
                }
            });
            this.colField = new EditBox(Minecraft.getInstance().font, 0, 0, 55, 20, Component.empty());
            this.colField.setBordered(false);
            this.colField.setMaxLength(6);
            this.colField.setValue(String.format("%06X", dt.color & 0xFFFFFF));
            this.colField.setResponder(s -> {
                String filtered = s.replaceAll("[^0-9a-fA-F]", "");
                if (!filtered.equals(s)) {
                    this.colField.setValue(filtered);
                    return;
                }
                try {
                    String hex = s.startsWith("#") ? s.substring(1) : s;
                    currentColor = (int) Long.parseLong(hex, 16) | 0xFF000000;
                    dt.color = currentColor;
                } catch (Exception ignored) {
                }
            });
            this.delBtn = new PlainTextButton(0, 0, 20, 20, Component.literal("-"), onDelete, 0xFFFFFFFF, 0xFFFBFB54);
        }

        @Override
        public void renderContent(GuiGraphics g, int idx, int y, int x, int ew, int eh,
                                  int mx, int my, boolean hovered, float dt) {
            int rx = x + ew;
            delBtn.setX(rx - 25);
            delBtn.setY(y + 2);
            delBtn.setWidth(20);
            delBtn.setForceHover(hovered);
            int ps = 18, px = rx - 25 - 5 - ps, py = y + 3;
            swatchX = px - 1;
            swatchY = py - 1;
            swatchSize = ps + 2;
            boolean overSwatch = mx >= swatchX && mx <= swatchX + swatchSize && my >= swatchY && my <= swatchY + swatchSize;
            int cbw = 55, cbx = px - 5 - cbw, cby = y + 2, cbh = 20;
            colField.setX(cbx + 4);
            colField.setY(cby + 6);
            colField.setWidth(cbw - 8);
            colField.setHeight(12);
            int vbw = 40, vbx = cbx - 5 - vbw, vby = y + 2, vbh = 20;
            valField.setX(vbx + 4);
            valField.setY(vby + 6);
            valField.setWidth(vbw - 8);
            valField.setHeight(12);
            g.drawString(Minecraft.getInstance().font, Component.translatable("text.damage-engine.damage_reach"), x + 10, y + 8, 0xFFFFFFFF);
            drawTextBox(g, vbx, vby, vbw, vbh, valField, mx, my);
            drawTextBox(g, cbx, cby, cbw, cbh, colField, mx, my);
            valField.render(g, mx, my, dt);
            colField.render(g, mx, my, dt);
            delBtn.render(g, mx, my, dt);
            g.fill(swatchX, swatchY, swatchX + swatchSize, swatchY + swatchSize, overSwatch ? 0xFFB5F0C6 : 0xFFFFFFFF);
            g.fill(px, py, px + ps, py + ps, 0xFF000000 | (currentColor & 0xFFFFFF));
        }

        @Override
        public boolean mouseClicked(double mx, double my, int btn) {
            if (btn == 0 && swatchSize > 0
                && mx >= swatchX && mx <= swatchX + swatchSize
                && my >= swatchY && my <= swatchY + swatchSize) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.screen instanceof ClickSoundHost host) host.playClickSound();
                Screen screen = mc.screen;
                if (screen != null) {
                    mc.setScreen(new ColorPickerScreen(screen,
                        Component.translatable("title.damage-engine.color_picker"), currentColor, c -> {
                        currentColor = c | 0xFF000000;
                        dt.color = currentColor;
                        colField.setValue(String.format("%06X", currentColor & 0xFFFFFF));
                    }));
                }
                return true;
            }
            return super.mouseClicked(mx, my, btn);
        }

        private void drawTextBox(GuiGraphics g, int bx, int by, int bw, int bh, EditBox f, int mx, int my) {
            g.fill(bx, by, bx + bw, by + bh, 0x20000000);
            int bc = (f.isFocused() || f.isMouseOver(mx, my)) ? 0xFFFFFFFF : 0xFFA0A0A0;
            g.fill(bx, by, bx + bw, by + 1, bc);
            g.fill(bx, by + bh - 1, bx + bw, by + bh, bc);
            g.fill(bx, by, bx + 1, by + bh, bc);
            g.fill(bx + bw - 1, by, bx + bw, by + bh, bc);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return Arrays.asList(valField, colField, delBtn);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return Arrays.asList(valField, colField, delBtn);
        }
    }

    private static class GlobalEntityRuleEntry extends OptionEntry {
        private final DamageEngineConfig.GlobalEntityRule rule;
        private final EditBox nameField, distField;
        private final PlainTextButton delBtn;

        public GlobalEntityRuleEntry(DamageEngineConfig.GlobalEntityRule rule, Runnable onDelete) {
            this.rule = rule;
            this.nameField = new EditBox(Minecraft.getInstance().font, 0, 0, 110, 20, Component.empty());
            this.nameField.setBordered(false);
            this.nameField.setMaxLength(64);
            this.nameField.setValue(rule.registryName != null ? rule.registryName : "");
            this.nameField.setResponder(s -> rule.registryName = s);
            this.distField = new EditBox(Minecraft.getInstance().font, 0, 0, 45, 20, Component.empty());
            this.distField.setBordered(false);
            this.distField.setMaxLength(7);
            this.distField.setValue(String.format("%.0f", rule.distance));
            this.distField.setResponder(s -> {
                String filtered = s.replaceAll("[^0-9.]", "");
                if (!filtered.equals(s)) {
                    this.distField.setValue(filtered);
                    return;
                }
                try {
                    rule.distance = Float.parseFloat(s);
                } catch (Exception ignored) {
                }
            });
            this.delBtn = new PlainTextButton(0, 0, 20, 20, Component.literal("-"), onDelete, 0xFFFFFFFF, 0xFFFBFB54);
        }

        @Override
        public void renderContent(GuiGraphics g, int idx, int y, int x, int ew, int eh,
                                  int mx, int my, boolean hovered, float dt) {
            int rx = x + ew;
            delBtn.setX(rx - 25);
            delBtn.setY(y + 2);
            delBtn.setWidth(20);
            delBtn.setForceHover(hovered);
            int bh = 20, by = y + (eh - bh) / 2;
            int nbw = 110, dbw = 45;
            int nameLblW = Minecraft.getInstance().font.width(Component.translatable("text.damage-engine.registry_name"));
            int distLblW = Minecraft.getInstance().font.width(Component.translatable("text.damage-engine.distance"));
            int nx = x + 10 + nameLblW + 4;
            int dlx = nx + nbw + 12;
            int dbx = dlx + distLblW + 4;
            g.drawString(Minecraft.getInstance().font, Component.translatable("text.damage-engine.registry_name"), x + 10, y + 8, 0xFFFFFFFF);
            nameField.setX(nx + 4);
            nameField.setY(by + 6);
            nameField.setWidth(nbw - 8);
            nameField.setHeight(12);
            g.drawString(Minecraft.getInstance().font, Component.translatable("text.damage-engine.distance"), dlx, y + 8, 0xFFFFFFFF);
            distField.setX(dbx + 4);
            distField.setY(by + 6);
            distField.setWidth(dbw - 8);
            distField.setHeight(12);
            drawBox(g, nx, by, nbw, bh, nameField, mx, my);
            drawBox(g, dbx, by, dbw, bh, distField, mx, my);
            nameField.render(g, mx, my, dt);
            distField.render(g, mx, my, dt);
            delBtn.render(g, mx, my, dt);
        }

        private void drawBox(GuiGraphics g, int bx, int by, int bw, int bh, EditBox f, int mx, int my) {
            g.fill(bx, by, bx + bw, by + bh, 0x20000000);
            int bc = (f.isFocused() || f.isMouseOver(mx, my)) ? 0xFFFFFFFF : 0xFFA0A0A0;
            g.fill(bx, by, bx + bw, by + 1, bc);
            g.fill(bx, by + bh - 1, bx + bw, by + bh, bc);
            g.fill(bx, by, bx + 1, by + bh, bc);
            g.fill(bx + bw - 1, by, bx + bw, by + bh, bc);
        }

        @Override
        public Component getTooltipAt(int mx, int my) {
            if (nameField.isMouseOver(mx, my)) {
                return Component.translatable("hint.damage-engine.entity_registry_name");
            }
            if (distField.isMouseOver(mx, my)) {
                return Component.translatable("hint.damage-engine.globalIndicatorMaxDistance");
            }
            return null;
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return Arrays.asList(nameField, distField, delBtn);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return Arrays.asList(nameField, distField, delBtn);
        }
    }

    private class KillTextEntry extends OptionEntry {
        private final DamageEngineConfig.KillText entry;
        private final EditBox textField;
        private final EditBox weightField;
        private final EditBox colorField;
        private final PlainTextButton delBtn;
        private int currentColor;
        private int swatchX, swatchY, swatchSize;

        public KillTextEntry(DamageEngineConfig.KillText entry, Runnable onDelete) {
            this.entry = entry;
            this.currentColor = entry.resolveColor(config.killTextColor);
            this.textField = new EditBox(Minecraft.getInstance().font, 0, 0, 130, 20, Component.empty());
            this.textField.setBordered(false);
            this.textField.setMaxLength(200);
            this.textField.setValue(entry.text != null ? entry.text : "");
            this.textField.setResponder(s -> {
                entry.text = s;
                markChanged();
            });
            this.weightField = new EditBox(Minecraft.getInstance().font, 0, 0, 45, 20, Component.empty());
            this.weightField.setBordered(false);
            this.weightField.setMaxLength(7);
            this.weightField.setValue(formatWeight(entry.weight));
            this.weightField.setResponder(s -> {
                String filtered = s.replaceAll("[^0-9.]", "");
                if (!filtered.equals(s)) {
                    this.weightField.setValue(filtered);
                    return;
                }
                try {
                    entry.weight = Mth.clamp(Float.parseFloat(s), 0f, 10000f);
                    markChanged();
                } catch (Exception ignored) {
                }
            });
            this.delBtn = new PlainTextButton(0, 0, 20, 20, Component.literal("-"), onDelete, 0xFFFFFFFF, 0xFFFBFB54);
            this.colorField = new EditBox(Minecraft.getInstance().font, 0, 0, 44, 20, Component.empty());
            this.colorField.setBordered(false);
            this.colorField.setMaxLength(7);
            this.colorField.setValue(String.format("%06X", currentColor & 0xFFFFFF));
            this.colorField.setResponder(s -> {
                String filtered = s.replaceAll("[^0-9a-fA-F]", "");
                if (!filtered.equals(s)) {
                    this.colorField.setValue(filtered);
                    return;
                }
                try {
                    String hex = filtered.startsWith("#") ? filtered.substring(1) : filtered;
                    currentColor = (int) Long.parseLong(hex, 16) | 0xFF000000;
                    entry.color = currentColor;
                    markChanged();
                } catch (Exception ignored) {
                }
            });
        }

        private String formatWeight(float weight) {
            return weight == (int) weight ? String.valueOf((int) weight) : String.valueOf(weight);
        }

        @Override
        public void renderContent(GuiGraphics g, int idx, int y, int x, int ew, int eh,
                                  int mx, int my, boolean hovered, float dt) {
            int rx = x + ew;
            delBtn.setX(rx - 25);
            delBtn.setY(y + 2);
            delBtn.setWidth(20);
            delBtn.setForceHover(hovered);
            int ps = 18;
            int bh = 20, by = y + (eh - bh) / 2;
            Component textLabel = Component.translatable("text.damage-engine.kill_text_content");
            Component weightLabel = Component.translatable("text.damage-engine.weight");
            int textLblW = Minecraft.getInstance().font.width(textLabel);
            int weightLblW = Minecraft.getInstance().font.width(weightLabel);
            int available = (rx - 25 - 5) - (x + 10);
            int fixed = textLblW + 4 + 10 + weightLblW + 4 + 8 + 4 + ps + 5;
            int inputW = Mth.clamp((available - fixed) / 3, 40, 90);
            int tx = x + 10 + textLblW + 4;
            int wlx = tx + inputW + 10;
            int wx = wlx + weightLblW + 4;
            int cbx = wx + inputW + 8;
            int px = cbx + inputW + 4;
            int py = y + 3;
            swatchX = px - 1;
            swatchY = py - 1;
            swatchSize = ps + 2;
            boolean overSwatch = mx >= swatchX && mx <= swatchX + swatchSize
                && my >= swatchY && my <= swatchY + swatchSize;
            g.drawString(Minecraft.getInstance().font, textLabel, x + 10, y + 8, 0xFFFFFFFF);
            g.drawString(Minecraft.getInstance().font, weightLabel, wlx, y + 8, 0xFFFFFFFF);
            textField.setX(tx + 4);
            textField.setY(by + 6);
            textField.setWidth(inputW - 8);
            textField.setHeight(12);
            weightField.setX(wx + 4);
            weightField.setY(by + 6);
            weightField.setWidth(inputW - 8);
            weightField.setHeight(12);
            colorField.setX(cbx + 4);
            colorField.setY(by + 6);
            colorField.setWidth(inputW - 8);
            colorField.setHeight(12);
            drawBox(g, tx, by, inputW, bh, textField, mx, my);
            drawBox(g, wx, by, inputW, bh, weightField, mx, my);
            drawBox(g, cbx, by, inputW, bh, colorField, mx, my);
            textField.render(g, mx, my, dt);
            weightField.render(g, mx, my, dt);
            colorField.render(g, mx, my, dt);
            delBtn.render(g, mx, my, dt);
            g.fill(swatchX, swatchY, swatchX + swatchSize, swatchY + swatchSize,
                overSwatch ? 0xFFB5F0C6 : 0xFFFFFFFF);
            g.fill(px, py, px + ps, py + ps, 0xFF000000 | (currentColor & 0xFFFFFF));
        }

        private void drawBox(GuiGraphics g, int bx, int by, int bw, int bh, EditBox f, int mx, int my) {
            g.fill(bx, by, bx + bw, by + bh, 0x20000000);
            int bc = (f.isFocused() || f.isMouseOver(mx, my)) ? 0xFFFFFFFF : 0xFFA0A0A0;
            g.fill(bx, by, bx + bw, by + 1, bc);
            g.fill(bx, by + bh - 1, bx + bw, by + bh, bc);
            g.fill(bx, by, bx + 1, by + bh, bc);
            g.fill(bx + bw - 1, by, bx + bw, by + bh, bc);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int btn) {
            if (btn == 0 && swatchSize > 0
                && mx >= swatchX && mx <= swatchX + swatchSize
                && my >= swatchY && my <= swatchY + swatchSize) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.screen instanceof ClickSoundHost host) host.playClickSound();
                Screen screen = mc.screen;
                if (screen != null) {
                    mc.setScreen(new ColorPickerScreen(screen,
                        Component.translatable("title.damage-engine.color_picker"), currentColor, c -> {
                        currentColor = c | 0xFF000000;
                        entry.color = currentColor;
                        colorField.setValue(String.format("%06X", currentColor & 0xFFFFFF));
                        markChanged();
                    }));
                }
                return true;
            }
            return super.mouseClicked(mx, my, btn);
        }

        @Override
        public Component getTooltipAt(int mx, int my) {
            if (weightField.isMouseOver(mx, my)) {
                return Component.translatable("hint.damage-engine.kill_text_weight");
            }
            if (colorField.isMouseOver(mx, my)) {
                return Component.translatable("option.damage-engine.killTextColor");
            }
            if (swatchSize > 0 && mx >= swatchX && mx <= swatchX + swatchSize
                && my >= swatchY && my <= swatchY + swatchSize) {
                return Component.translatable("option.damage-engine.killTextColor");
            }
            return null;
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return Arrays.asList(textField, weightField, colorField, delBtn);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return Arrays.asList(textField, weightField, colorField, delBtn);
        }
    }

    private static class RatingGradeEntry extends OptionEntry {
        private final EditBox scoreField, textField;
        private final PlainTextButton delBtn;

        public RatingGradeEntry(DamageEngineConfig.RatingGrade g, Runnable onDelete) {
            this.scoreField = new EditBox(Minecraft.getInstance().font, 0, 0, 60, 20, Component.empty());
            this.scoreField.setBordered(false);
            this.scoreField.setValue(String.format("%.0f", g.minScore));
            this.scoreField.setResponder(s -> {
                try {
                    g.minScore = Float.parseFloat(s);
                } catch (Exception ignored) {
                }
            });
            this.textField = new EditBox(Minecraft.getInstance().font, 0, 0, 60, 20, Component.empty());
            this.textField.setBordered(false);
            this.textField.setMaxLength(1000);
            this.textField.setValue(g.text);
            this.textField.setResponder(s -> g.text = s);
            this.delBtn = new PlainTextButton(0, 0, 20, 20, Component.literal("-"), onDelete, 0xFFFFFFFF, 0xFFFBFB54);
        }

        @Override
        public void renderContent(GuiGraphics g, int idx, int y, int x, int ew, int eh,
                                  int mx, int my, boolean hovered, float dt) {
            int rx = x + ew, bw = 60, bh = 20, by = y + (eh - bh) / 2;
            delBtn.setX(rx - 25);
            delBtn.setY(by);
            delBtn.setWidth(20);
            delBtn.setForceHover(hovered);
            int tbx = rx - 25 - 3 - bw;
            textField.setX(tbx + 4);
            textField.setY(by + 6);
            textField.setWidth(bw - 8);
            textField.setHeight(12);
            int sbx = tbx - 3 - bw;
            scoreField.setX(sbx + 4);
            scoreField.setY(by + 6);
            scoreField.setWidth(bw - 8);
            scoreField.setHeight(12);
            g.drawString(Minecraft.getInstance().font, Component.literal("≥"), x + 10, y + 8, 0xFFFFFFFF);
            drawBox(g, sbx, by, bw, bh, scoreField, mx, my);
            drawBox(g, tbx, by, bw, bh, textField, mx, my);
            scoreField.render(g, mx, my, dt);
            textField.render(g, mx, my, dt);
            delBtn.render(g, mx, my, dt);
        }

        private void drawBox(GuiGraphics g, int bx, int by, int bw, int bh, EditBox f, int mx, int my) {
            g.fill(bx, by, bx + bw, by + bh, 0x20000000);
            int bc = (f.isFocused() || f.isMouseOver(mx, my)) ? 0xFFFFFFFF : 0xFFA0A0A0;
            g.fill(bx, by, bx + bw, by + 1, bc);
            g.fill(bx, by + bh - 1, bx + bw, by + bh, bc);
            g.fill(bx, by, bx + 1, by + bh, bc);
            g.fill(bx + bw - 1, by, bx + bw, by + bh, bc);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return Arrays.asList(scoreField, textField, delBtn);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return Arrays.asList(scoreField, textField, delBtn);
        }
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
            this.grade = g;
            this.config = config;
            this.currentColor = g.color;
            this.colField = new EditBox(Minecraft.getInstance().font, 0, 0, 75, 20, Component.empty());
            this.colField.setBordered(false);
            this.colField.setMaxLength(6);
            this.colField.setValue(String.format("%06X", g.color & 0xFFFFFF));
            this.colField.setResponder(s -> {
                String filtered = s.replaceAll("[^0-9a-fA-F]", "");
                if (!filtered.equals(s)) {
                    this.colField.setValue(filtered);
                    return;
                }
                try {
                    String hex = s.startsWith("#") ? s.substring(1) : s;
                    currentColor = (int) Long.parseLong(hex, 16) | 0xFF000000;
                    g.color = currentColor;
                } catch (Exception ignored) {
                }
            });
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
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void onResetImage() {
            grade.imagePath = "";
        }

        @Override
        public void renderContent(GuiGraphics g, int idx, int y, int x, int ew, int eh,
                                  int mx, int my, boolean hovered, float dt) {
            int rx = x + ew, by = y + 2;
            if (config.ratingUseImages) {
                boolean hasImg = grade.imagePath != null && !grade.imagePath.isEmpty();
                selectImageBtn.setX(rx - 110);
                selectImageBtn.setY(by);
                selectImageBtn.setWidth(100);
                selectImageBtn.visible = true;
                resetImageBtn.visible = hasImg;
                if (hasImg) {
                    resetImageBtn.setX(rx - 110 - 25);
                    resetImageBtn.setY(by);
                    resetImageBtn.setWidth(20);
                }
                String pt = hasImg ? grade.imagePath : "Not set";
                if (pt.contains("/")) pt = pt.substring(pt.lastIndexOf("/") + 1);
                g.drawString(Minecraft.getInstance().font, Component.literal(grade.text), x + 10, y + 8, 0xFFFFFFFF);
                int mw = 80, tw = Minecraft.getInstance().font.width(pt);
                if (tw > mw) {
                    while (tw > mw - 20 && pt.length() > 4) {
                        pt = pt.substring(0, pt.length() - 1);
                        tw = Minecraft.getInstance().font.width(pt + "...");
                    }
                    pt += "...";
                }
                int px = rx - 110 - 5 - tw - (hasImg ? 25 : 0) - 5;
                if (px < x + 10) px = x + 10;
                g.drawString(Minecraft.getInstance().font, Component.literal(pt), px, y + 8, 0xFFFFFFFF);
                resetImageBtn.render(g, mx, my, dt);
                selectImageBtn.render(g, mx, my, dt);
            } else {
                selectImageBtn.visible = false;
                int sx = x + ew - 110, ps = 17, px = sx + 80, py = by + 1;
                swatchX = px - 1;
                swatchY = py - 1;
                swatchSize = ps + 2;
                boolean overSwatch = mx >= swatchX && mx <= swatchX + swatchSize && my >= swatchY && my <= swatchY + swatchSize;
                colField.setX(sx + 4);
                colField.setY(by + 6);
                colField.setWidth(75 - 8);
                colField.setHeight(12);
                colField.visible = true;
                g.drawString(Minecraft.getInstance().font, Component.literal(grade.text), x + 10, y + 8, 0xFFFFFFFF);
                drawBox(g, sx, by, 75, 20, colField, mx, my);
                colField.render(g, mx, my, dt);
                g.fill(swatchX, swatchY, swatchX + swatchSize, swatchY + swatchSize, overSwatch ? 0xFFB5F0C6 : 0xFFFFFFFF);
                g.fill(px, py, px + ps, py + ps, 0xFF000000 | (currentColor & 0xFFFFFF));
            }
        }

        @Override
        public boolean mouseClicked(double mx, double my, int btn) {
            if (!config.ratingUseImages && btn == 0 && swatchSize > 0
                && mx >= swatchX && mx <= swatchX + swatchSize
                && my >= swatchY && my <= swatchY + swatchSize) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.screen instanceof ClickSoundHost host) host.playClickSound();
                Screen screen = mc.screen;
                if (screen != null) {
                    mc.setScreen(new ColorPickerScreen(screen,
                        Component.translatable("title.damage-engine.color_picker"), currentColor, c -> {
                        currentColor = c | 0xFF000000;
                        grade.color = currentColor;
                        colField.setValue(String.format("%06X", currentColor & 0xFFFFFF));
                    }));
                }
                return true;
            }
            return super.mouseClicked(mx, my, btn);
        }

        private void drawBox(GuiGraphics g, int bx, int by, int bw, int bh, EditBox f, int mx, int my) {
            g.fill(bx, by, bx + bw, by + bh, 0x20000000);
            int bc = (f.isFocused() || f.isMouseOver(mx, my)) ? 0xFFFFFFFF : 0xFFA0A0A0;
            g.fill(bx, by, bx + bw, by + 1, bc);
            g.fill(bx, by + bh - 1, bx + bw, by + bh, bc);
            g.fill(bx, by, bx + 1, by + bh, bc);
            g.fill(bx + bw - 1, by, bx + bw, by + bh, bc);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            if (config.ratingUseImages) {
                List<GuiEventListener> l = new ArrayList<>();
                l.add(selectImageBtn);
                if (resetImageBtn.visible) l.add(resetImageBtn);
                return l;
            } else {
                return Collections.singletonList(colField);
            }
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            if (config.ratingUseImages) {
                List<NarratableEntry> l = new ArrayList<>();
                l.add(selectImageBtn);
                if (resetImageBtn.visible) l.add(resetImageBtn);
                return l;
            } else {
                return Collections.singletonList(colField);
            }
        }
    }
}
