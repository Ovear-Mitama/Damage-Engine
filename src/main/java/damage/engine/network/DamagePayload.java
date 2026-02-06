package damage.engine.network;

import damage.engine.DamageEngine;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record DamagePayload(int entityId, float amount, boolean isCrit, int attackerId) implements CustomPayload {
    public static final CustomPayload.Id<DamagePayload> ID = new CustomPayload.Id<>(Identifier.of(DamageEngine.MOD_ID, "damage_packet"));
    public static final PacketCodec<RegistryByteBuf, DamagePayload> CODEC = PacketCodec.tuple(
        PacketCodecs.VAR_INT, DamagePayload::entityId,
        PacketCodecs.FLOAT, DamagePayload::amount,
        PacketCodecs.BOOL, DamagePayload::isCrit,
        PacketCodecs.VAR_INT, DamagePayload::attackerId,
        DamagePayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
