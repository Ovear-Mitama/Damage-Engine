package damage.engine.mixin.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Drives the EditBox caret blink from wall-clock time instead of EditBox.tick().
 *
 * <p>MC's caret blinks when {@code frame / 6 % 2 == 0}, and {@code frame} only
 * advances in {@code EditBox.tick()}. Rows in a custom list never forward tick()
 * to their children, so the caret stays frozen. This mixin forces {@code frame}
 * to 0 (visible) or 7 (hidden, 7/6 % 2 == 1) right before rendering, giving a
 * steady ~0.6s blink cycle regardless of tick delivery.</p>
 */
@Mixin(EditBox.class)
public abstract class EditBoxCursorMixin {

    @Shadow
    private int frame;

    @Inject(method = "renderWidget", at = @At("HEAD"))
    private void damageEngine$timeDrivenBlink(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        this.frame = (System.currentTimeMillis() / 300L) % 2L == 0L ? 0 : 7;
    }
}
