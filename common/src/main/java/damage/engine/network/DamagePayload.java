package damage.engine.network;

import damage.engine.DamageEngine;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DamagePayload(int entityId, float amount, boolean isCrit, int attackerId, String debugInfo, double posX, double posY, double posZ, boolean isProjectile, boolean killed) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<DamagePayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(DamageEngine.MOD_ID, "damage_packet"));
    
    public static final StreamCodec<RegistryFriendlyByteBuf, DamagePayload> STREAM_CODEC = StreamCodec.of(
        (buf, value) -> {
            buf.writeVarInt(value.entityId);
            buf.writeFloat(value.amount);
            buf.writeBoolean(value.isCrit);
            buf.writeVarInt(value.attackerId);
            buf.writeUtf(value.debugInfo);
            buf.writeDouble(value.posX);
            buf.writeDouble(value.posY);
            buf.writeDouble(value.posZ);
            buf.writeBoolean(value.isProjectile);
            buf.writeBoolean(value.killed);
        },
        buf -> new DamagePayload(
            buf.readVarInt(),
            buf.readFloat(),
            buf.readBoolean(),
            buf.readVarInt(),
            buf.readUtf(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readBoolean(),
            buf.readBoolean()
        )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
