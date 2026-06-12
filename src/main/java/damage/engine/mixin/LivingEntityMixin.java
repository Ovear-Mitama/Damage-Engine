package damage.engine.mixin;

import damage.engine.network.DamagePayload;
import damage.engine.util.AttackerAccessor;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.Mth;
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
    @Unique
    private double damageEngine$sourceX;
    @Unique
    private double damageEngine$sourceY;
    @Unique
    private double damageEngine$sourceZ;
    @Unique
    private boolean damageEngine$isProjectile;

    public LivingEntityMixin(net.minecraft.world.entity.EntityType<?> type, net.minecraft.world.level.Level world) {
        super(type, world);
    }

    @Inject(method = "actuallyHurt", at = @At("HEAD"))
    private void onApplyDamageHead(ServerLevel level, DamageSource source, float amount, CallbackInfo ci) {
        if (this.level().isClientSide()) return;
        if ((Object)this instanceof Player) return;
        
        LivingEntity self = (LivingEntity) (Object) this;
        this.damageEngine$previousHealth = self.getHealth();
        this.damageEngine$previousAbsorption = self.getAbsorptionAmount();
        
        this.damageEngine$wasCrit = false;
        if (source.getEntity() instanceof Player player) {
             if (player.fallDistance > 0.0F && !player.onGround() && !player.onClimbable() && !player.isInWater()) {
                 this.damageEngine$wasCrit = true;
             }
        }
        
        this.damageEngine$attackerId = -1;
        this.damageEngine$isProjectile = false;
        Entity attacker = source.getEntity();
        Entity directSource = source.getDirectEntity();
        
        if (attacker == null) {
            if (directSource instanceof OwnableEntity ownable) {
                attacker = ownable.getOwner();
            } else if (directSource instanceof net.minecraft.world.entity.boss.enderdragon.EndCrystal crystal) {
                if (crystal instanceof AttackerAccessor accessor) {
                    attacker = accessor.damageEngine$getAttacker();
                }
            }
        }
        
        if (attacker == null && (source.is(DamageTypes.ON_FIRE) || source.is(DamageTypes.IN_FIRE))) {
            attacker = ((LivingEntity)(Object)this).getLastAttacker();
        }

        if (attacker != null) {
            this.damageEngine$attackerId = attacker.getId();
        }
        
        // Capture hit position: projectile position for projectile attacks, closest point on bounding box to attacker for melee
        if (directSource instanceof net.minecraft.world.entity.projectile.Projectile) {
            this.damageEngine$isProjectile = true;
            this.damageEngine$sourceX = directSource.getX();
            this.damageEngine$sourceY = directSource.getY();
            this.damageEngine$sourceZ = directSource.getZ();
        } else if (attacker != null) {
            // Use closest point on entity bounding box to attacker for accurate hit position
            AABB box = self.getBoundingBox();
            this.damageEngine$sourceX = Mth.clamp(attacker.getX(), box.minX, box.maxX);
            this.damageEngine$sourceY = Mth.clamp(attacker.getEyeY(), box.minY, box.maxY);
            this.damageEngine$sourceZ = Mth.clamp(attacker.getZ(), box.minZ, box.maxZ);
        } else {
            this.damageEngine$sourceX = self.getX();
            this.damageEngine$sourceY = self.getY() + self.getEyeHeight() * 0.6;
            this.damageEngine$sourceZ = self.getZ();
        }
    }

    @Inject(method = "actuallyHurt", at = @At("RETURN"))
    private void onApplyDamageReturn(ServerLevel level, DamageSource source, float amount, CallbackInfo ci) {
        if (this.level().isClientSide()) return;
        if ((Object)this instanceof Player) return;

        LivingEntity self = (LivingEntity) (Object) this;
        float currentHealth = self.getHealth();
        float currentAbsorption = self.getAbsorptionAmount();

        float healthLost = this.damageEngine$previousHealth - currentHealth;
        float absorptionLost = this.damageEngine$previousAbsorption - currentAbsorption;
        float actualDamage = healthLost + absorptionLost;
        
        String debugInfo = "";
        
        if (damage.engine.DamageEngineConfig.getInstance().debugMode) {
            String attackerName = source.getMsgId();
            if (this.damageEngine$attackerId != 0 && self.level() instanceof ServerLevel serverWorld) {
                Entity attacker = serverWorld.getEntity(this.damageEngine$attackerId);
                if (attacker != null) {
                    attackerName = attacker.getName().getString();
                }
            }
            debugInfo = String.format("%s 造成伤害%.1f 伤害对象 %s", attackerName, actualDamage, self.getName().getString());
            LOGGER.info(debugInfo);
        }
        

        boolean killed = !self.isAlive() || self.getHealth() <= 0f;
        
        if (actualDamage > 0 || killed) {
            DamagePayload payload = new DamagePayload(this.getId(), actualDamage, this.damageEngine$wasCrit, this.damageEngine$attackerId, debugInfo,
                this.damageEngine$sourceX, this.damageEngine$sourceY, this.damageEngine$sourceZ, this.damageEngine$isProjectile, killed);
            
            for (ServerPlayer player : PlayerLookup.tracking(this)) {
                ServerPlayNetworking.send(player, payload);
            }
            
            if ((Object)this instanceof ServerPlayer selfPlayer) {
                ServerPlayNetworking.send(selfPlayer, payload);
            }
        }
    }
}
