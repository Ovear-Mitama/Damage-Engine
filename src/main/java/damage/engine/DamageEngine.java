package damage.engine;

import damage.engine.compat.tacz.TaczCompat;
import damage.engine.network.DamagePayload;
import damage.engine.network.HandshakePayload;
import damage.engine.util.DamageTrackerHelper;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.Unpooled;

public class DamageEngine implements ModInitializer {
	public static final String MOD_ID = "damage-engine";
	public static final String MOD_VERSION = "1.4.6.1";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final ResourceLocation DAMAGE_PACKET_ID = new ResourceLocation(MOD_ID, "damage_packet");
	public static final ResourceLocation HANDSHAKE_PACKET_ID = new ResourceLocation(MOD_ID, "handshake");

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing Damage Engine (Fabric 1.20.1)...");
		DamageEngineConfig.getInstance().load();

		// Register handshake handler - server receives client ping
		ServerPlayNetworking.registerGlobalReceiver(HANDSHAKE_PACKET_ID,
			(server, player, handler, buf, responseSender) -> {
				// Handler的存在本身表示服务端安装了本mod
			});

		// Register damage broadcaster
		DamageTrackerHelper.setBroadcaster((payload, level) -> {
			for (ServerPlayer player : level.players()) {
				FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
				buf.writeVarInt(payload.entityId());
				buf.writeFloat(payload.amount());
				buf.writeBoolean(payload.isCrit());
				buf.writeVarInt(payload.attackerId());
				buf.writeUtf(payload.debugInfo() != null ? payload.debugInfo() : "");
				buf.writeDouble(payload.posX());
				buf.writeDouble(payload.posY());
				buf.writeDouble(payload.posZ());
				buf.writeBoolean(payload.isProjectile());
				buf.writeBoolean(payload.killed());
				ServerPlayNetworking.send(player, DAMAGE_PACKET_ID, buf);
			}
		});

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

		// Flush any pending (merged) damage payload at the end of each server tick.
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(
			server -> DamageTrackerHelper.flushPendingDamage());
	}
}
