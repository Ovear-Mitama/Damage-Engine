package damage.engine.network;

import damage.engine.DamageEngine;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public record DamagePayload(int entityId, float amount, boolean isCrit, int attackerId) {
    public static final Identifier ID = new Identifier(DamageEngine.MOD_ID, "damage_packet");

    public static DamagePayload decode(PacketByteBuf buf) {
        return new DamagePayload(buf.readVarInt(), buf.readFloat(), buf.readBoolean(), buf.readVarInt());
    }

    public void encode(PacketByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeFloat(amount);
        buf.writeBoolean(isCrit);
        buf.writeVarInt(attackerId);
    }
}
