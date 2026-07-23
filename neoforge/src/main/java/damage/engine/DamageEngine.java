package damage.engine;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod("damageengine")
public class DamageEngine {
	public static final String MOD_ID = "damageengine";
	public static final String MOD_VERSION = "1.3.4.5";
	public static final Logger LOGGER = LogUtils.getLogger();

	public DamageEngine(IEventBus modEventBus, ModContainer modContainer) {
		LOGGER.info("Initializing Damage Engine...");
		
		// 加载配置
		DamageEngineConfig.getInstance().load();
	}
}
