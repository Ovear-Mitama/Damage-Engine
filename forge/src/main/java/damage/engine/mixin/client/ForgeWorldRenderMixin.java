package damage.engine.mixin.client;

import org.joml.Matrix4f;
import damage.engine.hud.DamageIndicator;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Captures the game's real view+projection matrices right before entities are
 * drawn (the entity loop starts right after ClientLevel.entitiesForRendering()).
 * At that point the RenderSystem modelview is the camera view matrix, so the
 * damage floats project exactly like the world.
 */
@Mixin(LevelRenderer.class)
public class ForgeWorldRenderMixin {

    @Inject(method = "renderLevel", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/multiplayer/ClientLevel;entitiesForRendering()Ljava/lang/Iterable;"))
    private void damageEngine$captureMatrices(CallbackInfo ci) {
        Matrix4f proj = new Matrix4f(com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix());
        Matrix4f view = new Matrix4f(com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix());
        DamageIndicator.captureMatrices(proj, view);
    }
}
