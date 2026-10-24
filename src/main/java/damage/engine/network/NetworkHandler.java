package damage.engine.network;

import damage.engine.DamageEngine;
import damage.engine.DamageEngineClient;
import damage.engine.hud.session.DamageSessionManager;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/** 网络通道：服务端把每次命中发给造成伤害的玩家。 */
public final class NetworkHandler {
    public static final SimpleNetworkWrapper CHANNEL =
        NetworkRegistry.INSTANCE.newSimpleChannel(DamageEngine.MOD_ID);
    private static int nextId = 0;

    private NetworkHandler() {
    }

    public static void init() {
        CHANNEL.registerMessage(DamageHandler.class, DamageMessage.class, nextId++, Side.CLIENT);
    }

    public static final class DamageHandler implements IMessageHandler<DamageMessage, IMessage> {
        @Override
        public IMessage onMessage(DamageMessage message, MessageContext ctx) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
            mc.addScheduledTask(() -> DamageEngineClient.onDamage(message));
            return null;
        }
    }
}
