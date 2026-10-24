package damage.engine.network;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/** 服务端 -> 客户端：一次命中的伤害数据与命中位置。 */
public class DamageMessage implements IMessage {
    public int targetId;
    public float amount;
    public boolean crit;
    public boolean killed;
    public float distance;
    public boolean unseen;
    public double x;
    public double y;
    public double z;

    public DamageMessage() {
    }

    public DamageMessage(int targetId, float amount, boolean crit, boolean killed,
                         float distance, boolean unseen, double x, double y, double z) {
        this.targetId = targetId;
        this.amount = amount;
        this.crit = crit;
        this.killed = killed;
        this.distance = distance;
        this.unseen = unseen;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        targetId = buf.readInt();
        amount = buf.readFloat();
        crit = buf.readBoolean();
        killed = buf.readBoolean();
        distance = buf.readFloat();
        unseen = buf.readBoolean();
        x = buf.readDouble();
        y = buf.readDouble();
        z = buf.readDouble();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(targetId);
        buf.writeFloat(amount);
        buf.writeBoolean(crit);
        buf.writeBoolean(killed);
        buf.writeFloat(distance);
        buf.writeBoolean(unseen);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
    }
}
