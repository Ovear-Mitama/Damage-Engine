package damage.engine;

import damage.engine.compat.tacz.TaczCompat;
import damage.engine.network.HandshakePayload;
import damage.engine.util.DamageTrackerHelper;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DamageEngine implements ModInitializer {
	public static final String MOD_ID = "damage-engine";
	public static final String MOD_VERSION = "1.4.4.1";
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

		// Mod compat: allow external mods (e.g. TACZ) to resolve the attacker from
		// the direct damage source (bullet). Reflection-based, safe when TACZ is absent.
		DamageTrackerHelper.setAttackerResolver((victim, directSource, source) ->
			TaczCompat.tryGetTaczShooter(directSource));

		// TaCZ: Refabricated compatibility (headshot -> crit). Only when TaCZ is installed.
		if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("tacz")) {
			try {
				damage.engine.compat.tacz.TaczFabricCompat.init();
			} catch (Throwable t) {
				LOGGER.warn("Failed to enable TaCZ compatibility: {}", t.toString());
			}
		}

		// Flush any pending (merged) damage payload at the end of each server tick
		// (TACZ fires hurt() twice per hit, merged into one payload).
		ServerTickEvents.END_SERVER_TICK.register(server ->
			DamageTrackerHelper.flushPendingDamage());

		LOGGER.info("Damage Engine initialized.");
	}
}
