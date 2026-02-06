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

public class DamageEngineClient implements ClientModInitializer {
    public static KeyBinding configKeyBinding;
    public static KeyBinding toggleHudKeyBinding;

    @Override
    public void onInitializeClient() {
        // Register HUD
        HudRenderCallback.EVENT.register(new DamageHud());

        // Register KeyBindings
        configKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.damage_engine.config",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "category.damage_engine"
        ));
        
        toggleHudKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.damage_engine.toggle_hud",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN, // Unbound by default
            "category.damage_engine"
        ));

        // Register Tick
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

        // Register Packet Receiver
        ClientPlayNetworking.registerGlobalReceiver(DamagePayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().world == null || context.client().player == null) return;
                
                // Debug Log
                System.out.println("Damage Payload Received: Amount=" + payload.amount() + ", Attacker=" + payload.attackerId() + ", Player=" + context.client().player.getId());

                // Filter: Only show damage caused by the player
                if (payload.attackerId() != context.client().player.getId()) {
                    return;
                }
                
                // Add to HUD
                DamageSessionManager.getInstance().addDamage(payload.amount(), payload.isCrit());
            });
        });
    }
}
