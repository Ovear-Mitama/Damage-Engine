package damage.engine.network;

import damage.engine.DamageEngine;
import damage.engine.DamageEngineClient;
import damage.engine.DamageEngineConfig;
import damage.engine.hud.DamageIndicator;
import damage.engine.hud.DamageSessionManager;
import damage.engine.hud.RatingManager;
import damage.engine.util.DamageTrackerHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

public class NetworkHandler {
    private static final String PROTOCOL_VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        new ResourceLocation(DamageEngine.MOD_ID, "main"),
        () -> PROTOCOL_VERSION,
        PROTOCOL_VERSION::equals,
        PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    public static void init() {
        // S2C: Damage packet
        CHANNEL.registerMessage(packetId++, DamagePacket.class, DamagePacket::encode, DamagePacket::decode, DamagePacket::handle);

        // C2S: Handshake packet
        CHANNEL.registerMessage(packetId++, HandshakePacket.class, HandshakePacket::encode, HandshakePacket::decode, HandshakePacket::handle);

        // Register the damage broadcaster
        DamageTrackerHelper.setBroadcaster(NetworkHandler::broadcastDamage);
    }

    public static void broadcastDamage(DamagePayload payload, ServerLevel level) {
        DamagePacket packet = new DamagePacket(payload);
        for (ServerPlayer player : level.players()) {
            CHANNEL.sendTo(packet, player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
        }
    }

    /** Send a handshake packet from the client to check server mod presence. */
    public static void sendHandshakeToServer() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            CHANNEL.sendToServer(new HandshakePacket());
        }
    }

    // --- S2C Damage Packet Wrapper ---
    public static class DamagePacket {
        private final DamagePayload payload;

        public DamagePacket(DamagePayload payload) {
            this.payload = payload;
        }

        public DamagePayload getPayload() {
            return payload;
        }

        public static void encode(DamagePacket msg, FriendlyByteBuf buf) {
            buf.writeVarInt(msg.payload.entityId());
            buf.writeFloat(msg.payload.amount());
            buf.writeBoolean(msg.payload.isCrit());
            buf.writeVarInt(msg.payload.attackerId());
            buf.writeUtf(msg.payload.debugInfo() != null ? msg.payload.debugInfo() : "");
            buf.writeDouble(msg.payload.posX());
            buf.writeDouble(msg.payload.posY());
            buf.writeDouble(msg.payload.posZ());
            buf.writeBoolean(msg.payload.isProjectile());
            buf.writeBoolean(msg.payload.killed());
        }

        public static DamagePacket decode(FriendlyByteBuf buf) {
            DamagePayload payload = new DamagePayload(
                buf.readVarInt(), buf.readFloat(), buf.readBoolean(),
                buf.readVarInt(), buf.readUtf(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readBoolean(), buf.readBoolean()
            );
            return new DamagePacket(payload);
        }

        public static void handle(DamagePacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                DamagePayload payload = msg.getPayload();

                if (!DamageEngineClient.serverHasMod) {
                    DamageEngineClient.serverHasMod = true;
                    DamageEngineClient.serverModChecked = true;
                }

                Minecraft mc = Minecraft.getInstance();
                if (mc.level == null || mc.player == null) return;

                DamageEngineConfig config = DamageEngineConfig.getInstance();

                if (config.debugShowDamageInfo) {
                    if (payload.debugInfo() != null && !payload.debugInfo().isEmpty()) {
                        mc.player.displayClientMessage(
                            Component.literal("[DE Debug] ").withStyle(style -> style.withColor(TextColor.fromRgb(0xB3EDC4)))
                                .append(Component.literal(payload.debugInfo()).withStyle(style -> style.withColor(TextColor.fromRgb(0xFFFFFF)))),
                            false
                        );
                    }
                    mc.player.displayClientMessage(
                        Component.literal("[DE Debug] ").withStyle(style -> style.withColor(TextColor.fromRgb(0xB3EDC4)))
                            .append(Component.literal("Dmg: " + String.format("%.1f", payload.amount())
                            + (payload.isCrit() ? " Crit" : "") + " | Entity: " + payload.entityId()
                            + " | Projectile: " + (payload.isProjectile() ? "Yes" : "No")).withStyle(style -> style.withColor(TextColor.fromRgb(0xFFFFFF)))),
                        false
                    );
                }

                boolean isSelfDamage = payload.attackerId() == mc.player.getId();

                if (!isSelfDamage && config.showGlobalDamageIndicator) {
                    // Damage floats on other entities (victim isn't me) follow the "record other players" toggle.
                    // Only filter when an attacker was actually resolved (attackerId > 0): unresolved
                    // attackers (e.g. TACZ bullets whose owner can't be resolved) may still be our own damage.
                    if (payload.attackerId() > 0 && payload.entityId() != mc.player.getId() && !config.recordOtherPlayers) {
                        ctx.get().setPacketHandled(true);
                        return;
                    }
                    if (payload.attackerId() > 0 && mc.level != null) {
                        net.minecraft.world.entity.Entity attackerEntity = mc.level.getEntity(payload.attackerId());
                        if (attackerEntity instanceof net.minecraft.world.entity.LivingEntity le && le.isInvisible()) {
                            ctx.get().setPacketHandled(true);
                            return;
                        }
                    }

                    double dx = payload.posX() - mc.player.getX();
                    double dy = payload.posY() - mc.player.getY();
                    double dz = payload.posZ() - mc.player.getZ();
                    double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

                    if (distance <= config.globalIndicatorMaxDistance) {
                        if (config.showDamageIndicator && payload.amount() > 0 && !payload.killed()) {
                            DamageIndicator.addIndicator(payload.entityId(), payload.posX(), payload.posY(), payload.posZ(), payload.amount(), payload.isCrit(), false);
                        }
                        if (config.showKillIndicator && payload.killed()) {
                            DamageIndicator.addIndicator(payload.entityId(), payload.posX(), payload.posY(), payload.posZ(), payload.amount(), false, true);
                        }
                    }
                }

                if (!isSelfDamage) {
                    if (config.recordOtherPlayers) {
                        DamageSessionManager.getInstance().addOtherPlayerDamage(payload.amount(), payload.isCrit(), payload.attackerId());
                    }
                    ctx.get().setPacketHandled(true);
                    return;
                }

                boolean preferSwitchTarget = false;
                try {
                    if (mc.hitResult instanceof EntityHitResult ehr) {
                        preferSwitchTarget = ehr.getEntity() != null && ehr.getEntity().getId() == payload.entityId();
                    }
                } catch (Exception ignored) {}

                DamageSessionManager.getInstance().addDamage(payload.amount(), payload.isCrit(), payload.entityId(), preferSwitchTarget);

                if (config.showDamageIndicator && payload.amount() > 0) {
                    DamageIndicator.addIndicator(payload.entityId(), payload.posX(), payload.posY(), payload.posZ(), payload.amount(), payload.isCrit(), payload.killed());
                }
                if (config.showKillIndicator && payload.killed()) {
                    DamageIndicator.addIndicator(payload.entityId(), payload.posX(), payload.posY(), payload.posZ(), payload.amount(), false, true);
                }

                if (config.debugShowRating && mc.player != null) {
                    RatingManager rm = RatingManager.getInstance();
                    if (rm.isVisible()) {
                        mc.player.displayClientMessage(
                            Component.literal("[DE Debug] ").withStyle(style -> style.withColor(TextColor.fromRgb(0xB3EDC4)))
                                .append(Component.literal("Rating: " + rm.getGrade() + " | Score: " + String.format("%.1f", rm.getScore())).withStyle(style -> style.withColor(TextColor.fromRgb(0xFFFFFF)))),
                            true
                        );
                    }
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    // --- C2S Handshake Packet Wrapper ---
    public static class HandshakePacket {
        public HandshakePacket() {}

        public static void encode(HandshakePacket msg, FriendlyByteBuf buf) {}

        public static HandshakePacket decode(FriendlyByteBuf buf) {
            return new HandshakePacket();
        }

        public static void handle(HandshakePacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                // The presence of a handler means the server has the mod installed.
                // No action needed on server side - the client detects this via the handshake response.
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
