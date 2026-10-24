package damage.engine;

import damage.engine.hud.session.DamageSessionManager;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

/** 客户端每帧/每 tick 的 HUD 与按键处理。 */
@Mod.EventBusSubscriber(modid = DamageEngine.MOD_ID, value = Side.CLIENT)
public final class ClientEvents {
    private ClientEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getMinecraft();

        while (DamageEngineClient.clearKey != null && DamageEngineClient.clearKey.isPressed()) {
            DamageEngineClient.clearAll();
        }
        while (DamageEngineClient.toggleHudKey != null && DamageEngineClient.toggleHudKey.isPressed()) {
            DamageEngineConfig config = DamageEngineConfig.getInstance();
            config.showDamage = !config.showDamage;
            config.save();
        }
        while (DamageEngineClient.configKey != null && DamageEngineClient.configKey.isPressed()) {
            mc.displayGuiScreen(new GuiDamageConfig());
        }

        if (!mc.isGamePaused()) {
            DamageSessionManager.getInstance().tick();
        }
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.TEXT) return;
        DamageHudRenderer.render(event.getPartialTicks());
    }
}
