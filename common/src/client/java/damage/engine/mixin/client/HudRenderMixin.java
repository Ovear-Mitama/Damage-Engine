package damage.engine.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import damage.engine.hud.DamageHud;
import damage.engine.hud.DamageIndicator;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * HUD 渲染回调:1.18 的 {@code Gui#render(PoseStack, float)} 只给 PoseStack
 * (1.20 才改成 GuiGraphics),直接把它转交给 HUD 绘制即可。
 */
@Mixin(Gui.class)
public class HudRenderMixin {

    // HUD 惯性要在原版 HUD 画之前算好,"整层 HUD"模式下还得先把偏移压进矩阵栈,
    // 这样后面所有层(原版 + 其它模组织进来的 HUD 层)才会一起跟着让位。
    @Inject(method = "render", at = @At("HEAD"))
    private void damageEngine$updateInertia(PoseStack poseStack, float partialTick, CallbackInfo ci) {
        DamageHud.INSTANCE.updateHudInertia();
        DamageHud.INSTANCE.beginGlobalShift(poseStack);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void damageEngine$onHudRender(PoseStack poseStack, float partialTick, CallbackInfo ci) {
        // TaCZ's crosshair hit feedback pollutes GL state (depth test / blend) and
        // never restores it; force a stable 2D state so our HUD does not flicker in
        // sync with the crosshair (mirrors the Forge-side event handler).
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        try {
            // 此时整层偏移(若有)还在栈上,DE 自己的 HUD 也就跟着走了
            DamageHud.INSTANCE.onHudRender(poseStack, partialTick);
            DamageIndicator.render(poseStack, partialTick);
        } finally {
            DamageHud.INSTANCE.endGlobalShift(poseStack);
        }
    }

    // 暗角/传送门/望远镜/结霜以及进世界时那层变暗过渡都属于"屏幕特效"而不是 HUD:它们不接收
    // PoseStack、用单位矩阵画顶点,会跟着模型视图矩阵一起平移,整屏跟着晃比 HUD 让位明显得多。
    // 这些由 ScreenEffectShiftMixin 在渲染前后调用 suspendGlobalShift/resumeGlobalShift 抵消掉。
}
