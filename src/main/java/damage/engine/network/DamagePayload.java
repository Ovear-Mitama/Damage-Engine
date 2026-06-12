package damage.engine.network;

import damage.engine.DamageEngine;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record DamagePayload(int entityId, float amount, boolean isCrit, int attackerId, String debugInfo, double posX, double posY, double posZ, boolean isProjectile, boolean killed) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<DamagePayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(DamageEngine.MOD_ID, "damage_packet"));
    
    public static final StreamCodec<RegistryFriendlyByteBuf, DamagePayload> CODEC = new StreamCodec<>() {
        @Override
        public DamagePayload decode(RegistryFriendlyByteBuf buf) {
            return new DamagePayload(
                buf.readVarInt(),
                buf.readFloat(),
                buf.readBoolean(),
                buf.readVarInt(),
                ByteBufCodecs.STRING_UTF8.decode(buf),
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readBoolean(),
                buf.readBoolean()
            );
        }
        
        @Override
        public void encode(RegistryFriendlyByteBuf buf, DamagePayload value) {
            buf.writeVarInt(value.entityId);
            buf.writeFloat(value.amount);
            buf.writeBoolean(value.isCrit);
            buf.writeVarInt(value.attackerId);
            ByteBufCodecs.STRING_UTF8.encode(buf, value.debugInfo);
            buf.writeDouble(value.posX);
            buf.writeDouble(value.posY);
            buf.writeDouble(value.posZ);
            buf.writeBoolean(value.isProjectile);
            buf.writeBoolean(value.killed);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
