package damage.engine;

import damage.engine.network.HandshakePayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DamageEngine implements ModInitializer {
	public static final String MOD_ID = "damage-engine";
	public static final String MOD_VERSION = "1.3.4.5";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing Damage Engine...");
		
		// 加载配置
		DamageEngineConfig.getInstance().load();

		// 注册 S2C 包类型
		PayloadTypeRegistry.playS2C().register(damage.engine.network.DamagePayload.TYPE, damage.engine.network.DamagePayload.STREAM_CODEC);
		
		// 注册握手包（C2S）的服务端接收器
		PayloadTypeRegistry.playC2S().register(HandshakePayload.TYPE, HandshakePayload.STREAM_CODEC);
		ServerPlayNetworking.registerGlobalReceiver(HandshakePayload.TYPE, (payload, ctx) -> {
			// handler 的存在本身就是表示服务端安装了本 mod
		});
	}
}
