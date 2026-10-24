package damage.engine;

import damage.engine.network.DamageMessage;
import damage.engine.network.NetworkHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** 服务端事件：把玩家造成的每次伤害发给对应客户端。 */
@Mod.EventBusSubscriber(modid = DamageEngine.MOD_ID)
public final class ServerEvents {
    private ServerEvents() {
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        Entity source = event.getSource().getTrueSource();
        if (!(source instanceof EntityPlayerMP)) return;

        EntityPlayerMP player = (EntityPlayerMP) source;
        EntityLivingBase victim = event.getEntityLiving();
        if (victim == player) return;

        float amount = event.getAmount();
        boolean killed = victim.getHealth() - amount <= 0.0f;
        float distance = (float) Math.sqrt(player.getDistanceSq(victim));
        boolean unseen = killed && !player.canEntityBeSeen(victim);
        boolean crit = isCrit(player);

        NetworkHandler.CHANNEL.sendTo(
            new DamageMessage(victim.getEntityId(), amount, crit, killed, distance, unseen,
                victim.posX, victim.posY + victim.height * 0.7, victim.posZ), player);
    }

    /** 与 1.12.2 原版暴击判定一致（近似：由服务端玩家状态推导）。 */
    private static boolean isCrit(EntityPlayerMP player) {
        return player.fallDistance > 0.0F
            && !player.onGround
            && !player.isOnLadder()
            && !player.isInWater()
            && !player.isPotionActive(MobEffects.BLINDNESS)
            && !player.isRiding()
            && !player.isSprinting();
    }
}
