package damage.engine;

/**
 * Version metadata shared by both loader entrypoints.
 * Keep in sync with gradle.properties (mod_version).
 */
public final class DamageEngineMeta {
    public static final String VERSION = "1.4.8";

    /**
     * 当前运行的 Minecraft 版本(如 "1.21.11")。版本检查要用它去匹配 Modrinth 的
     * game_versions，写死会在升版本时漏改，所以统一从运行时取。
     */
    public static String mcVersion() {
        return net.minecraft.SharedConstants.getCurrentVersion().name();
    }

    private DamageEngineMeta() {}
}
