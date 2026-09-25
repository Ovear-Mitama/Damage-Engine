package damage.engine.mixin.client;

import damage.engine.hud.DamageHud;
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
        // 伤害跳字不再画在 HUD 上：改为交给 Anima 在世界渲染阶段画真 3D 文字
        // （见 DamageIndicator.renderWorld，由 DamageEngineClient 注册到 AnimaApi.onWorldRender）
    }
}
