package damage.engine;

import damage.engine.network.DamagePayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DamageEngine implements ModInitializer {
	public static final String MOD_ID = "damage-engine";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing Damage Engine...");
		
		// Load Config
		DamageEngineConfig.getInstance().load();

		// Register Networking
		PayloadTypeRegistry.playS2C().register(DamagePayload.ID, DamagePayload.CODEC);
	}
}
