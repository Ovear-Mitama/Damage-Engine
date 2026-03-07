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
    public boolean showProgressBar = true;
    public boolean showDamageHistory = true;

    public ModuleConfig totalDamageConfig = new ModuleConfig(0.8203125f, 0.3602415f, 0.95652175f);
    public ModuleConfig comboConfig = new ModuleConfig(0.8828125f, 0.2611276f, 0.95652175f);
    public ModuleConfig historyConfig = new ModuleConfig(0.9359375f, 0.3305973f, 0.95652175f);
    
    public int progressBarColor = 0xFFFFFFFF;
    public int comboColor = 0xFFAA00;
    public int historyColor = 0xFFFFFFFF;
    public int critColor = 0xFFD700;
    
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

    public float resetTime = 5.0f;
    public int historyLimit = 5;
    public float historyDisappearanceTime = 5.0f;
    public boolean debugMode = false;
    public boolean hideOnF1 = true;

    private DamageEngineConfig() {
        damageThresholds.add(new DamageThreshold(0f, 0xFFFFFFFF));
        damageThresholds.add(new DamageThreshold(25f, 0x3FA9F0));
        damageThresholds.add(new DamageThreshold(50f, 0xFC54FC));
        damageThresholds.add(new DamageThreshold(100f, 0xFFD700));
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
                    this.showProgressBar = loaded.showProgressBar;
                    this.showDamageHistory = loaded.showDamageHistory;
                    
                    if (loaded.totalDamageConfig != null) this.totalDamageConfig = loaded.totalDamageConfig;
                    if (loaded.comboConfig != null) this.comboConfig = loaded.comboConfig;
                    if (loaded.historyConfig != null) this.historyConfig = loaded.historyConfig;
                    
                    this.progressBarColor = loaded.progressBarColor;
                    this.comboColor = loaded.comboColor;
                    this.historyColor = loaded.historyColor;
                    this.critColor = loaded.critColor;
                    
                    if (loaded.damageThresholds != null) {
                        this.damageThresholds = loaded.damageThresholds;
                    }
                    this.resetTime = loaded.resetTime;
                    this.historyLimit = loaded.historyLimit;
                    this.historyDisappearanceTime = loaded.historyDisappearanceTime;
                    this.debugMode = loaded.debugMode;
                    this.hideOnF1 = loaded.hideOnF1;
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
        showDamage = true;
        showProgressBar = true;
        showDamageHistory = true;
        
        totalDamageConfig = new ModuleConfig(0.8203125f, 0.3602415f, 0.95652175f);
        comboConfig = new ModuleConfig(0.8828125f, 0.2611276f, 0.95652175f);
        historyConfig = new ModuleConfig(0.9359375f, 0.3305973f, 0.95652175f);
        
        progressBarColor = 0xFFFFFFFF;
        comboColor = 0xFFAA00;
        historyColor = 0xFFFFFFFF;
        critColor = 0xFFD700;
        
        damageThresholds.clear();
        damageThresholds.add(new DamageThreshold(0f, 0xFFFFFFFF));
        damageThresholds.add(new DamageThreshold(25f, 0x3FA9F0));
        damageThresholds.add(new DamageThreshold(50f, 0xFC54FC));
        damageThresholds.add(new DamageThreshold(100f, 0xFFD700));
        
        resetTime = 5.0f;
        historyLimit = 5;
        historyDisappearanceTime = 5.0f;
        debugMode = false;
        hideOnF1 = true;
        save();
    }
}
