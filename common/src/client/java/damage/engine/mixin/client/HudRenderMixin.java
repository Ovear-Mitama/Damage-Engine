package damage.engine.mixin.client;

import damage.engine.hud.DamageHud;
import net.minecraft.client.DeltaTracker;
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
    private void damageEngine$onHudRender(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        damageHud.onHudRender(guiGraphics, deltaTracker);
        // 跳字不在这里画：它走 Anima 的世界渲染（见 DamageIndicator.renderWorld）
    }
}
