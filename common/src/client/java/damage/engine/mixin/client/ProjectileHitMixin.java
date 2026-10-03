package damage.engine.mixin.client;

import damage.engine.client.ClientAttackTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tracks projectile hits for projectiles owned by the local player,
 * so ClientHealthTracker can filter for damage actually caused by the player.
 */
@Mixin(Projectile.class)
public class ProjectileHitMixin {
    @Inject(method = "onHitEntity", at = @At("HEAD"))
    private void damageEngine$onHitEntity(EntityHitResult hitResult, CallbackInfo ci) {
        Projectile self = (Projectile) (Object) this;
        Entity owner = self.getOwner();
        Entity target = hitResult.getEntity();
        if (owner != null && owner == Minecraft.getInstance().player && target != null) {
            ClientAttackTracker.getInstance().recordAttack(target.getId());
        }
    }
}
