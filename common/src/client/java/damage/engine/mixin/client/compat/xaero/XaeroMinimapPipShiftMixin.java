package damage.engine.mixin.client.compat.xaero;

import damage.engine.hud.DamageHud;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让 Xaero 小地图跟着"整层 HUD"惯性一起让位。
 * <p>
 * 小地图不走 GUI 的 pose 栈:它把小地图在屏幕上的位置交给 {@code MinimapPipRenderState.update(x, y, ...)}
 * 存成字段,再把这个 state 以绝对坐标提交给原版的 PictureInPicture 延迟渲染状态,最后由原版
 * {@code PictureInPictureRenderer.blitTexture} 在 GUI 绘制阶段贴出来。位置、裁剪区都由存的 x/y 推导,
 * 所以在 HUD 矩阵栈上压偏移对它无效,只能在它写好坐标后再补上同样的偏移。
 * <p>
 * 目标类用字符串指定:没装 Xaero 时该目标永远不会被加载,这个 mixin 也就不会被应用;注入再设为非致命
 * ({@code require = 0}),且配置本身 {@code required: false},这样 Xaero 改动内部结构时只是失效,不会崩游戏。
 */
@Mixin(targets = "xaero.hud.minimap.render.MinimapPipRenderState")
public class XaeroMinimapPipShiftMixin {

    @Shadow
    private int x;
    @Shadow
    private int y;
    /** scissorArea()/bounds() 返回的裁剪框,同样要跟着平移,否则小地图会被裁掉一角。 */
    @Shadow
    private ScreenRectangle rectangle;

    /** update() 已按 x/y 算好裁剪框,在返回前整体加上惯性偏移。宽高与 padding 不需要动,它们由 x/y 推导或保持不变。 */
    @Inject(method = "update", at = @At("RETURN"), require = 0)
    private void damageEngine$applyInertiaShift(CallbackInfoReturnable<Object> cir) {
        int dx = Math.round(DamageHud.INSTANCE.getGlobalShiftX());
        int dy = Math.round(DamageHud.INSTANCE.getGlobalShiftY());
        if (dx == 0 && dy == 0) {
            return;
        }
        this.x += dx;
        this.y += dy;
        if (this.rectangle != null) {
            this.rectangle = new ScreenRectangle(
                this.rectangle.left() + dx, this.rectangle.top() + dy,
                this.rectangle.width(), this.rectangle.height());
        }
    }
}
