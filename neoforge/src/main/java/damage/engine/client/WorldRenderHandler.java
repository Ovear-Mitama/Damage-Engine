package damage.engine.client;

import damage.engine.DamageEngine;
import damage.engine.hud.DamageIndicator;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

@EventBusSubscriber(modid = DamageEngine.MOD_ID, value = Dist.CLIENT)
public class WorldRenderHandler {

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            DamageIndicator.captureProjection(new Matrix4f(event.getProjectionMatrix()));
        }
    }
}
