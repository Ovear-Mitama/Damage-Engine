package damage.engine.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.decoration.EndCrystalEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.jetbrains.annotations.Nullable;

@Mixin(EndCrystalEntity.class)
public class EndCrystalEntityMixin {

    @Unique
    private Entity damageEngine$attacker;

    public @Nullable Entity damageEngine$getAttacker() {
        return this.damageEngine$attacker;
    }

    @Inject(method = "damage", at = @At("HEAD"))
    private void onDamageHead(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (source != null) {
            Entity attacker = source.getAttacker();
            if (attacker != null) {
                this.damageEngine$attacker = attacker;
            }
        }
    }
}
