package damage.engine;

import damage.engine.hud.indicator.DamageIndicator;
import damage.engine.hud.rating.RatingManager;
import damage.engine.hud.session.DamageSessionManager;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/** 客户端命令：/damage_engine clear 清空当前伤害与评分数据。 */
@EventBusSubscriber(value = Dist.CLIENT, modid = DamageEngine.MOD_ID)
public final class ClientCommands {

    private ClientCommands() {
    }

    @SubscribeEvent
    static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("damage_engine")
            .then(Commands.literal("clear").executes(ctx -> {
                DamageSessionManager.getInstance().reset();
                RatingManager.getInstance().reset();
                DamageIndicator.clearAll();
                ctx.getSource().sendSuccess(
                    () -> Component.translatable("command.damage-engine.cleared"), false);
                return 1;
            })));
    }
}
