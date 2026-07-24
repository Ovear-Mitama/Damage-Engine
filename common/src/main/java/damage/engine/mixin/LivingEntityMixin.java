package damage.engine.mixin;

import damage.engine.util.DamageTrackerHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity {
    private static final Logger LOGGER = LoggerFactory.getLogger("damage-engine-mixin");

    @Unique
    private DamageTrackerHelper.Snapshot damageEngine$snap;

    public LivingEntityMixin(EntityType<?> type, Level world) {
        super(type, world);
    }

    @Inject(method = "hurt", at = @At("HEAD"), require = 0)
    private void onHurtHead(DamageSource source, float amount, CallbackInfoReturnable<Boolean> ci) {
        if (this.level().isClientSide()) return;
        if ((Object) this instanceof Player) return;

        damageEngine$snap = DamageTrackerHelper.capturePreDamage((LivingEntity) (Object) this, source);
    }

    @Inject(method = "hurt", at = @At("RETURN"), require = 0)
    private void onHurtReturn(DamageSource source, float amount, CallbackInfoReturnable<Boolean> ci) {
        if (this.level().isClientSide()) return;
        if ((Object) this instanceof Player) return;

        if (damageEngine$snap != null) {
            DamageTrackerHelper.broadcastPostDamage((LivingEntity) (Object) this, source, damageEngine$snap, LOGGER);
            damageEngine$snap = null;
        }
    }
}
