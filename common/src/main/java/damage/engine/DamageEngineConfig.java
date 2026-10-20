package damage.engine;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class DamageEngineConfig {
    
    private static final Path CONFIG_DIR = Path.of("config", "damage-engine");
    private static final Path PROFILES_DIR = CONFIG_DIR.resolve("profiles");
    private static final DamageEngineConfig INSTANCE = new DamageEngineConfig();
    
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // ========== General ==========
    public boolean showDamage = true;
    /**
     * 减少动画：关闭/简化纯装饰性的逐帧动画（HUD 惯性、伤害记录滑入、评分滚动、
     * 血条平滑、跳字逐字动画与漂移等），用于在低配机器或高负载战斗时换回帧率。
     * 关闭时保持原有表现不变。
     */
    public boolean reduceAnimations = false;
    /**
     * 整个 DE HUD 的独立帧率上限（FPS）。0 = 跟随游戏帧率（不限制）；
     * 大于 0 时 HUD 的动画/状态更新不超过该频率，HUD 本身仍然每帧绘制。
     */
    public int hudMaxFps = 60;
    /**
     * 伤害跳字的独立帧率上限（FPS）。0 = 跟随游戏帧率（不限制）；
     * 大于 0 时跳字的动画/属性更新不超过该频率，文字本身仍然每帧绘制。
     */
    public int indicatorMaxFps = 0;
    public boolean hideOnF1 = true;
    /** HUD 惯性:off = 关闭, de_only = 只让 DE 自己的 HUD 让位, all = 整层 HUD 一起让位。 */
    public String hudInertiaMode = "de_only";
    /** HUD 惯性强度(百分比),100 = 默认幅度。 */
    public int hudInertiaStrength = 100;
    public boolean numberSeparator = true; // true = "1,000", false = "1000"
    public boolean abbreviateNumbers = false; // true = "1.5K", false = "1,500"
    public boolean showDamageOverflow = false; // true = 显示未被目标血量截断的伤害
    
    // ========== Damage Display ==========
    public boolean showDamageDisplay = true;
    /** 总伤害与伤害记录的对齐方式:right = 靠右(数字右边缘对齐), left = 靠左。 */
    public String alignMode = "right";
    public int decimalPlaces = 1;
    public int normalColor = 0xFFFFFFFF;
    public int critColor = 0xFFD700;
    public boolean resetEnabled = true;
    public float resetTime = 10.0f;
    public boolean showProgressBar = true;
    public int progressBarColor = 0xFFFFFFFF;
    public boolean showCombo = true;
    public int comboColor = 0xFFAA00;
    public boolean showDamageHistory = true;
    public boolean recordOtherPlayers = false;
    public boolean shareDamage = true;
    public float historyDisappearanceTime = 10.0f;
    public int historyLimit = 20;
    public int historyDecimalPlaces = 1;
    
    // ========== Damage Indicator ==========
    public boolean showDamageIndicator = true;
    public int indicatorDecimalPlaces = 1;
    public String indicatorMode = "enhanced"; // "enhanced" or "cloud"
    public float indicatorScale = 0.8f;
    public int indicatorOpacity = 100;
    public boolean indicatorBold = false;
    /** 同时存在的跳字数量上限；0 = 无限制，超过时移除最旧的跳字。 */
    public int indicatorMaxCount = 0;
    public boolean showHealIndicator = false;
    public int healIndicatorColor = 0xFFB1EAC2;
    public boolean indicatorPrefixSign = false;
    public boolean showGlobalDamageIndicator = false;
    // 合并跳字:把同一目标短时间内受到的伤害合并成一个数字(默认关)
    public boolean indicatorMerge = false;
    // 追踪模式:off = 停在命中点, entity = 跟随受害实体, crosshair = 跟随准心
    public String indicatorTrackMode = "off";
    // 旧版「追踪实体」开关:只用于读取旧配置,逻辑已改用 indicatorTrackMode
    public boolean indicatorTrackEntity = false;
    // 全局伤害跳字 - 玩家显示距离(0 = 无限制)
    public float globalIndicatorMaxDistance = 128.0f;
    // 感知过滤:看不见且听不见该生物时,隐藏其受到的伤害跳字
    public boolean globalIndicatorSmartHide = false;
    // 全局伤害跳字 - 非玩家实体整体模式(false=显示/白名单, true=屏蔽/黑名单)
    public boolean globalEntityBlockMode = false;
    // 全局伤害跳字 - 非玩家实体规则(注册名 All 表示全体,与具体注册名互斥)
    public List<GlobalEntityRule> globalEntityRules = new ArrayList<>();
    public String killText = "Kill!";
    public int killTextColor = 0xFFF9867D;
    public boolean showKillIndicator = true;
    public int damageIndicatorNormalColor = 0xFFFFFFFF;
    public int damageIndicatorCritColor = 0xFFD700;
    public int damageIndicatorKillColor = 0xFFFC887E;
    
    // ========== Entity Info ==========
    public boolean showInfo = true;
    public float infoTrackTime = 15.0f;
    // 实体信息面板是否渲染生物实体(3D 模型)
    public boolean entityRenderEnabled = true;
    // 生物实体渲染朝向:"follow" = 跟随实际朝向(以玩家视角为基准),"custom" = 按自定义角度
    public String entityRenderRotation = "custom";
    // 自定义朝向角度(0~360,0 = 正面朝向观察者,角度增大为顺时针)
    public int entityRenderRotationAngle = 30;
    // 准心指向生物时在信息面板显示该生物(按原版实体交互距离)
    public boolean crosshairEntityInfo = false;
    public boolean infoNoRoundedBorder = false;
    public int infoBackgroundColor = 0xFF000000;
    public int infoBackgroundOpacity = 25;
    public int infoBarColor = 0xFFB1EAC2;
    public int infoBarHealColor = 0xFFB1EAC2;
    public int infoBarDamageColor = 0xFFF9867D;
    
    // ========== Rating ==========
    // 评分等级默认关闭（分数显示仍保持默认开启）
    public boolean showRating = false;
    // 评分分数显示(类似 COD 的加分提示)
    public boolean showRatingScore = true;
    // 加分时是否显示加分项目名称(连击/暴击/伤害加成等)
    public boolean showRatingScoreSource = true;
    // 加分提示同时显示的最大条数,超出后最旧的条目不再显示
    public int ratingScoreMaxGains = 5;
    // 评分总分的颜色
    public int ratingScoreColor = 0xFFB1EAC2;
    public float ratingResetTime = 20f;
    // 连击在该时间内没有新命中则重置（秒）
    public float comboResetTime = 10f;
    // 加分规则(内置 + 自定义)，表达式支持 {combo} {crit} {damage} {kill} {unseen} {meter} 等
    public List<BonusRule> bonusRules = new ArrayList<>();
    public boolean ratingUseImages = false;
    public List<RatingGrade> ratingGrades = new ArrayList<>();
    
    // ========== Debug ==========
    public boolean debugMode = false;
    public boolean debugShowDamageInfo = false;
    /** 屏幕左上角显示 DE 性能叠加层（HUD FPS / 跳字 FPS / 活跃数量等）。 */
    public boolean debugShowPerformance = false;
    
    // ========== Update Check ==========
    public boolean checkUpdate = true;
    
    // ========== Preview ==========
    public boolean previewEnabled = false;
    public boolean hasShownWelcomeMessage = false;
    
    // ========== Module Configs ==========
    public ModuleConfig totalDamageConfig = new ModuleConfig(0.8171875f, 0.38462415f, 0.95652175f);
    public ModuleConfig comboConfig = new ModuleConfig(0.8828125f, 0.2611276f, 0.95652175f);
    public ModuleConfig historyConfig = new ModuleConfig(0.9359375f, 0.3305973f, 0.95652175f);
    public ModuleConfig infoConfig = new ModuleConfig(0.6359375f, 0.38017642f, 0.5539445f);
    public ModuleConfig ratingConfig = new ModuleConfig(0.7867187f, 0.31508327f, 1.4066461f);
    public ModuleConfig ratingScoreConfig = new ModuleConfig(0.5953125f, 0.43195266f, 1.0f);

    /**
     * HUD 布局基准尺寸（GUI 像素），在 HUD 编辑器保存时记录。
     * 窗口缩放时整套 HUD（位置 + 尺寸）按同一比例缩放，控件之间的相对位置保持不变。
     * 0 = 尚未记录，按当前窗口 1:1 处理。
     */
    public float hudLayoutBaseWidth = 0f;
    public float hudLayoutBaseHeight = 0f;
    
    public List<DamageThreshold> damageThresholds = new ArrayList<>();

    // ========== Inner Classes ==========

    public static class ModuleConfig {
        public float x;
        public float y;
        public float scale;
        public boolean enabled;

        public ModuleConfig() {
            this.enabled = true;
        }

        /**
         * 屏幕横坐标：始终按屏幕比例定位。
         * 所有模块共用同一套比例坐标，窗口缩放时它们之间的相对位置不会漂移。
         */
        public int resolveScreenX(int guiWidth) {
            if (x == -1.0f) return guiWidth / 2;
            return Math.round(x * guiWidth);
        }

        public int resolveScreenY(int guiHeight) {
            if (y == -1.0f) return guiHeight / 2;
            return Math.round(y * guiHeight);
        }

        public ModuleConfig(float x, float y, float scale) {
            this.x = x;
            this.y = y;
            this.scale = scale;
            this.enabled = true;
        }
    }

    public static class DamageThreshold {
        public float threshold;
        public int color;
        
        public DamageThreshold() {
        }
        
        public DamageThreshold(float threshold, int color) {
            this.threshold = threshold;
            this.color = color;
        }
    }

    /**
     * 一条加分规则。
     * <p>expression 支持占位符与 + - * / 和括号，例如 {@code {kill} * 50}、{@code {damage:1}}。</p>
     */
    public static class BonusRule {
        /** 唯一标识；内置规则使用固定 id。 */
        public String id = "";
        /** 显示名称；内置规则留空时使用语言文件中的名称。 */
        public String name = "";
        /** 计分表达式。 */
        public String expression = "0";
        public boolean enabled = true;
        public boolean builtin = false;
        /** 持续项：不产生浮动提示，累计值常驻显示在评分界面。 */
        public boolean continuous = false;

        public BonusRule() {
        }

        public BonusRule(String id, String name, String expression, boolean builtin, boolean continuous) {
            this.id = id;
            this.name = name;
            this.expression = expression;
            this.builtin = builtin;
            this.continuous = continuous;
        }
    }

    /** 默认加分规则。 */
    public static List<BonusRule> defaultBonusRules() {
        List<BonusRule> list = new ArrayList<>();
        list.add(new BonusRule("combo", "", "{combo,time:combo_time} * 0.2", true, true));
        list.add(new BonusRule("killstreak", "", "{killstreak,time:20000} * 10", true, true));
        list.add(new BonusRule("crit", "", "{crit:5}", true, false));
        list.add(new BonusRule("damage", "", "{damage:1}", true, true));
        list.add(new BonusRule("kill", "", "{kill:25}", true, false));
        list.add(new BonusRule("unseen", "", "{unseen:20}", true, false));
        list.add(new BonusRule("distance", "", "{kill_meter} > 5 ? {kill_meter} * 2 : 0", true, false));
        return list;
    }

    /** 旧版本默认表达式迁移到新格式，用户自定义过的值保持不动。 */
    private void migrateBonusRules() {
        if (bonusRules == null) return;
        for (BonusRule rule : bonusRules) {
            if (rule == null || !rule.builtin) continue;
            String expr = rule.expression == null ? "" : rule.expression.trim();
            switch (rule.id) {
                case "combo" -> {
                    if (expr.equals("{combo} * 2") || expr.equals("{combo} * 0.2")) {
                        rule.expression = "{combo,time:combo_time} * 0.2";
                    }
                    rule.continuous = true;
                }
                case "crit" -> {
                    if (expr.equals("{crit} * 5")) rule.expression = "{crit:5}";
                }
                case "kill" -> {
                    if (expr.equals("{kill} * 50")) rule.expression = "{kill:25}";
                }
                case "unseen" -> {
                    if (expr.equals("{unseen} * 20")) rule.expression = "{unseen:20}";
                }
                case "killstreak" -> {
                    if (expr.equals("{连杀} * 10") || expr.equals("{killstreak} * 10")) {
                        rule.expression = "{killstreak,time:20000} * 10";
                    }
                }
                case "distance" -> {
                    if (expr.equals("{kill_meter}") || expr.equals("{kill_meter} * 2")
                        || expr.equals("{kill} * {meter} * 5")) {
                        rule.expression = "{kill_meter} > 5 ? {kill_meter} * 2 : 0";
                    }
                }
                default -> {
                }
            }
        }
        // 旧配置没有“连杀”时补上默认项
        boolean hasKillstreak = false;
        for (BonusRule rule : bonusRules) {
            if (rule != null && "killstreak".equals(rule.id)) {
                hasKillstreak = true;
                rule.continuous = true;
                break;
            }
        }
        if (!hasKillstreak) {
            bonusRules.add(new BonusRule("killstreak", "", "{killstreak,time:20000} * 10", true, true));
        }
    }

    /**
     * 全局伤害跳字 - 非玩家实体规则。
     * registryName 为实体注册名;特殊值 "All" 表示全体实体(与具体注册名项互斥,存在 All 时不可再添加)。
     * distance 为显示距离(0 = 无限制)。整体 显示/屏蔽 由 {@link #globalEntityBlockMode} 控制。
     */
    public static class GlobalEntityRule {
        public String registryName = "";
        public float distance = 0f;

        public GlobalEntityRule() {
        }

        public GlobalEntityRule(String registryName, float distance) {
            this.registryName = registryName;
            this.distance = distance;
        }

        public boolean isAll() {
            return registryName != null && "All".equalsIgnoreCase(registryName);
        }
    }
    
    public static class RatingGrade {
        public float minScore;
        public String text;
        public int color;
        public int index;
        public String imagePath;
        
        public RatingGrade() {
            this.index = 0;
            this.imagePath = "";
        }
        
        public RatingGrade(float minScore, String text, int color) {
            this(minScore, text, color, 0);
        }
        
        public RatingGrade(float minScore, String text, int color, int index) {
            this.minScore = minScore;
            this.text = text;
            this.color = color;
            this.index = index;
            this.imagePath = "";
        }
    }

    private DamageEngineConfig() {
        damageThresholds.add(new DamageThreshold(0f, 0xFFFFFFFF));
        damageThresholds.add(new DamageThreshold(25f, 0x3FA9F0));
        damageThresholds.add(new DamageThreshold(50f, 0xFC54FC));
        damageThresholds.add(new DamageThreshold(100f, 0xFFD700));
        
        ratingGrades.add(new RatingGrade(1000f, "S", 0xFFD700, 1));
        ratingGrades.add(new RatingGrade(500f, "A", 0xFF6347, 2));
        ratingGrades.add(new RatingGrade(250f, "B", 0x00BFFF, 3));
        ratingGrades.add(new RatingGrade(100f, "C", 0x32CD32, 4));
        ratingGrades.add(new RatingGrade(20f, "D", 0xA0A0A0, 5));

        // 全局伤害跳字 - 非玩家实体默认包含一项 All(全体实体,距离 0 = 无限制)
        globalEntityRules.add(new GlobalEntityRule("All", 0f));
    }

    public static DamageEngineConfig getInstance() {
        return INSTANCE;
    }

    // ---- HUD 布局：基准尺寸 + 统一缩放，保证窗口变化时控件相对位置不变 ----

    /** 当前窗口相对布局基准的整体缩放系数（以高度为准，避免窗口变宽时横向距离被拉伸）。 */
    public float hudLayoutScale(int guiHeight) {
        if (hudLayoutBaseHeight <= 0f) return 1f;
        return guiHeight / hudLayoutBaseHeight;
    }

    /** 布局基准宽度；未记录时退回当前宽度（等价 1:1）。 */
    public float hudBaseWidth(int guiWidth) {
        return hudLayoutBaseWidth > 0f ? hudLayoutBaseWidth : guiWidth;
    }

    public int resolveHudX(ModuleConfig m, int guiWidth, int guiHeight) {
        return Math.round(m.x * hudBaseWidth(guiWidth) * hudLayoutScale(guiHeight));
    }

    public int resolveHudY(ModuleConfig m, int guiHeight) {
        return m.resolveScreenY(guiHeight);
    }

    /** 模块最终缩放：用户设定的缩放 × 整体布局缩放。 */
    public float resolveHudScale(ModuleConfig m, int guiHeight) {
        return m.scale * hudLayoutScale(guiHeight);
    }

    /**
     * 全局伤害跳字 - 解析受害实体的显示距离(分项设置)。
     * <ul>
     *   <li>受害者为玩家:返回 {@link #globalIndicatorMaxDistance}(玩家显示距离,0 = 无限制)</li>
     *   <li>受害者为非玩家实体:由 {@link #globalEntityBlockMode} 与 {@link #globalEntityRules} 决定,
     *       显示模式(白名单)仅列表内实体(具体注册名或 All)显示;屏蔽模式(黑名单)列表内实体屏蔽,
     *       其余实体显示(距离取 All 项,无 All 时默认 {@link #DEFAULT_NON_PLAYER_DISTANCE});
     *       返回 null 表示不显示</li>
     * </ul>
     */
    public static final float DEFAULT_NON_PLAYER_DISTANCE = 128f;

    public Float resolveGlobalIndicatorDistance(Entity victim) {
        if (victim instanceof Player) {
            return globalIndicatorMaxDistance;
        }
        if (globalEntityRules == null || globalEntityRules.isEmpty()) {
            return null; // 未配置任何规则:不显示非玩家实体跳字
        }
        String regName = null;
        try {
            net.minecraft.resources.Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType());
            regName = key == null ? null : key.toString();
        } catch (Exception ignored) {
            regName = null;
        }
        GlobalEntityRule allRule = null;
        GlobalEntityRule match = null;
        for (GlobalEntityRule r : globalEntityRules) {
            if (r == null || r.registryName == null || r.registryName.trim().isEmpty()) continue;
            if (r.isAll()) {
                allRule = r;
                continue;
            }
            if (regName != null && r.registryName.trim().equalsIgnoreCase(regName)) {
                match = r;
            }
        }
        GlobalEntityRule effective = match != null ? match : allRule;
        if (!globalEntityBlockMode) {
            // 显示模式(白名单):仅列表内实体(具体或 All)显示
            return effective != null ? effective.distance : null;
        }
        // 屏蔽模式(黑名单):列表内实体屏蔽;其余实体显示,距离取 All 项,无 All 时用默认距离
        if (effective != null) {
            return null;
        }
        return allRule != null ? allRule.distance : DEFAULT_NON_PLAYER_DISTANCE;
    }

    public static Path getProfilesDir() {
        return PROFILES_DIR;
    }

    public static List<String> getProfileNames() {
        List<String> names = new ArrayList<>();
        try {
            if (!Files.exists(PROFILES_DIR)) {
                Files.createDirectories(PROFILES_DIR);
            }
            File[] files = PROFILES_DIR.toFile().listFiles((dir, name) -> name.endsWith(".json"));
            if (files != null) {
                for (File f : files) {
                    names.add(f.getName().replace(".json", ""));
                }
            }
        } catch (Exception ignored) {}
        if (names.isEmpty()) {
            names.add("default");
        }
        return names;
    }

    private static Path configPath;
    private static String currentProfile = "default";

    public static String getCurrentProfile() {
        return currentProfile;
    }

    public static void setCurrentProfile(String profile) {
        currentProfile = profile;
        configPath = PROFILES_DIR.resolve(profile + ".json");
    }

    private static Path getConfigPath() {
        if (configPath == null) {
            configPath = PROFILES_DIR.resolve("default.json");
        }
        return configPath;
    }

    public void load() {
        load(null);
    }

    public void load(String profile) {
        try {
            if (!Files.exists(CONFIG_DIR)) {
                Files.createDirectories(CONFIG_DIR);
            }
            if (!Files.exists(PROFILES_DIR)) {
                Files.createDirectories(PROFILES_DIR);
            }
            
            Path path;
            if (profile != null) {
                setCurrentProfile(profile);
            }
            path = getConfigPath();
            
            // Migrate old config if exists
            Path oldConfig = CONFIG_DIR.resolve("config.json");
            if (Files.exists(oldConfig) && !Files.exists(path)) {
                Files.move(oldConfig, path);
            }
            
            if (!Files.exists(path)) {
                save();
                return;
            }
            
            try (Reader reader = Files.newBufferedReader(path)) {
                JsonObject raw = JsonParser.parseReader(reader).getAsJsonObject();
                DamageEngineConfig loaded = GSON.fromJson(raw, DamageEngineConfig.class);
                if (loaded != null) {
                    copyFrom(loaded);
                    // 旧配置没有该字段：迁移到新的默认值 60（显式写过 0 的保持不变）
                    if (!raw.has("hudMaxFps")) {
                        this.hudMaxFps = 60;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void copyFrom(DamageEngineConfig loaded) {
        // General
        this.showDamage = loaded.showDamage;
        this.reduceAnimations = loaded.reduceAnimations;
        this.hudMaxFps = Math.max(0, loaded.hudMaxFps);
        this.indicatorMaxFps = Math.max(0, loaded.indicatorMaxFps);
        this.hideOnF1 = loaded.hideOnF1;
        this.hudInertiaMode = loaded.hudInertiaMode != null ? loaded.hudInertiaMode : "de_only";
        this.hudInertiaStrength = loaded.hudInertiaStrength > 0 ? loaded.hudInertiaStrength : 100;
        this.numberSeparator = loaded.numberSeparator;
        this.abbreviateNumbers = loaded.abbreviateNumbers;
        this.showDamageOverflow = loaded.showDamageOverflow;
        
        // Damage Display
        this.showDamageDisplay = loaded.showDamageDisplay;
        this.alignMode = loaded.alignMode != null ? loaded.alignMode : "right";
        this.decimalPlaces = loaded.decimalPlaces;
        this.normalColor = loaded.normalColor;
        this.critColor = loaded.critColor;
        this.resetEnabled = loaded.resetEnabled;
        this.resetTime = loaded.resetTime;
        this.showProgressBar = loaded.showProgressBar;
        this.progressBarColor = loaded.progressBarColor;
        this.showCombo = loaded.showCombo;
        this.comboColor = loaded.comboColor;
        this.showDamageHistory = loaded.showDamageHistory;
        this.recordOtherPlayers = loaded.recordOtherPlayers;
        this.shareDamage = loaded.shareDamage;
        this.historyDisappearanceTime = loaded.historyDisappearanceTime;
        this.historyLimit = loaded.historyLimit;
        this.historyDecimalPlaces = loaded.historyDecimalPlaces;
        
        // Damage Indicator
        this.showDamageIndicator = loaded.showDamageIndicator;
        this.indicatorDecimalPlaces = loaded.indicatorDecimalPlaces;
        this.indicatorMode = loaded.indicatorMode != null ? loaded.indicatorMode : "enhanced";
        this.indicatorScale = loaded.indicatorScale > 0 ? loaded.indicatorScale : 1.5f;
        this.indicatorOpacity = loaded.indicatorOpacity;
        this.indicatorBold = loaded.indicatorBold;
        this.indicatorMaxCount = Math.max(0, loaded.indicatorMaxCount);
        this.showHealIndicator = loaded.showHealIndicator;
        this.healIndicatorColor = loaded.healIndicatorColor;
        this.indicatorPrefixSign = loaded.indicatorPrefixSign;
        this.showGlobalDamageIndicator = loaded.showGlobalDamageIndicator;
        this.indicatorMerge = loaded.indicatorMerge;
        // 旧配置只有布尔开关：true 迁移为「跟随实体」，false 为「关闭」
        this.indicatorTrackMode = loaded.indicatorTrackMode != null && !loaded.indicatorTrackMode.isEmpty()
            ? loaded.indicatorTrackMode
            : (loaded.indicatorTrackEntity ? "entity" : "off");
        this.globalIndicatorSmartHide = loaded.globalIndicatorSmartHide;
        this.globalEntityBlockMode = loaded.globalEntityBlockMode;
        if (loaded.globalEntityRules != null) {
            this.globalEntityRules = loaded.globalEntityRules;
        }
        // 默认包含一项 All(全体实体):旧配置为空列表时自动补默认项,避免非玩家跳字空规则不显示
        if (this.globalEntityRules == null || this.globalEntityRules.isEmpty()) {
            this.globalEntityRules = new ArrayList<>();
            this.globalEntityRules.add(new GlobalEntityRule("All", 0f));
        }
        // 0 = unlimited is a valid configured value (see hint text); keep it as-is
        // instead of forcing the 128 default back, which made "set 0 for unlimited"
        // silently reset to the default on reload.
        this.globalIndicatorMaxDistance = loaded.globalIndicatorMaxDistance;
        this.killText = loaded.killText != null ? loaded.killText : "Kill!";
        this.killTextColor = loaded.killTextColor;
        this.showKillIndicator = loaded.showKillIndicator;
        this.damageIndicatorNormalColor = loaded.damageIndicatorNormalColor;
        this.damageIndicatorCritColor = loaded.damageIndicatorCritColor;
        this.damageIndicatorKillColor = loaded.damageIndicatorKillColor;
        
        // Entity Info
        this.showInfo = loaded.showInfo;
        this.infoTrackTime = loaded.infoTrackTime;
        this.entityRenderEnabled = loaded.entityRenderEnabled;
        // 旧版本写下的 "fixed" 与现在的 "custom" 同义(都是"不跟随、按固定角度")
        this.entityRenderRotation = ("custom".equals(loaded.entityRenderRotation) || "fixed".equals(loaded.entityRenderRotation)) ? "custom" : "follow";
        this.entityRenderRotationAngle = loaded.entityRenderRotationAngle;
        this.crosshairEntityInfo = loaded.crosshairEntityInfo;
        this.infoNoRoundedBorder = loaded.infoNoRoundedBorder;
        this.infoBackgroundColor = loaded.infoBackgroundColor;
        this.infoBackgroundOpacity = loaded.infoBackgroundOpacity;
        this.infoBarColor = loaded.infoBarColor;
        this.infoBarHealColor = loaded.infoBarHealColor;
        this.infoBarDamageColor = loaded.infoBarDamageColor;
        
        // Rating
        this.showRating = loaded.showRating;
        this.showRatingScore = loaded.showRatingScore;
        this.showRatingScoreSource = loaded.showRatingScoreSource;
        this.ratingScoreMaxGains = Math.max(1, Math.min(10, loaded.ratingScoreMaxGains));
        // 旧默认 10 秒迁移到新的 20 秒；用户其它取值保持不动
        this.ratingResetTime = Math.abs(loaded.ratingResetTime - 10f) < 0.001f
            ? 20f : loaded.ratingResetTime;
        this.comboResetTime = loaded.comboResetTime > 0f ? loaded.comboResetTime : 10f;
        this.ratingUseImages = loaded.ratingUseImages;
        this.bonusRules = (loaded.bonusRules != null && !loaded.bonusRules.isEmpty())
            ? loaded.bonusRules : defaultBonusRules();
        migrateBonusRules();
        if (loaded.ratingGrades != null && !loaded.ratingGrades.isEmpty()) {
            this.ratingGrades = loaded.ratingGrades;
            for (int i = 0; i < this.ratingGrades.size(); i++) {
                this.ratingGrades.get(i).index = i + 1;
            }
        }
        
        // Debug
        this.debugMode = loaded.debugMode;
        this.debugShowDamageInfo = loaded.debugShowDamageInfo;
        this.debugShowPerformance = loaded.debugShowPerformance;
        
        // Update Check
        this.checkUpdate = loaded.checkUpdate;
        
        // Preview
        this.previewEnabled = loaded.previewEnabled;
        this.hasShownWelcomeMessage = loaded.hasShownWelcomeMessage;
        
        // Module configs
        if (loaded.totalDamageConfig != null) this.totalDamageConfig = loaded.totalDamageConfig;
        if (loaded.comboConfig != null) this.comboConfig = loaded.comboConfig;
        if (loaded.historyConfig != null) this.historyConfig = loaded.historyConfig;
        if (loaded.infoConfig != null) this.infoConfig = loaded.infoConfig;
        if (loaded.ratingConfig != null) this.ratingConfig = loaded.ratingConfig;
        if (loaded.ratingScoreConfig != null) this.ratingScoreConfig = loaded.ratingScoreConfig;

        this.hudLayoutBaseWidth = loaded.hudLayoutBaseWidth;
        this.hudLayoutBaseHeight = loaded.hudLayoutBaseHeight;
        
        if (loaded.damageThresholds != null) {
            this.damageThresholds = loaded.damageThresholds;
        }
    }

    public void save() {
        try {
            if (!Files.exists(CONFIG_DIR)) {
                Files.createDirectories(CONFIG_DIR);
            }
            if (!Files.exists(PROFILES_DIR)) {
                Files.createDirectories(PROFILES_DIR);
            }
            try (Writer writer = Files.newBufferedWriter(getConfigPath())) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public void resetToDefaults() {
        applyHardcodedDefaults();
        save();
    }
    
    private void applyHardcodedDefaults() {
        // General
        showDamage = true;
        reduceAnimations = false;
        hudMaxFps = 60;
        indicatorMaxFps = 0;
        hideOnF1 = true;
        hudInertiaMode = "de_only";
        hudInertiaStrength = 100;
        numberSeparator = true;
        abbreviateNumbers = false;
        showDamageOverflow = false;
        
        // Damage Display
        showDamageDisplay = true;
        alignMode = "right";
        decimalPlaces = 1;
        normalColor = 0xFFFFFFFF;
        critColor = 0xFFD700;
        resetEnabled = true;
        resetTime = 10.0f;
        showProgressBar = true;
        progressBarColor = 0xFFFFFFFF;
        showCombo = true;
        comboColor = 0xFFAA00;
        showDamageHistory = true;
        recordOtherPlayers = false;
        shareDamage = true;
        historyDisappearanceTime = 10.0f;
        historyLimit = 20;
        historyDecimalPlaces = 1;
        
        // Damage Indicator
        showDamageIndicator = true;
        indicatorDecimalPlaces = 1;
        indicatorMode = "enhanced";
        indicatorScale = 0.8f;
        indicatorOpacity = 100;
        indicatorBold = false;
        indicatorMaxCount = 0;
        showHealIndicator = false;
        healIndicatorColor = 0xFFB1EAC2;
        indicatorPrefixSign = false;
        showGlobalDamageIndicator = false;
        indicatorMerge = false;
        indicatorTrackMode = "off";
        indicatorTrackEntity = false;
        globalIndicatorMaxDistance = 128.0f;
        globalIndicatorSmartHide = false;
        globalEntityBlockMode = false;
        globalEntityRules.clear();
        // 重置后默认包含一项 All(全体实体,距离 0 = 无限制)
        globalEntityRules.add(new GlobalEntityRule("All", 0f));
        killText = "Kill!";
        killTextColor = 0xFFF9867D;
        showKillIndicator = true;
        damageIndicatorNormalColor = 0xFFFFFFFF;
        damageIndicatorCritColor = 0xFFD700;
        damageIndicatorKillColor = 0xFFFC887E;
        
        // Entity Info
        showInfo = true;
        infoTrackTime = 15.0f;
        entityRenderEnabled = true;
        entityRenderRotation = "custom";
        entityRenderRotationAngle = 30;
        crosshairEntityInfo = false;
        infoNoRoundedBorder = false;
        infoBackgroundColor = 0xFF000000;
        infoBackgroundOpacity = 25;
        infoBarColor = 0xFFB1EAC2;
        infoBarHealColor = 0xFFB1EAC2;
        infoBarDamageColor = 0xFFF9867D;
        
        // Rating
        showRating = false;
        showRatingScore = true;
        showRatingScoreSource = true;
        ratingScoreMaxGains = 5;
        ratingScoreColor = 0xFFB1EAC2;
        ratingResetTime = 20f;
        comboResetTime = 10f;
        ratingUseImages = false;
        bonusRules.clear();
        bonusRules.addAll(defaultBonusRules());
        ratingGrades.clear();
        ratingGrades.add(new RatingGrade(1000f, "S", 0xFFD700, 1));
        ratingGrades.add(new RatingGrade(500f, "A", 0xFF6347, 2));
        ratingGrades.add(new RatingGrade(250f, "B", 0x00BFFF, 3));
        ratingGrades.add(new RatingGrade(100f, "C", 0x32CD32, 4));
        ratingGrades.add(new RatingGrade(20f, "D", 0xA0A0A0, 5));
        
        // Debug
        debugMode = false;
        debugShowDamageInfo = false;
        debugShowPerformance = false;
        
        // Update Check
        checkUpdate = true;
        
        // Preview
        previewEnabled = false;
        
        // Module configs
        totalDamageConfig = new ModuleConfig(0.8171875f, 0.38462415f, 0.95652175f);
        comboConfig = new ModuleConfig(0.8828125f, 0.2611276f, 0.95652175f);
        historyConfig = new ModuleConfig(0.9359375f, 0.3305973f, 0.95652175f);
        infoConfig = new ModuleConfig(0.6359375f, 0.38017642f, 0.5539445f);
        ratingConfig = new ModuleConfig(0.7867187f, 0.31508327f, 1.4066461f);
        ratingScoreConfig = new ModuleConfig(0.5953125f, 0.43195266f, 1.0f);
        hudLayoutBaseWidth = 0f;
        hudLayoutBaseHeight = 0f;
        
        damageThresholds.clear();
        damageThresholds.add(new DamageThreshold(0f, 0xFFFFFFFF));
        damageThresholds.add(new DamageThreshold(25f, 0x3FA9F0));
        damageThresholds.add(new DamageThreshold(50f, 0xFC54FC));
        damageThresholds.add(new DamageThreshold(100f, 0xFFD700));
    }
}