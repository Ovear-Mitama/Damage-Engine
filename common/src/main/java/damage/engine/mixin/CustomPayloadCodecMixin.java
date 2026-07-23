package damage.engine.mixin;

import damage.engine.network.NetworkRegistry;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.ArrayList;
import java.util.List;

/**
 * Injects our custom payload types into the vanilla ClientboundCustomPayloadPacket codec
 * so they can be decoded correctly on the receiving end.
 */
@Mixin(ClientboundCustomPayloadPacket.class)
public class CustomPayloadCodecMixin {

    @ModifyArg(
        method = "<clinit>",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;codec(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload$FallbackProvider;Ljava/util/List;Lnet/minecraft/network/ConnectionProtocol;Lnet/minecraft/network/protocol/PacketFlow;)Lnet/minecraft/network/codec/StreamCodec;"
        ),
        index = 1
    )
    private static List<CustomPacketPayload.TypeAndCodec<?, ?>> modifyS2CTypes(List<CustomPacketPayload.TypeAndCodec<?, ?>> types) {
        List<CustomPacketPayload.TypeAndCodec<?, ?>> newTypes = new ArrayList<>(types);
        for (var entry : NetworkRegistry.getS2CCodecs().entrySet()) {
            @SuppressWarnings({"unchecked", "rawtypes"})
            CustomPacketPayload.TypeAndCodec<?, ?> typeAndCodec = new CustomPacketPayload.TypeAndCodec(entry.getKey(), entry.getValue());
            newTypes.add(typeAndCodec);
        }
        return newTypes;
    }
}
