package damage.engine.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import damage.engine.hud.DamageHud;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 成就/进度/教程这类弹窗（toast）由原版单独调用 {@code ToastComponent#render} 渲染，
 * 不在 {@code Gui#render} 里，所以「整层 HUD」模式的偏移管不到它们。
 * 这里补上同一次偏移，让它们和 HUD 一起让位。
 * <p>
 * 1.18.2 的类是 {@code ToastComponent}（{@code ToastManager} 是更晚的名字），
 * 方法签名是 {@code render(PoseStack)}（由 {@code Minecraft} 用一个新的 PoseStack 调用）。
 */
@Mixin(ToastComponent.class)
public class ToastShiftMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private void damageEngine$shiftToasts(PoseStack poseStack, CallbackInfo ci) {
        DamageHud.INSTANCE.beginGlobalShift(poseStack);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void damageEngine$unshiftToasts(PoseStack poseStack, CallbackInfo ci) {
        DamageHud.INSTANCE.endGlobalShift(poseStack);
    }
}
