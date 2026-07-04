package damage.engine.mixin.client;

import damage.engine.client.ClientAttackTracker;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client tick mixin that cleans up expired attack records and fixes serverHasMod detection.
 */
@Mixin(Minecraft.class)
public class ClientTickMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void onClientTickEnd(CallbackInfo ci) {
        ClientAttackTracker.getInstance().cleanup();
    }
}
