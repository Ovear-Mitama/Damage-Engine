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
		
		// 加载配置
		DamageEngineConfig.getInstance().load();

		// 注册网络
		PayloadTypeRegistry.playS2C().register(DamagePayload.ID, DamagePayload.CODEC);
	}
}
