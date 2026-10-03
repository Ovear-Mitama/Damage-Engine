package damage.engine.mixin.client.compat.xaero;

import damage.engine.hud.DamageHud;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让 Xaero 小地图跟着"整层 HUD"惯性一起让位。
 * <p>
 * 1.21.1 没有延迟 GUI 渲染状态:Xaero 在 {@code Gui.render} 的 HEAD
 * (beforeIngameGuiRender → handleRenderGameOverlayEventPre)就把整个 HUD 覆盖层画完了,
 * 而 DE 的整层偏移同样是在那个 HEAD 才压进矩阵栈的 —— 两者谁先执行由 mixin 应用顺序决定,
 * 实测 Xaero 更早,于是小地图拿到的是还没让位的矩阵。
 * <p>
 * 这里把 Xaero 的绘制入口自己包一层,按当前让位量平移,不再依赖注入顺序:该方法是 Xaero 自己的入口,
 * 没有其它注入跟它竞争。若这一帧 DE 的偏移已经压进矩阵栈(顺序反过来时),就不再叠加,避免让位量翻倍。
 * <p>
 * 目标类用字符串指定:没装 Xaero 时该目标永远不会被加载,这个 mixin 也就不会被应用;
 * 注入设为非致命({@code require = 0})且配置 {@code required: false},它改了内部结构时只是失效,不会崩游戏。
 */
@Mixin(targets = "xaero.common.events.ClientEvents")
public class XaeroHudShiftMixin {

    /** 本帧是否由这个 mixin 压了矩阵,决定 RETURN 时要不要弹。 */
    @Unique
    private boolean damageEngine$pushedShift;

    @Inject(method = "handleRenderGameOverlayEventPre", at = @At("HEAD"), require = 0)
    private void damageEngine$shiftBeforeXaeroHud(GuiGraphics guiGraphics, float partialTick, CallbackInfo ci) {
        this.damageEngine$pushedShift = false;
        if (DamageHud.INSTANCE.isGlobalShiftPushed()) {
            return;
        }
        float shiftX = DamageHud.INSTANCE.getInertiaShiftX();
        float shiftY = DamageHud.INSTANCE.getInertiaShiftY();
        if (shiftX == 0f && shiftY == 0f) {
            return;
        }
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(shiftX, shiftY, 0);
        this.damageEngine$pushedShift = true;
    }

    @Inject(method = "handleRenderGameOverlayEventPre", at = @At("RETURN"), require = 0)
    private void damageEngine$shiftAfterXaeroHud(GuiGraphics guiGraphics, float partialTick, CallbackInfo ci) {
        if (this.damageEngine$pushedShift) {
            guiGraphics.pose().popPose();
            this.damageEngine$pushedShift = false;
        }
    }
}
