package damage.engine;

import damage.engine.client.gui.DamageConfigScreen;
import damage.engine.network.DamagePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import damage.engine.hud.DamageHud;
import damage.engine.hud.DamageSessionManager;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DamageEngineClient implements ClientModInitializer {
    public static KeyBinding configKeyBinding;
    public static KeyBinding toggleHudKeyBinding;
    public static KeyBinding clearDamageKeyBinding;
    
    public static final Logger LOGGER = LoggerFactory.getLogger("damage-engine");
    private static final KeyBinding.Category KEYBIND_CATEGORY = KeyBinding.Category.create(Identifier.of(DamageEngine.MOD_ID, "general"));

    private static KeyBinding registerKeyBinding(String translationKey, int defaultKeyCode) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(
            translationKey,
            defaultKeyCode,
            KEYBIND_CATEGORY
        ));
    }
    
    public static void resetKeyBindings() {
        boolean wasShownFirstTimeMessage = DamageEngineConfig.getInstance().shownFirstTimeMessage;
        configKeyBinding.setBoundKey(InputUtil.UNKNOWN_KEY);
        toggleHudKeyBinding.setBoundKey(InputUtil.UNKNOWN_KEY);
        clearDamageKeyBinding.setBoundKey(InputUtil.UNKNOWN_KEY);
        DamageEngineConfig.getInstance().resetToDefaults();
        DamageEngineConfig.getInstance().shownFirstTimeMessage = wasShownFirstTimeMessage;
    }
    
    @Override
    public void onInitializeClient() {
        HudRenderCallback.EVENT.register(new DamageHud());

        configKeyBinding = registerKeyBinding("key.damage_engine.config", GLFW.GLFW_KEY_UNKNOWN);
        toggleHudKeyBinding = registerKeyBinding("key.damage_engine.toggle_hud", GLFW.GLFW_KEY_UNKNOWN);
        clearDamageKeyBinding = registerKeyBinding("key.damage_engine.clear_damage", GLFW.GLFW_KEY_UNKNOWN);
        
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            DamageEngineConfig config = DamageEngineConfig.getInstance();
            if (!config.shownFirstTimeMessage && client.player != null) {
                client.player.sendMessage(
                    Text.literal("[伤害引擎]").withColor(0xB1EAC2).append(Text.literal("当你在攻击实体时，屏幕上会显示伤害和相关生物信息，如果你想关闭，请打开该mod配置界面进行配置。").withColor(0xB1EAC2)),
                    false
                );
                config.shownFirstTimeMessage = true;
                config.save();
            }
        });
        
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null) {
                if (configKeyBinding != null) {
                    while (configKeyBinding.wasPressed()) {
                        client.setScreen(new DamageConfigScreen(client.currentScreen));
                    }
                }
                if (toggleHudKeyBinding != null) {
                    while (toggleHudKeyBinding.wasPressed()) {
                        DamageEngineConfig config = DamageEngineConfig.getInstance();
                        config.showDamage = !config.showDamage;
                        config.save();
                    }
                }
                if (clearDamageKeyBinding != null) {
                    while (clearDamageKeyBinding.wasPressed()) {
                        DamageSessionManager.getInstance().reset();
                    }
                }
            }

            if (!client.isPaused()) {
                DamageSessionManager.getInstance().tick();
            }
        });
        
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("damage_engine")
                .then(ClientCommandManager.literal("clear")
                    .executes(ctx -> {
                        DamageSessionManager.getInstance().reset();
                        if (ctx.getSource().getPlayer() != null) {
                            ctx.getSource().getPlayer().sendMessage(
                                Text.literal("[Damage Engine] ").withColor(0xB3EDC4).append(Text.translatable("text.damage-engine.cleared").withColor(0xFFFFFF)),
                                false
                            );
                        }
                        return 1;
                    }))
                .then(ClientCommandManager.literal("options")
                    .executes(ctx -> {
                        if (ctx.getSource().getClient() != null) {
                            ctx.getSource().getClient().execute(() -> ctx.getSource().getClient().setScreen(new DamageConfigScreen(ctx.getSource().getClient().currentScreen)));
                        }
                        return 1;
                    }))
            );
        });

        ClientPlayNetworking.registerGlobalReceiver(DamagePayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().world == null || context.client().player == null) return;
                
                if (DamageEngineConfig.getInstance().debugMode) {
                    if (payload.debugInfo() != null && !payload.debugInfo().isEmpty()) {
                        context.client().player.sendMessage(
                            Text.literal("[DE Debug] ").withColor(0xB3EDC4).append(Text.literal(payload.debugInfo()).withColor(0xFFFFFF)),
                            false
                        );
                    }
                }

                if (payload.attackerId() != context.client().player.getId()) {
                    return;
                }
                
                boolean preferSwitchTarget = false;
                try {
                    if (context.client().crosshairTarget instanceof net.minecraft.util.hit.EntityHitResult ehr) {
                        preferSwitchTarget = ehr.getEntity() != null && ehr.getEntity().getId() == payload.entityId();
                    }
                } catch (Exception ignored) {
                }
                
                DamageSessionManager.getInstance().addDamage(payload.amount(), payload.isCrit(), payload.entityId(), preferSwitchTarget);
            });
        });
    }
}
