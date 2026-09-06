package damage.engine;

import com.mojang.logging.LogUtils;
import damage.engine.compat.tacz.TaczCompat;
import damage.engine.util.DamageTrackerHelper;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mod("damageengine")
public class DamageEngine {
	public static final String MOD_ID = "damageengine";
	public static final String MOD_VERSION = "1.4.6.3";
	public static final Logger LOGGER = LogUtils.getLogger();

	private static final Map<LivingEntity, DamageTrackerHelper.Snapshot> PRE_DAMAGE_SNAPS = new ConcurrentHashMap<>();

	public DamageEngine(IEventBus modEventBus, ModContainer modContainer) {
		LOGGER.info("Initializing Damage Engine...");

		DamageEngineConfig.getInstance().load();

		// Mod compat: allow external mods (e.g. TACZ) to resolve the attacker from
		// the direct damage source (bullet). Reflection-based helper keeps this safe
		// whether TACZ is present or not.
		DamageTrackerHelper.setAttackerResolver((victim, directSource, source) ->
			TaczCompat.tryGetTaczShooter(directSource));
		LOGGER.info("TACZ compatibility attacker resolver registered.");

		// TaCZ (NeoForge port) compatibility (headshot -> crit). Only when TaCZ is installed.
		// Reflective call: TaczNeoCompat may be excluded from the build when the TaCZ
		// jar is unavailable (see build.gradle), so a hard reference would break it.
		if (ModList.get().isLoaded("tacz")) {
			try {
				Class<?> cls = Class.forName("damage.engine.compat.tacz.TaczNeoCompat");
				cls.getMethod("init").invoke(null);
			} catch (Throwable t) {
				LOGGER.warn("Failed to enable TaCZ compatibility: {}", t.toString());
			}
		}

		// 使用事件进行伤害追踪，兼容 1.21.x-1.21.8
		NeoForge.EVENT_BUS.addListener((LivingIncomingDamageEvent event) -> {
			// Clean any stale snapshot for the same entity first: if the previous
			// hurt() was cancelled (no Post event fires), the old snapshot would
			// otherwise linger in the map forever.
			PRE_DAMAGE_SNAPS.remove(event.getEntity());
			PRE_DAMAGE_SNAPS.put(event.getEntity(),
				DamageTrackerHelper.capturePreDamage(event.getEntity(), event.getSource(), event.getAmount()));
		});

		NeoForge.EVENT_BUS.addListener((LivingDamageEvent.Post event) -> {
			DamageTrackerHelper.Snapshot snap = PRE_DAMAGE_SNAPS.remove(event.getEntity());
			if (snap != null) {
				// Post damage events only fire when the hurt() was accepted
				DamageTrackerHelper.broadcastPostDamage(event.getEntity(), event.getSource(), snap, LOGGER, true);
			}
		});

		// Flush any pending (merged) damage payload at the end of each server tick
		// (TACZ fires hurt() twice per hit, merged into one payload).
		NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) ->
			DamageTrackerHelper.flushPendingDamage());
	}
}
