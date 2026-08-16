package damage.engine;

import com.mojang.logging.LogUtils;
import damage.engine.compat.tacz.TaczCompat;
import damage.engine.network.NetworkHandler;
import damage.engine.util.DamageTrackerHelper;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mod("damageengine")
public class DamageEngine {
	public static final String MOD_ID = "damageengine";
	public static final String MOD_VERSION = "1.4.4";
	public static final Logger LOGGER = LogUtils.getLogger();

	// NOTE: keyed by entity id, NOT LivingEntity - referencing LivingEntity in a
	// static field's generic signature can trigger its class load before the
	// client mixin config (Mixins.addConfiguration below) is prepared, which
	// crashes with "MixinTargetAlreadyLoadedException: loaded too early".
	private static final Map<Integer, DamageTrackerHelper.Snapshot> PRE_DAMAGE_SNAPS = new ConcurrentHashMap<>();

	public DamageEngine() {
		LOGGER.info("Initializing Damage Engine (Forge 1.20.1)...");

		// Register the client mixin config from code (Vanilla Forge 1.20.1 does not
		// support mods.toml [[mixins]]). This config ONLY contains EditBoxCursorMixin
		// whose target (EditBox) loads late at GUI-open time - NOT the entity mixins
		// (LivingEntity etc.) that are already loaded during mod construction and
		// crashed with "MixinTargetAlreadyLoadedException: loaded too early".
		DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
			() -> () -> {
				org.spongepowered.asm.mixin.Mixins.addConfiguration("damage-engine.client.mixins.json");
			});

		DamageEngineConfig.getInstance().load();

		// Initialize network (SimpleChannel + broadcaster registration)
		NetworkHandler.init();

		// Initialize client-side only components
		DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
			() -> () -> new DamageEngineClient());

		// Register TACZ compat attacker resolver
		DamageTrackerHelper.setAttackerResolver((victim, directSource, source) ->
			TaczCompat.tryGetTaczShooter(directSource));
		LOGGER.info("TACZ compatibility handler registered.");

		// Forge-side TaCZ headshot hook: only when TaCZ is loaded.
		try {
			Class.forName("com.tacz.guns.api.event.common.EntityHurtByGunEvent$Pre");
			damage.engine.compat.tacz.TaczForgeCompat.register(MinecraftForge.EVENT_BUS);
			LOGGER.info("TaCZ headshot event hook registered.");
		} catch (Throwable t) {
			// TaCZ not loaded - headshot hook stays inactive.
		}

		IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

		// Track damage using Forge events
		MinecraftForge.EVENT_BUS.addListener((LivingHurtEvent event) -> {
			PRE_DAMAGE_SNAPS.put(event.getEntity().getId(),
				DamageTrackerHelper.capturePreDamage(event.getEntity(), event.getSource(), event.getAmount()));
		});

		MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.entity.living.LivingDamageEvent event) -> {
			DamageTrackerHelper.Snapshot snap = PRE_DAMAGE_SNAPS.remove(event.getEntity().getId());
			if (snap != null) {
				DamageTrackerHelper.broadcastPostDamage(event.getEntity(), event.getSource(), snap, LOGGER, true);
			}
		});

		// Flush any pending (merged) damage payload at the end of each server tick.
		MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.TickEvent.ServerTickEvent event) -> {
			if (event.phase == net.minecraftforge.event.TickEvent.Phase.END) {
				DamageTrackerHelper.flushPendingDamage();
			}
		});
	}
}
