package damage.engine.network;

import damage.engine.DamageEngine;
import damage.engine.util.DamageTrackerHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Consumer;
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

    /**
     * 客户端伤害包处理器。仅由客户端侧的 {@code DamageEngineClient} 注入；
     * 服务端保持为 null，从而保证本类（服务端也会加载）不引用任何客户端类。
     */
    public static Consumer<DamagePayload> clientPacketHandler = null;

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

    /** 发送握手包到服务端。仅客户端调用，由 {@link ClientPacketHandler} 转发。 */
    public static void sendHandshake() {
        CHANNEL.sendToServer(new HandshakePacket());
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
                // 该包只发给客户端；处理器由客户端在构造时注入，
                // 服务端为 null，因此不会触发任何客户端类的加载。
                Consumer<DamagePayload> handler = clientPacketHandler;
                if (handler != null) {
                    handler.accept(msg.getPayload());
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
