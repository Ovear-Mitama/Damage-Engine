package damage.engine;

import damage.engine.network.HandshakePayload;
import damage.engine.util.DamageTrackerHelper;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DamageEngine implements ModInitializer {
	public static final String MOD_ID = "damage-engine";
	public static final String MOD_VERSION = "1.4.3";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing Damage Engine...");
		DamageEngineConfig.getInstance().load();

		// codec 注册用 Fabric API
		PayloadTypeRegistry.playS2C().register(
			damage.engine.network.DamagePayload.TYPE,
			damage.engine.network.DamagePayload.STREAM_CODEC);
		PayloadTypeRegistry.playC2S().register(
			HandshakePayload.TYPE,
			HandshakePayload.STREAM_CODEC);

		// Flush any pending (merged) damage payload at the end of each server tick
		ServerTickEvents.END_SERVER_TICK.register(server ->
			DamageTrackerHelper.flushPendingDamage());

		LOGGER.info("Damage Engine initialized.");
	}
}
