package damage.engine.mixin.client;

import damage.engine.hud.DamageIndicator;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
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

    static {
        org.slf4j.LoggerFactory.getLogger("damage-engine-forge").info("[DE-FORGE] ForgeWorldRenderMixin class loaded");
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/multiplayer/ClientLevel;entitiesForRendering()Ljava/lang/Iterable;"))
    private void damageEngine$captureMatrices(CallbackInfo ci) {
        org.joml.Matrix4f proj = new org.joml.Matrix4f(com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix());
        org.joml.Matrix4f view = new org.joml.Matrix4f(com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix());
        DamageIndicator.captureMatrices(proj, view);
        // TEMP DEBUG: verify captured matrices
        float colLen = (float) Math.sqrt(view.m00() * view.m00() + view.m01() * view.m01() + view.m02() * view.m02());
        org.slf4j.LoggerFactory.getLogger("damage-engine-forge").info(
            "[DE-FORGE] P[m00={} m11={}] V[m00={} m01={} m10={} m30={}] colLen={}",
            String.format("%.3f", proj.m00()), String.format("%.3f", proj.m11()),
            String.format("%.3f", view.m00()), String.format("%.3f", view.m01()),
            String.format("%.3f", view.m10()), String.format("%.3f", view.m30()),
            String.format("%.3f", colLen));
    }
}
