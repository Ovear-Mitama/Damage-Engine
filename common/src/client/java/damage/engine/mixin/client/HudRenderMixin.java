package damage.engine.mixin.client;

import damage.engine.hud.DamageHud;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class HudRenderMixin {

    // HUD 惯性要在原版 HUD 画之前算好,"整层 HUD"模式下还得先把偏移压进矩阵栈,
    // 这样后面所有层(原版 + 其它模组织进来的 HUD 层)才会一起跟着让位。
    @Inject(method = "render", at = @At("HEAD"))
    private void damageEngine$updateInertia(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        DamageHud.INSTANCE.updateHudInertia();
        DamageHud.INSTANCE.beginGlobalShift(guiGraphics);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void damageEngine$onHudRender(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        try {
            // 此时整层偏移(若有)还在栈上,DE 自己的 HUD 也就跟着走了
            DamageHud.INSTANCE.onHudRender(guiGraphics, deltaTracker);
            // 跳字不在这里画：它走 Anima 的世界渲染（见 DamageIndicator.renderWorld）
        } finally {
            DamageHud.INSTANCE.endGlobalShift(guiGraphics);
        }
    }

    // 暗角/传送门/望远镜这些是铺满整屏的渐变,属于屏幕特效而非 HUD。
    // 整屏渐变被平移几像素时感知上是"整个屏幕在晃",比 HUD 移动明显得多,所以这里抵消掉让位。
    @Inject(method = "renderCameraOverlays", at = @At("HEAD"))
    private void damageEngine$suspendShiftForScreenEffects(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        DamageHud.INSTANCE.suspendGlobalShift(guiGraphics);
    }

    @Inject(method = "renderCameraOverlays", at = @At("RETURN"))
    private void damageEngine$resumeShiftForScreenEffects(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        DamageHud.INSTANCE.resumeGlobalShift(guiGraphics);
    }
}
