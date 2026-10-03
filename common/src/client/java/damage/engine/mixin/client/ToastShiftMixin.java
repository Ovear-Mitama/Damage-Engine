package damage.engine.mixin.client;

import damage.engine.hud.DamageHud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.ToastManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 成就/进度/教程这类弹窗（toast）走的是 {@code ToastManager} 自己的渲染阶段，
 * 不在 {@code Gui#extractRenderState} 里，所以「整层 HUD」模式的偏移管不到它们。
 * 这里补上同一次偏移，让它们和 HUD 一起让位。
 */
@Mixin(ToastManager.class)
public class ToastShiftMixin {

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void damageEngine$shiftToasts(GuiGraphicsExtractor guiGraphics, CallbackInfo ci) {
        DamageHud.INSTANCE.beginGlobalShift(guiGraphics);
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void damageEngine$unshiftToasts(GuiGraphicsExtractor guiGraphics, CallbackInfo ci) {
        DamageHud.INSTANCE.endGlobalShift(guiGraphics);
    }
}
