package damage.engine;

import com.mojang.logging.LogUtils;
import damage.engine.util.DamageTrackerHelper;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.slf4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mod("damageengine")
public class DamageEngine {
	public static final String MOD_ID = "damageengine";
	public static final String MOD_VERSION = "1.4.1.1";
	public static final Logger LOGGER = LogUtils.getLogger();

	private static final Map<LivingEntity, DamageTrackerHelper.Snapshot> PRE_DAMAGE_SNAPS = new ConcurrentHashMap<>();

	public DamageEngine(IEventBus modEventBus, ModContainer modContainer) {
		LOGGER.info("Initializing Damage Engine...");

		DamageEngineConfig.getInstance().load();

		// 使用事件进行伤害追踪，兼容 1.21.x-1.21.8
		NeoForge.EVENT_BUS.addListener((LivingIncomingDamageEvent event) -> {
			PRE_DAMAGE_SNAPS.put(event.getEntity(),
				DamageTrackerHelper.capturePreDamage(event.getEntity(), event.getSource()));
		});

		NeoForge.EVENT_BUS.addListener((LivingDamageEvent.Post event) -> {
			DamageTrackerHelper.Snapshot snap = PRE_DAMAGE_SNAPS.remove(event.getEntity());
			if (snap != null) {
				DamageTrackerHelper.broadcastPostDamage(event.getEntity(), event.getSource(), snap, LOGGER);
			}
		});

		NeoForge.EVENT_BUS.addListener((LivingHealEvent event) -> {
			if (event.getAmount() > 0) {
				DamageTrackerHelper.broadcastHeal(event.getEntity(), event.getAmount());
			}
		});
	}
}
