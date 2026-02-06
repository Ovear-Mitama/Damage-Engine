package damage.engine.mixin;

import damage.engine.network.DamagePayload;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
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
    private void onApplyDamageHead(DamageSource source, float amount, CallbackInfo ci) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        if (self.getWorld().isClient) return;
        
        this.damageEngine$previousHealth = self.getHealth();
        this.damageEngine$previousAbsorption = self.getAbsorptionAmount();
        
        this.damageEngine$wasCrit = false;
        if (source.getAttacker() instanceof PlayerEntity attacker) {
             if (attacker.fallDistance > 0.0F && !attacker.isOnGround() && !attacker.isClimbing() && !attacker.isTouchingWater()) {
                 this.damageEngine$wasCrit = true;
             }
        }
        
        this.damageEngine$attackerId = -1;
        if (source.getAttacker() != null) {
            this.damageEngine$attackerId = source.getAttacker().getId();
        }
    }

    @Inject(method = "applyDamage", at = @At("RETURN"))
    private void onApplyDamageReturn(DamageSource source, float amount, CallbackInfo ci) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        if (self.getWorld().isClient) return;

        float currentHealth = self.getHealth();
        float currentAbsorption = self.getAbsorptionAmount();

        float healthLost = this.damageEngine$previousHealth - currentHealth;
        float absorptionLost = this.damageEngine$previousAbsorption - currentAbsorption;
        float actualDamage = healthLost + absorptionLost;
        
        // Debug Log
        LOGGER.info("Player: {}, Raw Amount: {}, Actual Damage: {} (HealthLost: {}, AbsLost: {})", 
            self.getName().getString(), amount, actualDamage, healthLost, absorptionLost);

        if (actualDamage > 0) {
            DamagePayload payload = new DamagePayload(self.getId(), actualDamage, this.damageEngine$wasCrit, this.damageEngine$attackerId);
            
            // Send to tracking players
            for (ServerPlayerEntity player : PlayerLookup.tracking(self)) {
                PacketByteBuf buf = PacketByteBufs.create();
                payload.encode(buf);
                ServerPlayNetworking.send(player, DamagePayload.ID, buf);
            }
            
            // Send to self (the victim)
            if (self instanceof ServerPlayerEntity selfPlayer) {
                PacketByteBuf buf = PacketByteBufs.create();
                payload.encode(buf);
                ServerPlayNetworking.send(selfPlayer, DamagePayload.ID, buf);
            }
        }
    }
}
