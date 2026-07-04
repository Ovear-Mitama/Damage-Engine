package damage.engine;

import net.fabricmc.loader.api.FabricLoader;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class DamageEngineConfig {
    
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("damage-engine");
    private static final Path CONFIG_PATH = CONFIG_DIR.resolve("config.json");
    private static final DamageEngineConfig INSTANCE = new DamageEngineConfig();
    
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public boolean showDamage = true;
    public boolean showDamageDisplay = true;
    public boolean showProgressBar = true;
    public boolean showCombo = true;
    public boolean showDamageHistory = true;
    public boolean resetEnabled = true;
    public boolean showInfo = true;
    public boolean previewEnabled = false;
    public boolean hasShownWelcomeMessage = false;
    public boolean showDamageIndicator = true;
    public boolean showKillIndicator = true;

    public ModuleConfig totalDamageConfig = new ModuleConfig(0.8160156f, 0.37278107f, 0.95652175f);
    public ModuleConfig comboConfig = new ModuleConfig(0.8828125f, 0.2611276f, 0.95652175f);
    public ModuleConfig historyConfig = new ModuleConfig(0.9359375f, 0.3305973f, 0.95652175f);
    public ModuleConfig infoConfig = new ModuleConfig(0.61875f, 0.59909815f, 0.5539445f);
    public ModuleConfig ratingConfig = new ModuleConfig(0.7867187f, 0.31508327f, 1.4066461f);
    
    public int progressBarColor = 0xFFFFFFFF;
    public int comboColor = 0xFFAA00;
    public int historyColor = 0xFFFFFFFF;
    public int normalColor = 0xFFFFFFFF;
    public int critColor = 0xFFD700;
    public int infoBarColor = 0xFFB1EAC2;
    public int infoBarHealColor = 0xFFB1EAC2;
    public int infoBarDamageColor = 0xFFF9867D;
    public int infoBackgroundColor = 0xFF000000;
    public int infoBackgroundOpacity = 25;
    public boolean infoNoRoundedBorder = false;
    
    public int damageIndicatorNormalColor = 0xFFFFFFFF;
    public int damageIndicatorCritColor = 0xFFD700;
    public int damageIndicatorKillColor = 0xFFFC887E;
    
    public int decimalPlaces = 1;
    
    public List<DamageThreshold> damageThresholds = new ArrayList<>();

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

    public float resetTime = 10.0f;
    public int historyLimit = 20;
    public float historyDisappearanceTime = 10.0f;
    public float infoTrackTime = 15.0f;
    public boolean debugMode = false;
    public boolean debugShowDamageInfo = false;
    public boolean debugShowRating = false;
    public boolean hideOnF1 = true;
    
    // Rating system
    public boolean showRating = true;
    public boolean ratingUseImages = false;
    public float ratingResetTime = 10f;
    public float comboPoints = 0.2f;
    public float hitPoints = 6f;
    public float critPoints = 10f;
    public List<RatingGrade> ratingGrades = new ArrayList<>();

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

    public void load() {
        try {
            if (!Files.exists(CONFIG_DIR)) {
                Files.createDirectories(CONFIG_DIR);
            }
            
            if (!Files.exists(CONFIG_PATH)) {
                save();
                return;
            }
            
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                DamageEngineConfig loaded = GSON.fromJson(reader, DamageEngineConfig.class);
                if (loaded != null) {
                    this.showDamage = loaded.showDamage;
                    this.showDamageDisplay = loaded.showDamageDisplay;
                    this.showProgressBar = loaded.showProgressBar;
                    this.showCombo = loaded.showCombo;
                    this.showDamageHistory = loaded.showDamageHistory;
                    this.resetEnabled = loaded.resetEnabled;
                    this.showInfo = loaded.showInfo;
                    this.previewEnabled = loaded.previewEnabled;
                    this.hasShownWelcomeMessage = loaded.hasShownWelcomeMessage;
                    this.showDamageIndicator = loaded.showDamageIndicator;
                    this.showKillIndicator = loaded.showKillIndicator;
                    
                    if (loaded.totalDamageConfig != null) this.totalDamageConfig = loaded.totalDamageConfig;
                    if (loaded.comboConfig != null) this.comboConfig = loaded.comboConfig;
                    if (loaded.historyConfig != null) this.historyConfig = loaded.historyConfig;
                    if (loaded.infoConfig != null) this.infoConfig = loaded.infoConfig;
                    if (loaded.ratingConfig != null) this.ratingConfig = loaded.ratingConfig;
                    
                    this.progressBarColor = loaded.progressBarColor;
                    this.comboColor = loaded.comboColor;
                    this.historyColor = loaded.historyColor;
                    this.normalColor = loaded.normalColor;
                    this.critColor = loaded.critColor;
                    this.infoBarColor = loaded.infoBarColor;
                    this.infoBarHealColor = loaded.infoBarHealColor;
                    this.infoBarDamageColor = loaded.infoBarDamageColor;
                    this.infoBackgroundColor = loaded.infoBackgroundColor;
                    this.infoBackgroundOpacity = loaded.infoBackgroundOpacity;
                    this.infoNoRoundedBorder = loaded.infoNoRoundedBorder;
                    
                    this.damageIndicatorNormalColor = loaded.damageIndicatorNormalColor;
                    this.damageIndicatorCritColor = loaded.damageIndicatorCritColor;
                    this.damageIndicatorKillColor = loaded.damageIndicatorKillColor;
                    this.decimalPlaces = loaded.decimalPlaces;
                    
                    if (loaded.damageThresholds != null) {
                        this.damageThresholds = loaded.damageThresholds;
                    }
                    this.resetTime = loaded.resetTime;
                    this.historyLimit = loaded.historyLimit;
                    this.historyDisappearanceTime = loaded.historyDisappearanceTime;
                    this.infoTrackTime = loaded.infoTrackTime;
                    this.debugMode = loaded.debugMode;
                    this.debugShowDamageInfo = loaded.debugShowDamageInfo;
                    this.debugShowRating = loaded.debugShowRating;
                    this.hideOnF1 = loaded.hideOnF1;
                    
                    this.showRating = loaded.showRating;
                    this.ratingResetTime = loaded.ratingResetTime;
                    this.comboPoints = loaded.comboPoints;
                    this.hitPoints = loaded.hitPoints;
                    this.critPoints = loaded.critPoints;
                    if (loaded.ratingGrades != null && !loaded.ratingGrades.isEmpty()) {
                        this.ratingGrades = loaded.ratingGrades;
                        for (int i = 0; i < this.ratingGrades.size(); i++) {
                            this.ratingGrades.get(i).index = i + 1;
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void save() {
        try {
            if (!Files.exists(CONFIG_DIR)) {
                Files.createDirectories(CONFIG_DIR);
            }
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
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
        showDamage = true;
        showDamageDisplay = true;
        showProgressBar = true;
        showCombo = true;
        showDamageHistory = true;
        resetEnabled = true;
        showInfo = true;
        previewEnabled = false;
        showDamageIndicator = true;
        showKillIndicator = true;
        
        totalDamageConfig = new ModuleConfig(0.8160156f, 0.37278107f, 0.95652175f);
        comboConfig = new ModuleConfig(0.8828125f, 0.2611276f, 0.95652175f);
        historyConfig = new ModuleConfig(0.9359375f, 0.3305973f, 0.95652175f);
        infoConfig = new ModuleConfig(0.61875f, 0.59909815f, 0.5539445f);
        ratingConfig = new ModuleConfig(0.7867187f, 0.31508327f, 1.4066461f);
        
        progressBarColor = 0xFFFFFFFF;
        comboColor = 0xFFAA00;
        historyColor = 0xFFFFFFFF;
        normalColor = 0xFFFFFFFF;
        critColor = 0xFFD700;
        infoBarColor = 0xFFB1EAC2;
        infoBarHealColor = 0xFFB1EAC2;
        infoBarDamageColor = 0xFFF9867D;
        infoBackgroundColor = 0xFF000000;
        infoBackgroundOpacity = 25;
        infoNoRoundedBorder = false;
        
        damageIndicatorNormalColor = 0xFFFFFFFF;
        damageIndicatorCritColor = 0xFFD700;
        damageIndicatorKillColor = 0xFFFC887E;
        decimalPlaces = 1;
        
        damageThresholds.clear();
        damageThresholds.add(new DamageThreshold(0f, 0xFFFFFFFF));
        damageThresholds.add(new DamageThreshold(25f, 0x3FA9F0));
        damageThresholds.add(new DamageThreshold(50f, 0xFC54FC));
        damageThresholds.add(new DamageThreshold(100f, 0xFFD700));
        
        resetTime = 10.0f;
        historyLimit = 20;
        historyDisappearanceTime = 10.0f;
        infoTrackTime = 15.0f;
        debugMode = false;
        debugShowDamageInfo = false;
        debugShowRating = false;
        ratingUseImages = false;
        hideOnF1 = true;
        
        showRating = true;
        ratingResetTime = 10f;
        comboPoints = 0.2f;
        hitPoints = 6f;
        critPoints = 10f;
        ratingGrades.clear();
        ratingGrades.add(new RatingGrade(1000f, "S", 0xFFD700, 1));
        ratingGrades.add(new RatingGrade(500f, "A", 0xFF6347, 2));
        ratingGrades.add(new RatingGrade(250f, "B", 0x00BFFF, 3));
        ratingGrades.add(new RatingGrade(100f, "C", 0x32CD32, 4));
        ratingGrades.add(new RatingGrade(20f, "D", 0xA0A0A0, 5));
    }
}
