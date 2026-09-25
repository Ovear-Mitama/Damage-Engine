package damage.engine.mixin.client;

import damage.engine.hud.DamageHud;
import damage.engine.hud.DamageIndicator;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// 26.2: 游戏内 HUD 渲染从 Gui 拆分到 Hud 类,extractRenderState 签名(GuiGraphicsExtractor, DeltaTracker)与原注入一致
@Mixin(Hud.class)
public class HudRenderMixin {

    // HUD 惯性要在原版 HUD 画之前算好,"整层 HUD"模式下还得先把偏移压进矩阵栈,
    // 这样后面所有层(原版 + Fabric HUD API 织进来的其它模组层)才会一起跟着让位。
    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void damageEngine$updateInertia(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        DamageHud.INSTANCE.updateHudInertia();
        DamageHud.INSTANCE.beginGlobalShift(guiGraphics);
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void damageEngine$onHudRender(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        try {
            // 此时整层偏移(若有)还在栈上,DE 自己的 HUD 也就跟着走了
            DamageHud.INSTANCE.onHudRender(guiGraphics, deltaTracker);
            DamageIndicator.render(guiGraphics, deltaTracker.getGameTimeDeltaPartialTick(false));
        } finally {
            DamageHud.INSTANCE.endGlobalShift(guiGraphics);
        }
    }

    // 暗角/传送门/望远镜这些是铺满整屏的渐变,属于屏幕特效而非 HUD。
    // 整屏渐变被平移几像素时感知上是"整个屏幕在晃",比 HUD 移动明显得多,所以这里抵消掉让位。
    @Inject(method = "extractCameraOverlays", at = @At("HEAD"))
    private void damageEngine$suspendShiftForScreenEffects(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        DamageHud.INSTANCE.suspendGlobalShift(guiGraphics);
    }

    @Inject(method = "extractCameraOverlays", at = @At("RETURN"))
    private void damageEngine$resumeShiftForScreenEffects(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        DamageHud.INSTANCE.resumeGlobalShift(guiGraphics);
    }
}
