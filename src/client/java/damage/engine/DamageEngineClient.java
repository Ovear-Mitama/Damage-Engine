package damage.engine;

import damage.engine.client.gui.DamageConfigScreen;
import damage.engine.network.DamagePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import damage.engine.hud.DamageHud;
import damage.engine.hud.DamageSessionManager;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.text.Text;

public class DamageEngineClient implements ClientModInitializer {
    public static KeyBinding configKeyBinding;
    public static KeyBinding toggleHudKeyBinding;

    @Override
    public void onInitializeClient() {
        // 注册 HUD
        HudRenderCallback.EVENT.register(new DamageHud());

        // 注册按键绑定
        configKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.damage_engine.config",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "category.damage_engine"
        ));
        
        toggleHudKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.damage_engine.toggle_hud",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN, // 默认未绑定
            "category.damage_engine"
        ));

        // 注册 Tick 事件
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null) {
                while (configKeyBinding.wasPressed()) {
                    client.setScreen(new DamageConfigScreen(client.currentScreen));
                }
                while (toggleHudKeyBinding.wasPressed()) {
                    DamageEngineConfig config = DamageEngineConfig.getInstance();
                    config.showDamage = !config.showDamage;
                    config.save();
                }
            }

            if (!client.isPaused()) {
                DamageSessionManager.getInstance().tick();
            }
        });

        // 注册数据包接收器
        ClientPlayNetworking.registerGlobalReceiver(DamagePayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().world == null || context.client().player == null) return;
                
                // 调试日志
                if (DamageEngineConfig.getInstance().debugMode) {
                    if (payload.debugInfo() != null && !payload.debugInfo().isEmpty()) {
                        context.client().player.sendMessage(Text.literal("[调试] ").withColor(0xFBFB54).append(Text.literal(payload.debugInfo()).withColor(0xFFFFFF)), false);
                    }
                }

                // 过滤：仅显示玩家造成的伤害
                if (payload.attackerId() != context.client().player.getId()) {
                    return;
                }
                
                // 添加到 HUD
                DamageSessionManager.getInstance().addDamage(payload.amount(), payload.isCrit());
            });
        });
    }
}
