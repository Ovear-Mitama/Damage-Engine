package damage.engine.mixin;

import damage.engine.network.NetworkRegistry;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BiConsumer;

/**
 * Routes received C2S custom payloads to our handlers.
 */
@Mixin(ServerCommonPacketListenerImpl.class)
public class ServerPayloadHandlerMixin {

    @Inject(method = "handleCustomPayload", at = @At("HEAD"), cancellable = true)
    private void damageEngine$onCustomPayload(ServerboundCustomPayloadPacket packet, CallbackInfo ci) {
        CustomPacketPayload payload = packet.payload();
        BiConsumer<?, ?> handler = NetworkRegistry.getC2SHandler(payload.type());
        if (handler != null) {
            @SuppressWarnings("unchecked")
            BiConsumer<CustomPacketPayload, Object> typedHandler = (BiConsumer<CustomPacketPayload, Object>) handler;
            typedHandler.accept(payload, this);
            ci.cancel();
        }
    }
}
