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
	public static final String MOD_VERSION = "1.4.6.3";
	public static final Logger LOGGER = LogUtils.getLogger();

	// NOTE: keyed by entity id, NOT LivingEntity - referencing LivingEntity in a
	// static field's generic signature can trigger its class load before the
	// client mixin config (Mixins.addConfiguration below) is prepared, which
	// crashes with "MixinTargetAlreadyLoadedException: loaded too early".
	private static final Map<Integer, DamageTrackerHelper.Snapshot> PRE_DAMAGE_SNAPS = new ConcurrentHashMap<>();
	// Level per pending snapshot so the delayed end-of-tick broadcast can resolve
	// the (possibly dead) victim entity from its id.
	private static final Map<Integer, net.minecraft.server.level.ServerLevel> PRE_DAMAGE_LEVELS = new ConcurrentHashMap<>();

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

		// Epic Fight compat: Epic Fight's LivingHurtEvent handler runs at the default
		// (NORMAL) priority and recalculates the damage amount (armor penetration,
		// damage modifiers, executions). The snapshot above was captured at HIGHEST
		// with the pre-modification amount; re-capture the final amount at LOWEST so
		// entities that accept the hit without losing health (e.g. test dummies
		// intercepting setHealth) fall back to Epic Fight's real damage value.
		try {
			Class.forName("yesman.epicfight.main.EpicFightMod");
			MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST,
				(LivingHurtEvent event) -> {
				int id = event.getEntity().getId();
				DamageTrackerHelper.Snapshot snap = PRE_DAMAGE_SNAPS.get(id);
				if (snap != null) {
					PRE_DAMAGE_SNAPS.put(id, snap.withSourceAmount(event.getAmount()));
				}
			});
			LOGGER.info("Epic Fight compatibility handler registered.");
		} catch (Throwable t) {
			// Epic Fight not loaded - compat stays inactive.
		}

		IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

		// Track damage using Forge events. LivingDamageEvent fires BEFORE the victim's
		// health is actually reduced, so a health-diff there is always 0 and the code
		// falls back to the raw source amount (pre-armor). Instead, remember the
		// pre-hit snapshot here (first hit of the tick wins) and broadcast at the end
		// of the server tick, when setHealth() has already run - the health diff is
		// then the REAL damage the target took (armor/enchantment/resistance applied).
		//
		// Modern Damage Control (MDC) compat: MDC's onLivingHurt also runs at
		// EventPriority.HIGHEST and, for TaCZ bullets / vanilla projectiles / hardcore
		// part-health damage, CANCELS the event and applies the damage itself via
		// direct setHealth() during event dispatch (it does not call hurt() again, so
		// no second event fires). If we captured the snapshot at the default priority
		// (after MDC), prevHealth would already include MDC's health loss and the
		// tick-end diff would be ~0. HIGHEST priority maximizes the chance we capture
		// the true pre-hit health first; the tick-end health-diff then reports the
		// damage MDC actually dealt.
		MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.HIGHEST,
			(LivingHurtEvent event) -> {
			if (event.getEntity().level() instanceof net.minecraft.server.level.ServerLevel sl) {
				int id = event.getEntity().getId();
				PRE_DAMAGE_LEVELS.put(id, sl);
				PRE_DAMAGE_SNAPS.putIfAbsent(id,
					DamageTrackerHelper.capturePreDamage(event.getEntity(), event.getSource(), event.getAmount()));
			}
		});

		// Broadcast pending damage at the end of each server tick, after the victim's
		// health was actually reduced, then flush any merged payload.
		MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.TickEvent.ServerTickEvent event) -> {
			if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
			if (!PRE_DAMAGE_SNAPS.isEmpty()) {
				for (Integer id : new java.util.ArrayList<>(PRE_DAMAGE_SNAPS.keySet())) {
					DamageTrackerHelper.Snapshot snap = PRE_DAMAGE_SNAPS.remove(id);
					net.minecraft.server.level.ServerLevel level = PRE_DAMAGE_LEVELS.remove(id);
					if (snap == null || level == null) continue;
					net.minecraft.world.entity.Entity e = level.getEntity(id);
					if (e instanceof net.minecraft.world.entity.LivingEntity le) {
						DamageTrackerHelper.broadcastPostDamage(le, null, snap, LOGGER, true);
					}
				}
			}
			DamageTrackerHelper.flushPendingDamage();
		});
	}
}
