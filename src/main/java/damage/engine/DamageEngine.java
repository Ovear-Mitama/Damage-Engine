package damage.engine;

import damage.engine.network.DamagePayload;
import damage.engine.network.HandshakePayload;
import damage.engine.network.NetworkRegistry;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DamageEngine implements ModInitializer {
	public static final String MOD_ID = "damage-engine";
	public static final String MOD_VERSION = "1.3.4";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing Damage Engine...");
		
		// 加载配置
		DamageEngineConfig.getInstance().load();

		// 注册网络包（使用自己的 NetworkRegistry 替代 Fabric API）
		NetworkRegistry.registerS2C(DamagePayload.TYPE, DamagePayload.STREAM_CODEC, (payload, ctx) -> {
			// S2C payload handler is on the client side via ClientPayloadHandlerMixin
		});
		
		// 注册握手包（C2S）的服务端接收器
		NetworkRegistry.registerC2S(HandshakePayload.TYPE, HandshakePayload.STREAM_CODEC, (payload, ctx) -> {
			// handler 的存在本身就是表示服务端安装了本 mod
		});
	}
}
