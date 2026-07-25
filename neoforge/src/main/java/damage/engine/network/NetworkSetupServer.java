package damage.engine.network;

import damage.engine.DamageEngine;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = DamageEngine.MOD_ID, value = Dist.DEDICATED_SERVER)
public class NetworkSetupServer {

    @SubscribeEvent
    static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");

        registrar.optional().playToClient(
            DamagePayload.TYPE,
            DamagePayload.STREAM_CODEC,
            (payload, ctx) -> {}
        );

        registrar.optional().playToServer(
            HandshakePayload.TYPE,
            HandshakePayload.STREAM_CODEC,
            (payload, ctx) -> {}
        );
    }
}
