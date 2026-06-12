package damage.engine.network;

import damage.engine.DamageEngine;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * C2S handshake payload, used by the client to detect whether the server has Damage Engine installed.
 * The server registers a receiver for this payload in DamageEngine.onInitialize().
 * The client checks ClientPlayNetworking.canSend(HandshakePayload.ID) to determine server presence.
 */
public record HandshakePayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<HandshakePayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(DamageEngine.MOD_ID, "handshake"));
    
    public static final StreamCodec<RegistryFriendlyByteBuf, HandshakePayload> CODEC = new StreamCodec<>() {
        @Override
        public HandshakePayload decode(RegistryFriendlyByteBuf buf) {
            return new HandshakePayload();
        }
        
        @Override
        public void encode(RegistryFriendlyByteBuf buf, HandshakePayload value) {
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
