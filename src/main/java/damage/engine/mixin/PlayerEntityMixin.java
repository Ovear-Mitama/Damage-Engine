package damage.engine.mixin;

import damage.engine.network.DamagePayload;
import damage.engine.util.AttackerAccessor;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Ownable;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("damage-engine-mixin-player");

    @Unique
    private float damageEngine$previousHealth;
    @Unique
    private float damageEngine$previousAbsorption;
    @Unique
    private boolean damageEngine$wasCrit;
    @Unique
    private int damageEngine$attackerId;

    @Inject(method = "applyDamage", at = @At("HEAD"))
    private void onApplyDamageHead(ServerWorld world, DamageSource source, float amount, CallbackInfo ci) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        if (self.getEntityWorld().isClient()) return;
        
        this.damageEngine$previousHealth = self.getHealth();
        this.damageEngine$previousAbsorption = self.getAbsorptionAmount();
        
        this.damageEngine$wasCrit = false;
        if (source.getAttacker() instanceof PlayerEntity attacker) {
             if (attacker.fallDistance > 0.0F && !attacker.isOnGround() && !attacker.isClimbing() && !attacker.isTouchingWater()) {
                 this.damageEngine$wasCrit = true;
             }
        }
        
        this.damageEngine$attackerId = -1;
        Entity attacker = source.getAttacker();
        
        if (attacker == null) {
            Entity directSource = source.getSource();
            if (directSource instanceof Ownable ownable) {
                attacker = ownable.getOwner();
            } else if (directSource instanceof net.minecraft.entity.decoration.EndCrystalEntity crystal) {
                if (crystal instanceof AttackerAccessor accessor) {
                    attacker = accessor.damageEngine$getAttacker();
                }
            }
        }
        
        if (attacker == null && (source.isOf(DamageTypes.ON_FIRE) || source.isOf(DamageTypes.IN_FIRE))) {
            attacker = ((LivingEntity)(Object)this).getAttacker();
        }

        if (attacker != null) {
            this.damageEngine$attackerId = attacker.getId();
        }
    }

    @Inject(method = "applyDamage", at = @At("RETURN"))
    private void onApplyDamageReturn(ServerWorld world, DamageSource source, float amount, CallbackInfo ci) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        if (self.getEntityWorld().isClient()) return;

        float currentHealth = self.getHealth();
        float currentAbsorption = self.getAbsorptionAmount();

        float healthLost = this.damageEngine$previousHealth - currentHealth;
        float absorptionLost = this.damageEngine$previousAbsorption - currentAbsorption;
        float actualDamage = healthLost + absorptionLost;
        
        String debugInfo = "";
        if (damage.engine.DamageEngineConfig.getInstance().debugMode) {
            String attackerName = source.getName();
            if (this.damageEngine$attackerId != 0) {
                Entity attacker = world.getEntityById(this.damageEngine$attackerId);
                if (attacker != null) {
                    attackerName = attacker.getName().getString();
                }
            }
            debugInfo = String.format("%s 造成伤害%.1f 伤害对象 %s", attackerName, actualDamage, self.getName().getString());
            LOGGER.info(debugInfo);
        }

        if (actualDamage > 0) {
            DamagePayload payload = new DamagePayload(self.getId(), actualDamage, this.damageEngine$wasCrit, this.damageEngine$attackerId, debugInfo);
            
            for (ServerPlayerEntity player : PlayerLookup.tracking(self)) {
                ServerPlayNetworking.send(player, payload);
            }
            
            if (self instanceof ServerPlayerEntity selfPlayer) {
                ServerPlayNetworking.send(selfPlayer, payload);
            }
        }
    }
}
