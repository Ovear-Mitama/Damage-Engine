package damage.engine.mixin.client;

import damage.engine.hud.DamageHud;
import damage.engine.hud.DamageIndicator;
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
    private void damageEngine$updateInertia(GuiGraphics guiGraphics, float partialTick, CallbackInfo ci) {
        DamageHud.INSTANCE.updateHudInertia();
        DamageHud.INSTANCE.beginGlobalShift(guiGraphics);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void damageEngine$onHudRender(GuiGraphics guiGraphics, float partialTick, CallbackInfo ci) {
        // TaCZ's crosshair hit feedback pollutes GL state (depth test / blend) and
        // never restores it; force a stable 2D state so our HUD does not flicker in
        // sync with the crosshair (mirrors the Forge-side event handler).
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        try {
            // 此时整层偏移(若有)还在栈上,DE 自己的 HUD 也就跟着走了
            DamageHud.INSTANCE.onHudRender(guiGraphics, partialTick);
            DamageIndicator.render(guiGraphics, partialTick);
        } finally {
            DamageHud.INSTANCE.endGlobalShift(guiGraphics);
        }
    }

    // 暗角/传送门/望远镜/结霜这些是铺满整屏的渐变,属于屏幕特效而非 HUD。
    // 整屏渐变被平移几像素时感知上是"整个屏幕在晃",比 HUD 移动明显得多,所以这里抵消掉让位。
    // 1.20.1 没有统一的相机特效方法,逐个包住;require = 0 表示找不到也不报错。
    @Inject(method = "renderVignette", at = @At("HEAD"), require = 0)
    private void damageEngine$suspendShiftForVignette(GuiGraphics guiGraphics, net.minecraft.world.entity.Entity entity, CallbackInfo ci) {
        DamageHud.INSTANCE.suspendGlobalShift(guiGraphics);
    }

    @Inject(method = "renderVignette", at = @At("RETURN"), require = 0)
    private void damageEngine$resumeShiftForVignette(GuiGraphics guiGraphics, net.minecraft.world.entity.Entity entity, CallbackInfo ci) {
        DamageHud.INSTANCE.resumeGlobalShift(guiGraphics);
    }

    @Inject(method = "renderPortalOverlay", at = @At("HEAD"), require = 0)
    private void damageEngine$suspendShiftForPortal(GuiGraphics guiGraphics, float strength, CallbackInfo ci) {
        DamageHud.INSTANCE.suspendGlobalShift(guiGraphics);
    }

    @Inject(method = "renderPortalOverlay", at = @At("RETURN"), require = 0)
    private void damageEngine$resumeShiftForPortal(GuiGraphics guiGraphics, float strength, CallbackInfo ci) {
        DamageHud.INSTANCE.resumeGlobalShift(guiGraphics);
    }

    @Inject(method = "renderSpyglassOverlay", at = @At("HEAD"), require = 0)
    private void damageEngine$suspendShiftForSpyglass(GuiGraphics guiGraphics, float scale, CallbackInfo ci) {
        DamageHud.INSTANCE.suspendGlobalShift(guiGraphics);
    }

    @Inject(method = "renderSpyglassOverlay", at = @At("RETURN"), require = 0)
    private void damageEngine$resumeShiftForSpyglass(GuiGraphics guiGraphics, float scale, CallbackInfo ci) {
        DamageHud.INSTANCE.resumeGlobalShift(guiGraphics);
    }

    @Inject(method = "renderTextureOverlay", at = @At("HEAD"), require = 0)
    private void damageEngine$suspendShiftForTexture(GuiGraphics guiGraphics, net.minecraft.resources.ResourceLocation texture, float alpha, CallbackInfo ci) {
        DamageHud.INSTANCE.suspendGlobalShift(guiGraphics);
    }

    @Inject(method = "renderTextureOverlay", at = @At("RETURN"), require = 0)
    private void damageEngine$resumeShiftForTexture(GuiGraphics guiGraphics, net.minecraft.resources.ResourceLocation texture, float alpha, CallbackInfo ci) {
        DamageHud.INSTANCE.resumeGlobalShift(guiGraphics);
    }
}
