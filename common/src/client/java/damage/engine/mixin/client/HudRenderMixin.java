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

    private static final DamageHud damageHud = new DamageHud();

    @Inject(method = "render", at = @At("TAIL"))
    private void damageEngine$onHudRender(GuiGraphics guiGraphics, float partialTick, CallbackInfo ci) {
        // TaCZ's crosshair hit feedback pollutes GL state (depth test / blend) and
        // never restores it; force a stable 2D state so our HUD does not flicker in
        // sync with the crosshair (mirrors the Forge-side event handler).
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        damageHud.onHudRender(guiGraphics, partialTick);
        DamageIndicator.render(guiGraphics, partialTick);
    }
}
