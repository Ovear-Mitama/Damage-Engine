package damage.engine.mixin.client;

import damage.engine.client.ClientAttackTracker;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Records the target entity when a projectile hits an entity.
 */
@Mixin(Projectile.class)
public class ProjectileHitMixin {
    @Inject(method = "onHitEntity", at = @At("HEAD"))
    private void onProjectileHitEntity(EntityHitResult hitResult, CallbackInfo ci) {
        Projectile self = (Projectile) (Object) this;
        if (!self.level().isClientSide()) return;
        Entity owner = self.getOwner();
        if (owner == null) return;
        Entity target = hitResult.getEntity();
        if (target != null) {
            ClientAttackTracker.getInstance().recordAttack(target.getId());
        }
    }
}
