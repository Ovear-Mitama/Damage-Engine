package damage.engine.mixin.client;

import damage.engine.hud.DamageHud;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 成就/进度/教程这类弹窗（toast）由原版单独调用 {@code ToastComponent#render} 渲染，
 * 不在 {@code Gui#render} 里，所以「整层 HUD」模式的偏移管不到它们。
 * 这里补上同一次偏移，让它们和 HUD 一起让位。
 */
@Mixin(ToastComponent.class)
public class ToastShiftMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private void damageEngine$shiftToasts(GuiGraphics guiGraphics, CallbackInfo ci) {
        DamageHud.INSTANCE.beginGlobalShift(guiGraphics);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void damageEngine$unshiftToasts(GuiGraphics guiGraphics, CallbackInfo ci) {
        DamageHud.INSTANCE.endGlobalShift(guiGraphics);
    }
}
