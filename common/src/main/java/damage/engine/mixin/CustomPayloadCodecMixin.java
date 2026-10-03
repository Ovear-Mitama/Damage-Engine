package damage.engine.mixin;

import damage.engine.network.NetworkRegistry;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import java.util.ArrayList;
import java.util.List;

/**
 * Replaces the empty List.of() in ClientboundCustomPayloadPacket.&lt;clinit&gt;
 * with a list containing our custom S2C payload codecs.
 */
@Mixin(ClientboundCustomPayloadPacket.class)
public class CustomPayloadCodecMixin {

    @ModifyExpressionValue(
        method = "<clinit>",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/List;of()Ljava/util/List;"
        )
    )
    private static List<CustomPacketPayload.TypeAndCodec<?, ?>> addS2CTypes(List<CustomPacketPayload.TypeAndCodec<?, ?>> original) {
        List<CustomPacketPayload.TypeAndCodec<?, ?>> newTypes = new ArrayList<>();
        for (var entry : NetworkRegistry.getS2CCodecs().entrySet()) {
            @SuppressWarnings({"unchecked", "rawtypes"})
            CustomPacketPayload.TypeAndCodec<?, ?> typeAndCodec = new CustomPacketPayload.TypeAndCodec(entry.getKey(), entry.getValue());
            newTypes.add(typeAndCodec);
        }
        return newTypes;
    }
}
