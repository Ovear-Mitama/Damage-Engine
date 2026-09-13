package damage.engine.client;

import damage.engine.DamageEngineConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * 全局伤害跳字 - 感知过滤判定。
 * <p>
 * 规则(与 {@link DamageEngineConfig#globalIndicatorSmartHide} 配套):
 * 只有"看不见"且"听不见"时才屏蔽该次伤害跳字 —— 两个条件同时满足才隐藏。
 * <ul>
 *   <li>视觉:玩家与受害生物之间无遮挡(被方块遮挡或隐形则视为看不见)</li>
 *   <li>听觉:{@link #HEARING_RANGE} 格以内视为能听见</li>
 * </ul>
 * 未开启感知过滤时,保持原有纯距离上限判定。
 */
public final class GlobalDamageFilter {
    /** 听觉范围:该距离以内视为能听见,直接显示跳字。 */
    private static final double HEARING_RANGE = 16.0;

    private GlobalDamageFilter() {
    }

    /**
     * 判断受害生物对应的全局伤害跳字是否应该显示。
     *
     * @param posX/posY/posZ 伤害发生位置(用于距离计算与跳字锚点)
     */
    public static boolean shouldShow(DamageEngineConfig config, Minecraft client, Entity victim,
                                     double posX, double posY, double posZ) {
        if (client.player == null) return false;

        // 末影龙的碰撞箱是部件实体,统一映射到本体后再做规则匹配与视线判定
        victim = resolveVictim(victim);

        // 1. 实体规则(显示/屏蔽 + 玩家/非玩家距离):返回 null 表示该受害实体被屏蔽或未配置显示
        Float maxDist = config.resolveGlobalIndicatorDistance(victim);
        if (maxDist == null) return false;

        double dx = posX - client.player.getX();
        double dy = posY - client.player.getY();
        double dz = posZ - client.player.getZ();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

        // 2. 感知过滤:看不见 且 听不见(超出听觉范围)才隐藏;只要看得见或听得见就显示
        if (config.globalIndicatorSmartHide) {
            return canSee(client, victim) || distance <= HEARING_RANGE;
        }

        // 3. 普通模式:纯距离上限(0 = 无限制)
        return maxDist <= 0 || distance <= maxDist;
    }

    private static boolean canSee(Minecraft client, Entity victim) {
        if (victim == null || client.player == null) return false;
        if (victim.isInvisible()) return false;
        return victim instanceof LivingEntity living && living.hasLineOfSight(client.player);
    }

    /** 末影龙的碰撞箱由 {@code EnderDragonPart} 部件组成,部件不是 LivingEntity,需映射回本体。 */
    private static Entity resolveVictim(Entity victim) {
        if (victim instanceof net.minecraft.world.entity.boss.EnderDragonPart part
            && part.parentMob != null) {
            return part.parentMob;
        }
        return victim;
    }
}
