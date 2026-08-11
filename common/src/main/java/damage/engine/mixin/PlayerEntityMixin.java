package damage.engine.mixin;

import damage.engine.util.DamageTrackerHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.damagesource.DamageSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerEntityMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("damage-engine-mixin-player");

    @Unique
    private DamageTrackerHelper.Snapshot damageEngine$snap;

    @Inject(method = "hurtServer", at = @At("HEAD"), require = 0)
    private void onHurtHead(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> ci) {
        Player self = (Player) (Object) this;
        if (self.level().isClientSide()) return;

        damageEngine$snap = DamageTrackerHelper.capturePreDamage(self, source, amount);
    }

    @Inject(method = "hurtServer", at = @At("RETURN"), require = 0)
    private void onHurtReturn(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> ci) {
        Player self = (Player) (Object) this;
        if (self.level().isClientSide()) return;

        if (damageEngine$snap != null) {
            DamageTrackerHelper.broadcastPostDamage(self, source, damageEngine$snap, LOGGER);
            damageEngine$snap = null;
        }
    }
}
