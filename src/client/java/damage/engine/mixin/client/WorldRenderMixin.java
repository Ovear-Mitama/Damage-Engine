package damage.engine.mixin.client;

import damage.engine.hud.DamageIndicator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces Fabric API's WorldRenderEvents.AFTER_TRANSLUCENT with a direct mixin.
 */
@Mixin(LevelRenderer.class)
public class WorldRenderMixin {

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void damageEngine$afterTranslucent(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        DamageIndicator.captureProjection(new Matrix4f(mc.gameRenderer.getProjectionMatrix(mc.options.fov().get())));
    }
}
