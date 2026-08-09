package damage.engine;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class DamageEngineConfig {
    
    private static final Path CONFIG_DIR = Path.of("config", "damage-engine");
    private static final Path PROFILES_DIR = CONFIG_DIR.resolve("profiles");
    private static final Path CONFIG_PATH = PROFILES_DIR.resolve("default.json");
    private static final DamageEngineConfig INSTANCE = new DamageEngineConfig();
    
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // ========== General ==========
    public boolean showDamage = true;
    public boolean hideOnF1 = true;
    public boolean numberSeparator = true; // true = "1,000", false = "1000"
    
    // ========== Damage Display ==========
    public boolean showDamageDisplay = true;
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
    public boolean showHealIndicator = false;
    public int healIndicatorColor = 0xFFB1EAC2;
    public boolean indicatorPrefixSign = false;
    public boolean showGlobalDamageIndicator = false;
    public float globalIndicatorMaxDistance = 128.0f;
    public String killText = "Kill!";
    public int killTextColor = 0xFFF9867D;
    public boolean showKillIndicator = true;
    public int damageIndicatorNormalColor = 0xFFFFFFFF;
    public int damageIndicatorCritColor = 0xFFD700;
    public int damageIndicatorKillColor = 0xFFFC887E;
    
    // ========== Entity Info ==========
    public boolean showInfo = true;
    public float infoTrackTime = 15.0f;
    public boolean infoNoRoundedBorder = false;
    public int infoBackgroundColor = 0xFF000000;
    public int infoBackgroundOpacity = 25;
    public int infoBarColor = 0xFFB1EAC2;
    public int infoBarHealColor = 0xFFB1EAC2;
    public int infoBarDamageColor = 0xFFF9867D;
    
    // ========== Rating ==========
    public boolean showRating = true;
    public float ratingResetTime = 10f;
    public float comboPoints = 0.2f;
    public float hitPoints = 6f;
    public float critPoints = 10f;
    public boolean ratingUseImages = false;
    public List<RatingGrade> ratingGrades = new ArrayList<>();
    
    // ========== Debug ==========
    public boolean debugMode = false;
    public boolean debugShowDamageInfo = false;
    public boolean debugShowRating = false;
    
    // ========== Update Check ==========
    public boolean checkUpdate = true;
    
    // ========== Preview ==========
    public boolean previewEnabled = false;
    public boolean hasShownWelcomeMessage = false;
    
    // ========== Module Configs ==========
    public ModuleConfig totalDamageConfig = new ModuleConfig(0.8160156f, 0.37278107f, 0.95652175f);
    public ModuleConfig comboConfig = new ModuleConfig(0.8828125f, 0.2611276f, 0.95652175f);
    public ModuleConfig historyConfig = new ModuleConfig(0.9359375f, 0.3305973f, 0.95652175f);
    public ModuleConfig infoConfig = new ModuleConfig(0.61875f, 0.59909815f, 0.5539445f);
    public ModuleConfig ratingConfig = new ModuleConfig(0.7867187f, 0.31508327f, 1.4066461f);
    
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
    }

    public static DamageEngineConfig getInstance() {
        return INSTANCE;
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
                DamageEngineConfig loaded = GSON.fromJson(reader, DamageEngineConfig.class);
                if (loaded != null) {
                    copyFrom(loaded);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void copyFrom(DamageEngineConfig loaded) {
        // General
        this.showDamage = loaded.showDamage;
        this.hideOnF1 = loaded.hideOnF1;
        this.numberSeparator = loaded.numberSeparator;
        
        // Damage Display
        this.showDamageDisplay = loaded.showDamageDisplay;
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
        this.showHealIndicator = loaded.showHealIndicator;
        this.healIndicatorColor = loaded.healIndicatorColor;
        this.indicatorPrefixSign = loaded.indicatorPrefixSign;
        this.showGlobalDamageIndicator = loaded.showGlobalDamageIndicator;
        this.globalIndicatorMaxDistance = loaded.globalIndicatorMaxDistance > 0 ? loaded.globalIndicatorMaxDistance : 128.0f;
        this.killText = loaded.killText != null ? loaded.killText : "Kill!";
        this.killTextColor = loaded.killTextColor;
        this.showKillIndicator = loaded.showKillIndicator;
        this.damageIndicatorNormalColor = loaded.damageIndicatorNormalColor;
        this.damageIndicatorCritColor = loaded.damageIndicatorCritColor;
        this.damageIndicatorKillColor = loaded.damageIndicatorKillColor;
        
        // Entity Info
        this.showInfo = loaded.showInfo;
        this.infoTrackTime = loaded.infoTrackTime;
        this.infoNoRoundedBorder = loaded.infoNoRoundedBorder;
        this.infoBackgroundColor = loaded.infoBackgroundColor;
        this.infoBackgroundOpacity = loaded.infoBackgroundOpacity;
        this.infoBarColor = loaded.infoBarColor;
        this.infoBarHealColor = loaded.infoBarHealColor;
        this.infoBarDamageColor = loaded.infoBarDamageColor;
        
        // Rating
        this.showRating = loaded.showRating;
        this.ratingResetTime = loaded.ratingResetTime;
        this.comboPoints = loaded.comboPoints;
        this.hitPoints = loaded.hitPoints;
        this.critPoints = loaded.critPoints;
        this.ratingUseImages = loaded.ratingUseImages;
        if (loaded.ratingGrades != null && !loaded.ratingGrades.isEmpty()) {
            this.ratingGrades = loaded.ratingGrades;
            for (int i = 0; i < this.ratingGrades.size(); i++) {
                this.ratingGrades.get(i).index = i + 1;
            }
        }
        
        // Debug
        this.debugMode = loaded.debugMode;
        this.debugShowDamageInfo = loaded.debugShowDamageInfo;
        this.debugShowRating = loaded.debugShowRating;
        
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
        hideOnF1 = true;
        numberSeparator = true;
        
        // Damage Display
        showDamageDisplay = true;
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
        showHealIndicator = false;
        healIndicatorColor = 0xFFB1EAC2;
        indicatorPrefixSign = false;
        showGlobalDamageIndicator = false;
        globalIndicatorMaxDistance = 128.0f;
        killText = "Kill!";
        killTextColor = 0xFFF9867D;
        showKillIndicator = true;
        damageIndicatorNormalColor = 0xFFFFFFFF;
        damageIndicatorCritColor = 0xFFD700;
        damageIndicatorKillColor = 0xFFFC887E;
        
        // Entity Info
        showInfo = true;
        infoTrackTime = 15.0f;
        infoNoRoundedBorder = false;
        infoBackgroundColor = 0xFF000000;
        infoBackgroundOpacity = 25;
        infoBarColor = 0xFFB1EAC2;
        infoBarHealColor = 0xFFB1EAC2;
        infoBarDamageColor = 0xFFF9867D;
        
        // Rating
        showRating = true;
        ratingResetTime = 10f;
        comboPoints = 0.2f;
        hitPoints = 6f;
        critPoints = 10f;
        ratingUseImages = false;
        ratingGrades.clear();
        ratingGrades.add(new RatingGrade(1000f, "S", 0xFFD700, 1));
        ratingGrades.add(new RatingGrade(500f, "A", 0xFF6347, 2));
        ratingGrades.add(new RatingGrade(250f, "B", 0x00BFFF, 3));
        ratingGrades.add(new RatingGrade(100f, "C", 0x32CD32, 4));
        ratingGrades.add(new RatingGrade(20f, "D", 0xA0A0A0, 5));
        
        // Debug
        debugMode = false;
        debugShowDamageInfo = false;
        debugShowRating = false;
        
        // Update Check
        checkUpdate = true;
        
        // Preview
        previewEnabled = false;
        
        // Module configs
        totalDamageConfig = new ModuleConfig(0.8160156f, 0.37278107f, 0.95652175f);
        comboConfig = new ModuleConfig(0.8828125f, 0.2611276f, 0.95652175f);
        historyConfig = new ModuleConfig(0.9359375f, 0.3305973f, 0.95652175f);
        infoConfig = new ModuleConfig(0.61875f, 0.59909815f, 0.5539445f);
        ratingConfig = new ModuleConfig(0.7867187f, 0.31508327f, 1.4066461f);
        
        damageThresholds.clear();
        damageThresholds.add(new DamageThreshold(0f, 0xFFFFFFFF));
        damageThresholds.add(new DamageThreshold(25f, 0x3FA9F0));
        damageThresholds.add(new DamageThreshold(50f, 0xFC54FC));
        damageThresholds.add(new DamageThreshold(100f, 0xFFD700));
    }
}