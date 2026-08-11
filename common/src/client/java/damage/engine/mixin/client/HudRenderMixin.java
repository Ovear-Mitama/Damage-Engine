package damage.engine.mixin.client;

import damage.engine.hud.DamageHud;
import damage.engine.hud.DamageIndicator;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class HudRenderMixin {

    private static final DamageHud damageHud = new DamageHud();

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void damageEngine$onHudRender(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        damageHud.onHudRender(guiGraphics, deltaTracker);
        DamageIndicator.render(guiGraphics, deltaTracker.getGameTimeDeltaPartialTick(false));
    }
}
