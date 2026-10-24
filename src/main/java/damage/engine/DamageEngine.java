package damage.engine;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = DamageEngine.MOD_ID, name = "Damage Engine", version = DamageEngine.VERSION,
    acceptedMinecraftVersions = "[1.12.2]",
    guiFactory = "damage.engine.DamageEngineGuiFactory")
public class DamageEngine {
    public static final String MOD_ID = "damageengine";
    public static final String VERSION = "1.5.2";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        DamageEngineConfig.getInstance().load();
        damage.engine.network.NetworkHandler.init();
        if (event.getSide() == Side.CLIENT) {
            DamageEngineClient.init();
        }
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        LOGGER.info("Damage Engine initialized.");
    }
}
