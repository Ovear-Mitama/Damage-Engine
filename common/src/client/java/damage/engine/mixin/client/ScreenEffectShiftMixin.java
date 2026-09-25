package damage.engine.mixin.client;

import damage.engine.hud.DamageHud;
import net.minecraft.client.gui.Gui;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 整屏特效不吃「整层 HUD 让位」。
 * <p>
 * 暗角({@code renderVignette})、传送门({@code renderPortalOverlay})、望远镜
 * ({@code renderSpyglassOverlay})、结霜/其它满屏纹理({@code renderTextureOverlay})
 * 都不接收 PoseStack,用单位矩阵 + BufferBuilder 画顶点,于是会跟着模型视图矩阵一起平移。
 * 进世界时那层变暗过渡也是这一类,整屏跟着晃比 HUD 让位明显得多,所以这里在渲染这些特效的
 * 前后把整层偏移临时弹掉(见 DamageHud#suspendGlobalShift)。
 * <p>
 * 1.18.2 上这几个方法都是 Gui 的 private 方法,签名来自 javap。
 */
@Mixin(Gui.class)
public class ScreenEffectShiftMixin {

    @Inject(method = "renderVignette", at = @At("HEAD"))
    private void damageEngine$pauseShiftForVignette(Entity entity, CallbackInfo ci) {
        DamageHud.INSTANCE.suspendGlobalShift();
    }

    @Inject(method = "renderVignette", at = @At("RETURN"))
    private void damageEngine$resumeShiftAfterVignette(Entity entity, CallbackInfo ci) {
        DamageHud.INSTANCE.resumeGlobalShift();
    }

    @Inject(method = "renderPortalOverlay", at = @At("HEAD"))
    private void damageEngine$pauseShiftForPortal(float alpha, CallbackInfo ci) {
        DamageHud.INSTANCE.suspendGlobalShift();
    }

    @Inject(method = "renderPortalOverlay", at = @At("RETURN"))
    private void damageEngine$resumeShiftAfterPortal(float alpha, CallbackInfo ci) {
        DamageHud.INSTANCE.resumeGlobalShift();
    }

    @Inject(method = "renderSpyglassOverlay", at = @At("HEAD"))
    private void damageEngine$pauseShiftForSpyglass(float scale, CallbackInfo ci) {
        DamageHud.INSTANCE.suspendGlobalShift();
    }

    @Inject(method = "renderSpyglassOverlay", at = @At("RETURN"))
    private void damageEngine$resumeShiftAfterSpyglass(float scale, CallbackInfo ci) {
        DamageHud.INSTANCE.resumeGlobalShift();
    }

    @Inject(method = "renderTextureOverlay", at = @At("HEAD"))
    private void damageEngine$pauseShiftForTextureOverlay(ResourceLocation texture, float alpha, CallbackInfo ci) {
        DamageHud.INSTANCE.suspendGlobalShift();
    }

    @Inject(method = "renderTextureOverlay", at = @At("RETURN"))
    private void damageEngine$resumeShiftAfterTextureOverlay(ResourceLocation texture, float alpha, CallbackInfo ci) {
        DamageHud.INSTANCE.resumeGlobalShift();
    }
}
