package damage.engine.mixin;

import damage.engine.network.DamagePayload;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity {
    private static final Logger LOGGER = LoggerFactory.getLogger("damage-engine-mixin");

    public LivingEntityMixin(net.minecraft.entity.EntityType<?> type, net.minecraft.world.World world) {
        super(type, world);
    }

    @Inject(method = "applyDamage", at = @At("HEAD"))
    private void onApplyDamage(DamageSource source, float amount, CallbackInfo ci) {
        if (this.getWorld().isClient) return;
        
        // Debug Log
        LOGGER.info("applyDamage called for entity: {}, amount: {}", this.getName().getString(), amount);
        
        boolean isCrit = false;
        if (source.getAttacker() instanceof PlayerEntity player) {
             if (player.fallDistance > 0.0F && !player.isOnGround() && !player.isClimbing() && !player.isTouchingWater()) {
                 isCrit = true;
             }
        }
        
        int attackerId = -1;
        if (source.getAttacker() != null) {
            attackerId = source.getAttacker().getId();
        }
        
        if (amount > 0) {
            DamagePayload payload = new DamagePayload(this.getId(), amount, isCrit, attackerId);
            
            // Send to tracking players
            for (ServerPlayerEntity player : PlayerLookup.tracking(this)) {
                ServerPlayNetworking.send(player, payload);
            }
            
            // Send to self if player
            if ((Object)this instanceof ServerPlayerEntity selfPlayer) {
                ServerPlayNetworking.send(selfPlayer, payload);
            }
        }
    }
}
