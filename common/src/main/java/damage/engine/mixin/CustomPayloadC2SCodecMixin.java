package damage.engine.mixin;

import damage.engine.network.NetworkRegistry;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import java.util.ArrayList;
import java.util.List;

/**
 * Replaces the empty List.of() in ServerboundCustomPayloadPacket.&lt;clinit&gt;
 * with a list containing our custom C2S payload codecs.
 */
@Mixin(ServerboundCustomPayloadPacket.class)
public class CustomPayloadC2SCodecMixin {

    @ModifyExpressionValue(
        method = "<clinit>",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/List;of()Ljava/util/List;"
        )
    )
    private static List<CustomPacketPayload.TypeAndCodec<?, ?>> addC2STypes(List<CustomPacketPayload.TypeAndCodec<?, ?>> original) {
        List<CustomPacketPayload.TypeAndCodec<?, ?>> newTypes = new ArrayList<>();
        for (var entry : NetworkRegistry.getC2SCodecs().entrySet()) {
            @SuppressWarnings({"unchecked", "rawtypes"})
            CustomPacketPayload.TypeAndCodec<?, ?> typeAndCodec = new CustomPacketPayload.TypeAndCodec(entry.getKey(), entry.getValue());
            newTypes.add(typeAndCodec);
        }
        return newTypes;
    }
}
