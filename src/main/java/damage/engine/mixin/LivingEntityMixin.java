package damage.engine.mixin;

import damage.engine.network.DamagePayload;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
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

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity {
    private static final Logger LOGGER = LoggerFactory.getLogger("damage-engine-mixin");

    @Unique
    private float damageEngine$previousHealth;
    @Unique
    private float damageEngine$previousAbsorption;
    @Unique
    private boolean damageEngine$wasCrit;
    @Unique
    private int damageEngine$attackerId;

    public LivingEntityMixin(net.minecraft.entity.EntityType<?> type, net.minecraft.world.World world) {
        super(type, world);
    }

    @Inject(method = "applyDamage", at = @At("HEAD"))
    private void onApplyDamageHead(DamageSource source, float amount, CallbackInfo ci) {
        if (this.getWorld().isClient) return;
        if ((Object)this instanceof PlayerEntity) return;
        
        LivingEntity self = (LivingEntity) (Object) this;
        this.damageEngine$previousHealth = self.getHealth();
        this.damageEngine$previousAbsorption = self.getAbsorptionAmount();
        
        this.damageEngine$wasCrit = false;
        if (source.getAttacker() instanceof PlayerEntity player) {
             if (player.fallDistance > 0.0F && !player.isOnGround() && !player.isClimbing() && !player.isTouchingWater()) {
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
        if (this.getWorld().isClient) return;
        if ((Object)this instanceof PlayerEntity) return;

        LivingEntity self = (LivingEntity) (Object) this;
        float currentHealth = self.getHealth();
        float currentAbsorption = self.getAbsorptionAmount();

        float healthLost = this.damageEngine$previousHealth - currentHealth;
        float absorptionLost = this.damageEngine$previousAbsorption - currentAbsorption;
        float actualDamage = healthLost + absorptionLost;
        
        // Debug Log
        LOGGER.info("Entity: {}, Raw Amount: {}, Actual Damage: {} (HealthLost: {}, AbsLost: {})", 
            self.getName().getString(), amount, actualDamage, healthLost, absorptionLost);

        if (actualDamage > 0) {
            DamagePayload payload = new DamagePayload(this.getId(), actualDamage, this.damageEngine$wasCrit, this.damageEngine$attackerId);
            
            // Send to tracking players
            for (ServerPlayerEntity player : PlayerLookup.tracking(this)) {
                PacketByteBuf buf = PacketByteBufs.create();
                payload.encode(buf);
                ServerPlayNetworking.send(player, DamagePayload.ID, buf);
            }
            
            // Send to self if player
            if ((Object)this instanceof ServerPlayerEntity selfPlayer) {
                PacketByteBuf buf = PacketByteBufs.create();
                payload.encode(buf);
                ServerPlayNetworking.send(selfPlayer, DamagePayload.ID, buf);
            }
        }
    }
}
