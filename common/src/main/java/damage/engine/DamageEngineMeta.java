package damage.engine;

/**
 * Shared build/runtime metadata for Damage Engine.
 * Keep in sync with gradle.properties (mod_version).
 */
public final class DamageEngineMeta {
    public static final String VERSION = "1.4.4.2";

    /** Current loader platform: "fabric" or "forge". Detected at runtime via reflection. */
    public static final String PLATFORM;

    static {
        String platform = "forge";
        try {
            Class.forName("net.fabricmc.loader.api.FabricLoader");
            platform = "fabric";
        } catch (ClassNotFoundException ignored) {
            // Forge: fabric-loader is not present
        }
        PLATFORM = platform;
    }

    private DamageEngineMeta() {}
}
