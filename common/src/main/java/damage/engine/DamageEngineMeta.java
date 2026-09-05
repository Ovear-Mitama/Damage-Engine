package damage.engine;

/**
 * Version metadata shared by both loader entrypoints.
 * Keep in sync with gradle.properties (mod_version).
 */
public final class DamageEngineMeta {
    public static final String VERSION = "1.4.6.1";

    /** Current loader platform: "fabric" or "neoforge". Detected at runtime via reflection. */
    public static final String PLATFORM;

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
