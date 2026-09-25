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

    private static final DamageHud damageHud = new DamageHud();

    @Inject(method = "render", at = @At("TAIL"))
    private void damageEngine$onHudRender(PoseStack poseStack, float partialTick, CallbackInfo ci) {
        // TaCZ's crosshair hit feedback pollutes GL state (depth test / blend) and
        // never restores it; force a stable 2D state so our HUD does not flicker in
        // sync with the crosshair (mirrors the Forge-side event handler).
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        damageHud.onHudRender(poseStack, partialTick);
        DamageIndicator.render(poseStack, partialTick);
    }
}
