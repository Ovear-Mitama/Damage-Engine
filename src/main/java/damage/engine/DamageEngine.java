package damage.engine;

import damage.engine.network.HandshakePayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DamageEngine implements ModInitializer {
	public static final String MOD_ID = "damage-engine";
	public static final String MOD_VERSION = "1.4.1.1";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing Damage Engine...");
		DamageEngineConfig.getInstance().load();

		// codec 注册用 Fabric API（<clinit> 注入与 Fabric API 共存有问题）
		PayloadTypeRegistry.playS2C().register(
			damage.engine.network.DamagePayload.TYPE,
			damage.engine.network.DamagePayload.STREAM_CODEC);
		PayloadTypeRegistry.playC2S().register(
			HandshakePayload.TYPE,
			HandshakePayload.STREAM_CODEC);
	}
}
