package damage.engine.mixin;

import damage.engine.util.AttackerAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.jetbrains.annotations.Nullable;

@Mixin(EndCrystal.class)
public class EndCrystalEntityMixin implements AttackerAccessor {

    @Unique
    private Entity damageEngine$attacker;

    @Override
    public @Nullable Entity damageEngine$getAttacker() {
        return this.damageEngine$attacker;
    }

    @Inject(method = "hurtServer", at = @At("HEAD"))
    private void onDamageHead(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (source != null) {
            Entity attacker = source.getEntity();
            if (attacker != null) {
                this.damageEngine$attacker = attacker;
            }
        }
    }
}
