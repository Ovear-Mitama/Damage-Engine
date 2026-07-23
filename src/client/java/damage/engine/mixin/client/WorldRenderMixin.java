package damage.engine.mixin.client;

import damage.engine.hud.DamageIndicator;
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

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderSnowAndRain(Lnet/minecraft/client/renderer/LightTexture;FDDD)V"))
    private void damageEngine$afterTranslucent(CallbackInfo ci) {
        DamageIndicator.captureProjection(new Matrix4f(com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix()));
    }
}
