package damage.engine.mixin.client;

import damage.engine.client.ClientAttackTracker;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Records melee attacks for client-only mode (server without Damage Engine).
 *
 * <p>{@link Player#attack} / {@code Projectile.onHitEntity} only run server-side,
 * so on a remote server those mixins never fire and the attack tracker stays
 * empty - the client-only damage floats would never show. {@link
 * MultiPlayerGameMode#attack} is the CLIENT-side entry point that runs every
 * time the local player swings at an entity (both in singleplayer and on a
 * remote server), so it reliably marks the target as recently attacked.</p>
 */
@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {

    @Inject(method = "attack", at = @At("HEAD"))
    private void damageEngine$recordClientAttack(Player player, Entity target, CallbackInfoReturnable<Boolean> ci) {
        if (target != null) {
            ClientAttackTracker.getInstance().recordAttack(target.getId());
        }
    }
}
