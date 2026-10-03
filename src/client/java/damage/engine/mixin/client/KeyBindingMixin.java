package damage.engine.mixin.client;

import damage.engine.ClientKeybindings;
import net.minecraft.client.Options;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;

/**
 * Registers our custom keybindings by adding them to Options.keyMappings during initialization.
 */
@Mixin(Options.class)
public class KeyBindingMixin {

    @Shadow
    @Final
    @Mutable
    public KeyMapping[] keyMappings;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void damageEngine$registerKeyBindings(CallbackInfo ci) {
        ClientKeybindings.configKeyBinding = new KeyMapping(
            "key.damage_engine.config",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "key.category.damage-engine.general"
        );
        ClientKeybindings.toggleHudKeyBinding = new KeyMapping(
            "key.damage_engine.toggle_hud",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "key.category.damage-engine.general"
        );
        ClientKeybindings.clearDamageKeyBinding = new KeyMapping(
            "key.damage_engine.clear_damage",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "key.category.damage-engine.general"
        );

        KeyMapping[] newKeys = Arrays.copyOf(keyMappings, keyMappings.length + 3);
        newKeys[keyMappings.length] = ClientKeybindings.configKeyBinding;
        newKeys[keyMappings.length + 1] = ClientKeybindings.toggleHudKeyBinding;
        newKeys[keyMappings.length + 2] = ClientKeybindings.clearDamageKeyBinding;
        keyMappings = newKeys;
    }
}
