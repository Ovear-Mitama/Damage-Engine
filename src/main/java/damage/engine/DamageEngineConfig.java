package damage.engine;

import net.fabricmc.loader.api.FabricLoader;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

public class DamageEngineConfig {
    // 通过 GSON 使用 JSON 结构？
    // 用户请求："创建一个文件夹，然后创建一个 json 文件来存储数据"
    // 我们需要从 Properties 切换到 JSON。
    // 我们可以使用 Google Gson（通常包含在 Minecraft/Fabric 中）或者如果很简单则只需简单的字符串操作。
    // Fabric 提供了 Gson。
    
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("damage-engine");
    private static final Path CONFIG_PATH = CONFIG_DIR.resolve("config.json");
    private static final DamageEngineConfig INSTANCE = new DamageEngineConfig();
    
    private static final com.google.gson.Gson GSON = new com.google.gson.GsonBuilder().setPrettyPrinting().create();

    // 通用设置
    public boolean showDamage = true;
    public boolean showProgressBar = true;

    // 外观设置
    public float hudScale = 0.95652175f;
    public float hudX = 0.8328125f; // -1.0f 意味着居中（相对 0.0-1.0）
    public float hudY = 0.2611276f; // -1.0f 意味着居中（相对 0.0-1.0）
    public int progressBarColor = 0xFFFFFFFF; // 白色 (-1)
    
    // 伤害颜色
    public java.util.List<DamageThreshold> damageThresholds = new java.util.ArrayList<>();

    public static class DamageThreshold {
        public float threshold;
        public int color;
        
        public DamageThreshold(float threshold, int color) {
            this.threshold = threshold;
            this.color = color;
        }
    }

    // 其他
    public float resetTime = 5.0f;
    public int historyLimit = 5;

    private DamageEngineConfig() {
        // 默认值
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
                    this.hudScale = loaded.hudScale;
                    this.hudX = loaded.hudX;
                    this.hudY = loaded.hudY;
                    this.progressBarColor = loaded.progressBarColor;
                    if (loaded.damageThresholds != null) {
                        this.damageThresholds = loaded.damageThresholds;
                    }
                    this.resetTime = loaded.resetTime;
                    this.historyLimit = loaded.historyLimit;
                    
                    // 验证
                    if (Math.abs(hudX) > 2.0f) hudX = -1.0f;
                    if (Math.abs(hudY) > 2.0f) hudY = -1.0f;
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
        hudScale = 0.95652175f;
        hudX = 0.8328125f;
        hudY = 0.2611276f;
        progressBarColor = 0xFFFFFFFF;
        
        damageThresholds.clear();
        damageThresholds.add(new DamageThreshold(0f, 0xFFFFFFFF));
        damageThresholds.add(new DamageThreshold(25f, 0x3FA9F0));
        damageThresholds.add(new DamageThreshold(50f, 0xFC54FC));
        damageThresholds.add(new DamageThreshold(100f, 0xFFD700));
        
        resetTime = 5.0f;
        historyLimit = 5;
        save();
    }
}
