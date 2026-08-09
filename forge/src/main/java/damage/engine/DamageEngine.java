package damage.engine;

import com.mojang.logging.LogUtils;
import damage.engine.compat.tacz.TaczCompat;
import damage.engine.network.NetworkHandler;
import damage.engine.util.DamageTrackerHelper;
import net.minecraft.world.entity.LivingEntity;
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
	public static final String MOD_VERSION = "1.4.3";
	public static final Logger LOGGER = LogUtils.getLogger();

	private static final Map<LivingEntity, DamageTrackerHelper.Snapshot> PRE_DAMAGE_SNAPS = new ConcurrentHashMap<>();

	public DamageEngine() {
		LOGGER.info("Initializing Damage Engine (Forge 1.20.1)...");

		// Vanilla Forge 1.20.1 does NOT support `mods.toml [[mixins]]` (that is a
		// NeoForge feature) - the declarations were silently ignored, so none of our
		// client mixins (HUD render, keybinds, matrix capture, handshake) ever
		// loaded. Register the client mixin config from code instead. Server-side
		// damage tracking uses Forge events (see below), not server mixins.
		DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
			() -> () -> {
				LOGGER.info("[DE-FORGE] registering client mixin config...");
				org.spongepowered.asm.mixin.Mixins.addConfiguration("damage-engine.client.mixins.json");
				LOGGER.info("[DE-FORGE] client mixin config registered");
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
			PRE_DAMAGE_SNAPS.put(event.getEntity(),
				DamageTrackerHelper.capturePreDamage(event.getEntity(), event.getSource(), event.getAmount()));
		});

		MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.entity.living.LivingDamageEvent event) -> {
			DamageTrackerHelper.Snapshot snap = PRE_DAMAGE_SNAPS.remove(event.getEntity());
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
