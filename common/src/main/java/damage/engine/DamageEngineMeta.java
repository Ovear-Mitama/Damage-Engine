package damage.engine;

/**
 * Version metadata shared by both loader entrypoints.
 * Keep in sync with gradle.properties (mod_version).
 */
public final class DamageEngineMeta {
    public static final String VERSION = "2.1.0";

    /** Current loader platform: "fabric" or "neoforge". Detected at runtime via reflection. */
    public static final String PLATFORM;

    /**
     * 当前运行的 Minecraft 版本(如 "1.21.1")。版本检查要用它去匹配 Modrinth 的
     * game_versions，写死会在升版本时漏改，所以统一从运行时取。
     */
    public static String mcVersion() {
        return net.minecraft.SharedConstants.getCurrentVersion().getName();
    }

    static {
        String platform = "neoforge";
        try {
            Class.forName("net.fabricmc.loader.api.FabricLoader");
            platform = "fabric";
        } catch (ClassNotFoundException ignored) {
            // NeoForge: fabric-loader is not present
        }
        PLATFORM = platform;
    }

    private DamageEngineMeta() {}
}
