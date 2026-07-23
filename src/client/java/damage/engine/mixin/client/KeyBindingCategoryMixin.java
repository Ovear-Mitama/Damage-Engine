package damage.engine.mixin.client;

import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * Registers the Damage Engine keybinding category in the CATEGORY_SORT_ORDER
 * so that KeyMapping.compareTo() works correctly in the vanilla keybinds screen.
 */
@Mixin(KeyMapping.class)
public class KeyBindingCategoryMixin {

    @Shadow
    @Final
    @Mutable
    private static Map<String, Integer> CATEGORY_SORT_ORDER;

    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void damageEngine$registerCategory(CallbackInfo ci) {
        CATEGORY_SORT_ORDER.put("key.category.damage-engine.general", CATEGORY_SORT_ORDER.size());
    }
}
