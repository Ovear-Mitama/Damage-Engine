package damage.engine;

import net.fabricmc.loader.api.FabricLoader;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

public class DamageEngineConfig {
    // Use JSON structure via GSON? 
    // User requested: "Create a folder, then create a json file to store data"
    // We need to switch from Properties to JSON.
    // We can use Google Gson (included in Minecraft/Fabric usually) or just simple string manipulation if minimal.
    // Fabric provides Gson.
    
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("damage-engine");
    private static final Path CONFIG_PATH = CONFIG_DIR.resolve("config.json");
    private static final DamageEngineConfig INSTANCE = new DamageEngineConfig();
    
    private static final com.google.gson.Gson GSON = new com.google.gson.GsonBuilder().setPrettyPrinting().create();

    // General Settings
    public boolean showDamage = true;
    public boolean showProgressBar = true;

    // Appearance Settings
    public float hudScale = 0.95652175f;
    public float hudX = 0.8328125f; // -1.0f means center (Relative 0.0-1.0)
    public float hudY = 0.2611276f; // -1.0f means center (Relative 0.0-1.0)
    public int progressBarColor = 0xFFFFFFFF; // White (-1)

    // Other
    public float resetTime = 5.0f;

    private DamageEngineConfig() {
        // Don't load in constructor to avoid issues if called too early? 
        // Singleton pattern usually fine.
        // load() will be called manually or lazy.
        // But here we call it in constructor.
        // We must ensure directory exists.
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
                    this.resetTime = loaded.resetTime;
                    
                    // Validate
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
        resetTime = 5.0f;
        save();
    }
}
