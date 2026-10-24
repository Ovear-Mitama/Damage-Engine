package damage.engine.hud.rating;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;

/** 计算击杀加成需要的距离与“目标是否面向玩家”。 */
public final class CombatContext {
    private CombatContext() {}

    /** 玩家与目标的距离（米）。 */
    public static float distance(EntityPlayer player, Entity target) {
        if (player == null || target == null) return 0f;
        return (float) Math.sqrt(player.getDistanceSq(target));
    }

    /**
     * 玩家是否看不到目标（视线被方块遮挡）。
     * <p>用于“不见其人”：只有玩家看不到目标时击杀才加分；玩家能直接看到目标时不加分。</p>
     */
    public static boolean isHiddenFromPlayer(Entity target, EntityPlayer player) {
        if (target == null || player == null) return false;
        return !player.canEntityBeSeen(target);
    }
}
