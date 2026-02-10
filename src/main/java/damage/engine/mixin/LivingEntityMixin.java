package damage.engine.mixin;

import damage.engine.network.DamagePayload;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Ownable;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.player.PlayerEntity;
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
        Entity attacker = source.getAttacker();
        
        // 1. 直接攻击者
        if (attacker == null) {
            // 2. 间接来源 (如 TNT, 投掷物, 水晶)
            Entity directSource = source.getSource();
            if (directSource instanceof Ownable ownable) {
                attacker = ownable.getOwner();
            } else if (directSource instanceof net.minecraft.entity.TntEntity tnt) {
                attacker = tnt.getOwner();
            } else if (directSource instanceof net.minecraft.entity.decoration.EndCrystalEntity crystal) {

                try {
                    java.lang.reflect.Method m = crystal.getClass().getMethod("damageEngine$getAttacker");
                    attacker = (Entity) m.invoke(crystal);
                } catch (Exception e) {
                }
            }
        }
        
        // 3. 火焰伤害回溯
        if (attacker == null && (source.isOf(DamageTypes.ON_FIRE) || source.isOf(DamageTypes.IN_FIRE))) {
            attacker = ((LivingEntity)(Object)this).getAttacker();
        }

        if (attacker != null) {
            this.damageEngine$attackerId = attacker.getId();
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
        
        // 调试日志
        String debugInfo = "";
        // 移除非玩家实体的调试日志输出，避免刷屏
        
        if (damage.engine.DamageEngineConfig.getInstance().debugMode) {
            debugInfo = String.format("%s造成伤害%.1f，对象：%s", source.getName(), actualDamage, self.getName().getString());
            LOGGER.info(debugInfo);
        }
        

        if (actualDamage > 0) {
            DamagePayload payload = new DamagePayload(this.getId(), actualDamage, this.damageEngine$wasCrit, this.damageEngine$attackerId, debugInfo);
            
            // 发送给追踪的玩家
            for (ServerPlayerEntity player : PlayerLookup.tracking(this)) {
                ServerPlayNetworking.send(player, payload);
            }
            
            // 如果是玩家则发送给自己
            if ((Object)this instanceof ServerPlayerEntity selfPlayer) {
                ServerPlayNetworking.send(selfPlayer, payload);
            }
        }
    }
}
