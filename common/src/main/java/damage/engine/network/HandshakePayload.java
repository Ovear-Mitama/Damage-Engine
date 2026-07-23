package damage.engine.network;

import damage.engine.DamageEngine;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S handshake payload, used by the client to detect whether the server has Damage Engine installed.
 * The server registers a receiver for this payload in DamageEngine.onInitialize().
 * The client checks ClientPlayNetworking.canSend(HandshakePayload.ID) to determine server presence.
 */
public record HandshakePayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<HandshakePayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(DamageEngine.MOD_ID, "handshake"));
    
    public static final StreamCodec<RegistryFriendlyByteBuf, HandshakePayload> STREAM_CODEC = StreamCodec.of(
        (buf, value) -> {},
        buf -> new HandshakePayload()
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
