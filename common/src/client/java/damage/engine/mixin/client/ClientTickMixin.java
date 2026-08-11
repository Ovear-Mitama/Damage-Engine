package damage.engine.mixin.client;

import damage.engine.ClientKeybindings;
import damage.engine.DamageEngineConfig;
import damage.engine.DamageEngineClient;
import damage.engine.client.ClientAttackTracker;
import damage.engine.client.gui.HomeScreen;
import damage.engine.hud.DamageSessionManager;
import damage.engine.hud.DamageIndicator;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces Fabric API's ClientTickEvents.END_CLIENT_TICK with a direct mixin.
 */
@Mixin(Minecraft.class)
public class ClientTickMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void damageEngine$onEndClientTick(CallbackInfo ci) {
        Minecraft client = (Minecraft) (Object) this;
        
        if (client.player != null) {
            if (DamageEngineClient.joinCheckTicks > 0) {
                DamageEngineClient.joinCheckTicks--;
                if (DamageEngineClient.joinCheckTicks == 0) {
                    if (!DamageEngineClient.serverModChecked) {
                        if (client.getSingleplayerServer() != null) {
                            DamageEngineClient.serverHasMod = true;
                        } else {
                            DamageEngineClient.serverHasMod = false;
                        }
                        DamageEngineClient.serverModChecked = true;
                    }
                }
            }
            
            DamageEngineConfig config = DamageEngineConfig.getInstance();
            if (!config.hasShownWelcomeMessage) {
                client.player.displayClientMessage(
                    Component.translatable("text.damage-engine.welcome_message").withColor(0xB1EAC2),
                    false
                );
                config.hasShownWelcomeMessage = true;
                config.save();
            }
            
            if (ClientKeybindings.configKeyBinding != null) {
                while (ClientKeybindings.configKeyBinding.consumeClick()) {
                    client.setScreen(new HomeScreen(client.screen));
                }
            }
            if (ClientKeybindings.toggleHudKeyBinding != null) {
                while (ClientKeybindings.toggleHudKeyBinding.consumeClick()) {
                    config.showDamage = !config.showDamage;
                    config.save();
                }
            }
            if (ClientKeybindings.clearDamageKeyBinding != null) {
                while (ClientKeybindings.clearDamageKeyBinding.consumeClick()) {
                    DamageSessionManager.getInstance().reset();
                    DamageIndicator.clearAll();
                }
            }
        }

        if (!client.isPaused()) {
            DamageSessionManager.getInstance().tick();
            DamageIndicator.tickAndCleanup();
            ClientAttackTracker.getInstance().cleanup();
        }
    }
}
