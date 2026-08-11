package damage.engine.mixin.client;

import damage.engine.client.ClientAttackTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tracks the local player's melee attacks so ClientHealthTracker can filter
 * for damage actually caused by the player in client-only mode.
 */
@Mixin(Player.class)
public class PlayerAttackMixin {
    @Inject(method = "attack", at = @At("HEAD"))
    private void damageEngine$onAttack(Entity target, CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (self == Minecraft.getInstance().player && target != null) {
            ClientAttackTracker.getInstance().recordAttack(target.getId());
        }
    }
}
