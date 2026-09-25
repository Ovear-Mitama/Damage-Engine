package damage.engine.mixin.client;

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
        // 1.18.2 的矩阵类型是 com.mojang.math.Matrix4f(1.19.3 才换成 org.joml),
        // 字段 m00.. 是 protected,外部无法直接读取,这里做一份拷贝避免引用到会被复用的
        // 内部矩阵,调试输出改用 toString()。
        com.mojang.math.Matrix4f proj = com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix().copy();
        com.mojang.math.Matrix4f view = com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix().copy();
        DamageIndicator.captureMatrices(proj, view);
        // TEMP DEBUG: verify captured matrices
        org.slf4j.LoggerFactory.getLogger("damage-engine-forge").info(
            "[DE-FORGE] P={} V={}", proj.toString().replace('\n', ' '), view.toString().replace('\n', ' '));
    }
}
