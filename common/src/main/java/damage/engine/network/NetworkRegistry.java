package damage.engine.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

public class NetworkRegistry {
    private static final Map<CustomPacketPayload.Type<?>, StreamCodec<? super RegistryFriendlyByteBuf, ?>> S2C_CODECS = new HashMap<>();
    private static final Map<CustomPacketPayload.Type<?>, StreamCodec<? super RegistryFriendlyByteBuf, ?>> C2S_CODECS = new HashMap<>();
    private static final Map<CustomPacketPayload.Type<?>, BiConsumer<?, Object>> S2C_HANDLERS = new HashMap<>();
    private static final Map<CustomPacketPayload.Type<?>, BiConsumer<?, Object>> C2S_HANDLERS = new HashMap<>();

    public static <T extends CustomPacketPayload> void registerS2C(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, BiConsumer<T, Object> handler) {
        S2C_CODECS.put(type, (StreamCodec<? super RegistryFriendlyByteBuf, ?>) codec);
        S2C_HANDLERS.put(type, (BiConsumer<?, Object>) handler);
    }

    public static <T extends CustomPacketPayload> void registerC2S(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, BiConsumer<T, Object> handler) {
        C2S_CODECS.put(type, (StreamCodec<? super RegistryFriendlyByteBuf, ?>) codec);
        C2S_HANDLERS.put(type, (BiConsumer<?, Object>) handler);
    }

    /** 仅注册 codec，不注册处理器——用于不需要拦截的包（如握手） */
    public static <T extends CustomPacketPayload> void registerC2SCodec(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        C2S_CODECS.put(type, (StreamCodec<? super RegistryFriendlyByteBuf, ?>) codec);
    }

    public static Map<CustomPacketPayload.Type<?>, StreamCodec<? super RegistryFriendlyByteBuf, ?>> getS2CCodecs() {
        return S2C_CODECS;
    }

    public static Map<CustomPacketPayload.Type<?>, StreamCodec<? super RegistryFriendlyByteBuf, ?>> getC2SCodecs() {
        return C2S_CODECS;
    }

    public static BiConsumer<?, ?> getS2CHandler(CustomPacketPayload.Type<?> type) {
        return S2C_HANDLERS.get(type);
    }

    public static BiConsumer<?, ?> getC2SHandler(CustomPacketPayload.Type<?> type) {
        return C2S_HANDLERS.get(type);
    }

    /**
     * Look up a codec by id for decoding.
     */
    public static StreamCodec<? super RegistryFriendlyByteBuf, ?> getCodec(ResourceLocation id, boolean c2s) {
        Map<CustomPacketPayload.Type<?>, StreamCodec<? super RegistryFriendlyByteBuf, ?>> map = c2s ? C2S_CODECS : S2C_CODECS;
        for (Map.Entry<CustomPacketPayload.Type<?>, StreamCodec<? super RegistryFriendlyByteBuf, ?>> entry : map.entrySet()) {
            if (entry.getKey().id().equals(id)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
