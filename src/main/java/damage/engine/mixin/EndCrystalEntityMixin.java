package damage.engine.mixin;

import damage.engine.util.AttackerAccessor;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.jetbrains.annotations.Nullable;

@Mixin(EndCrystalEntity.class)
public class EndCrystalEntityMixin implements AttackerAccessor {

    @Unique
    private Entity damageEngine$attacker;

    @Override
    public @Nullable Entity damageEngine$getAttacker() {
        return this.damageEngine$attacker;
    }

    @Inject(method = "damage", at = @At("HEAD"))
    private void onDamageHead(ServerWorld world, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (source != null) {
            Entity attacker = source.getAttacker();
            if (attacker != null) {
                this.damageEngine$attacker = attacker;
            }
        }
    }
}
