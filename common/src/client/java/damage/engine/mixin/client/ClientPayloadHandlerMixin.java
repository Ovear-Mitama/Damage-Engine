package damage.engine.mixin.client;

import damage.engine.network.NetworkRegistry;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BiConsumer;

/**
 * Routes received S2C custom payloads to our handlers.
 * Our codecs are injected into ClientboundCustomPayloadPacket via CustomPayloadCodecMixin,
 * so payloads arrive already decoded.
 */
@Mixin(ClientCommonPacketListenerImpl.class)
public class ClientPayloadHandlerMixin {

    @Inject(method = "handleCustomPayload", at = @At("HEAD"), cancellable = true)
    private void damageEngine$onCustomPayload(ClientboundCustomPayloadPacket packet, CallbackInfo ci) {
        CustomPacketPayload payload = packet.payload();
        BiConsumer<?, ?> handler = NetworkRegistry.getS2CHandler(payload.type());
        if (handler != null) {
            @SuppressWarnings("unchecked")
            BiConsumer<CustomPacketPayload, Object> typedHandler = (BiConsumer<CustomPacketPayload, Object>) handler;
            typedHandler.accept(payload, null);
            ci.cancel();
        }
    }
}
