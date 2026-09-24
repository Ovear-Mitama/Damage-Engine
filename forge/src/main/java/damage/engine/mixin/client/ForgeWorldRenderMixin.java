package damage.engine.mixin.client;

import com.mojang.math.Matrix4f;
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

    static {
        org.slf4j.LoggerFactory.getLogger("damage-engine-forge").info("[DE-FORGE] ForgeWorldRenderMixin class loaded");
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/multiplayer/ClientLevel;entitiesForRendering()Ljava/lang/Iterable;"))
    private void damageEngine$captureMatrices(CallbackInfo ci) {
        // 1.19 的矩阵类型是 com.mojang.math.Matrix4f(1.20 起才是 org.joml.Matrix4f)
        Matrix4f proj = new Matrix4f(com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix());
        Matrix4f view = new Matrix4f(com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix());
        DamageIndicator.captureMatrices(proj, view);
        // TEMP DEBUG: 1.19 的 com.mojang.math.Matrix4f 元素是 protected 字段(没有 m00() 这类
        // 取值方法),读不到具体数值,这里只确认矩阵确实捕获到了
        org.slf4j.LoggerFactory.getLogger("damage-engine-forge").info(
            "[DE-FORGE] captured matrices proj={} view={}", proj != null, view != null);
    }
}
