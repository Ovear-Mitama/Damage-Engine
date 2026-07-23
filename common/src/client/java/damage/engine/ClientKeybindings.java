package damage.engine;

import net.minecraft.client.KeyMapping;

/**
 * Non-mixin holder for keybinding references.
 * DamageEngineClient creates and populates these, DamageConfigScreen reads from here.
 */
public class ClientKeybindings {
    public static KeyMapping configKeyBinding;
    public static KeyMapping toggleHudKeyBinding;
    public static KeyMapping clearDamageKeyBinding;
}
