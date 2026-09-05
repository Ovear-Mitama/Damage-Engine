package damage.engine.mixin.client;

import damage.engine.hud.DamageHud;
import damage.engine.hud.DamageIndicator;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// 26.2: 游戏内 HUD 渲染从 Gui 拆分到 Hud 类,extractRenderState 签名(GuiGraphicsExtractor, DeltaTracker)与原注入一致
@Mixin(Hud.class)
public class HudRenderMixin {

    private static final DamageHud damageHud = new DamageHud();

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void damageEngine$onHudRender(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        damageHud.onHudRender(guiGraphics, deltaTracker);
        DamageIndicator.render(guiGraphics, deltaTracker.getGameTimeDeltaPartialTick(false));
    }
}
