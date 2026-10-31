package damage.engine.network;

import damage.engine.DamageEngineClient;
import damage.engine.DamageEngineConfig;
import damage.engine.hud.indicator.DamageIndicator;
import damage.engine.hud.session.DamageSessionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 客户端专用数据包处理。
 * <p>
 * 该类只在客户端被 {@link DamageEngineClient} 注入到 {@link NetworkHandler} 的回调里，
 * 服务端不会引用它，因此这里可以安全地使用 {@link Minecraft} 等客户端类。
 */
public final class ClientPacketHandler {
    private ClientPacketHandler() {}

    /** 发送探测服务端是否安装本模组的握手包。 */
    public static void sendHandshakeToServer() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            NetworkHandler.sendHandshake();
        }
    }

    /** 处理 S2C 伤害包，更新跳字、会话与客户端降级状态。 */
    public static void handleDamage(DamagePayload payload) {
        if (!DamageEngineClient.serverHasMod) {
            DamageEngineClient.serverHasMod = true;
            DamageEngineClient.serverModChecked = true;
            // 服务端确实装了本模组：撤掉可能已显示的降级警告
            damage.engine.client.ClientDowngradeOverlay.reset();
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        DamageEngineConfig config = DamageEngineConfig.getInstance();

        if (config.debugShowDamageInfo) {
            if (payload.debugInfo() != null && !payload.debugInfo().isEmpty()) {
                mc.player.displayClientMessage(
                    Component.literal("[DE Debug] ").withStyle(style -> style.withColor(TextColor.fromRgb(0xB3EDC4)))
                        .append(Component.literal(payload.debugInfo()).withStyle(style -> style.withColor(TextColor.fromRgb(0xFFFFFF)))),
                    false
                );
            }
            mc.player.displayClientMessage(
                Component.literal("[DE Debug] ").withStyle(style -> style.withColor(TextColor.fromRgb(0xB3EDC4)))
                    .append(Component.literal("Dmg: " + String.format("%.1f", payload.amount())
                    + (payload.isCrit() ? " Crit" : "") + " | Entity: " + payload.entityId()
                    + " | Projectile: " + (payload.isProjectile() ? "Yes" : "No")).withStyle(style -> style.withColor(TextColor.fromRgb(0xFFFFFF)))),
                false
            );
        }

        boolean isSelfDamage = payload.attackerId() == mc.player.getId();

        if (!isSelfDamage && config.showGlobalDamageIndicator) {
            // Damage floats on other entities (victim isn't me) follow the "record other players" toggle.
            // Only filter when an attacker was actually resolved (attackerId > 0): unresolved
            // attackers (e.g. TACZ bullets whose owner can't be resolved) may still be our own damage.
            if (payload.attackerId() > 0 && payload.entityId() != mc.player.getId() && !config.recordOtherPlayers) {
                return;
            }
            if (payload.attackerId() > 0 && mc.level != null) {
                net.minecraft.world.entity.Entity attackerEntity = mc.level.getEntity(payload.attackerId());
                if (attackerEntity instanceof net.minecraft.world.entity.LivingEntity le && le.isInvisible()) {
                    return;
                }
            }

            // 分项设置:玩家使用玩家显示距离,非玩家实体按实体规则(注册名/All);
            // 智能隐藏开启时,由视觉(看不见则隐藏)决定
            net.minecraft.world.entity.Entity victim = payload.entityId() > 0 && mc.level != null
                ? mc.level.getEntity(payload.entityId()) : null;
            if (damage.engine.client.GlobalDamageFilter.shouldShow(config, mc, victim, payload.posX(), payload.posY(), payload.posZ())) {
                if (config.showDamageIndicator && payload.amount() > 0) {
                    Vec3 pos = DamageEngineClient.blendIndicatorPos(payload.posX(), payload.posY(), payload.posZ(), payload.entityId());
                    DamageIndicator.addIndicator(victim, pos.x, pos.y, pos.z, payload.amount(), payload.isCrit(), false);
                }
                if (config.showKillIndicator && payload.killed()) {
                    Vec3 pos = DamageEngineClient.blendIndicatorPos(payload.posX(), payload.posY(), payload.posZ(), payload.entityId());
                    DamageIndicator.addIndicator(victim, pos.x, pos.y, pos.z, payload.amount(), false, true);
                }
            }
        }

        if (!isSelfDamage) {
            if (config.recordOtherPlayers) {
                DamageSessionManager.getInstance().addOtherPlayerDamage(payload.amount(), payload.isCrit(), payload.attackerId());
            }
            return;
        }

        boolean preferSwitchTarget = false;
        try {
            if (mc.hitResult instanceof EntityHitResult ehr) {
                preferSwitchTarget = ehr.getEntity() != null && ehr.getEntity().getId() == payload.entityId();
            }
        } catch (Exception ignored) {}
        // 远程命中通常超出准心拾取距离，准心命中的不是受害实体；
        // 这类伤害直接切换信息面板目标，否则第二箭会一直停留在第一个目标上。
        if (payload.isProjectile()) {
            preferSwitchTarget = true;
        }

        net.minecraft.world.entity.Entity selfVictim = payload.entityId() > 0 && mc.level != null
            ? mc.level.getEntity(payload.entityId()) : null;
        float distance = (selfVictim != null && mc.player != null)
            ? (float) Math.sqrt(mc.player.distanceToSqr(selfVictim)) : 0f;
        boolean unseen = payload.killed() && selfVictim != null && mc.player != null
            && damage.engine.hud.rating.CombatContext.isHiddenFromPlayer(selfVictim, mc.player);

        DamageSessionManager.getInstance().addDamage(payload.amount(), payload.isCrit(), payload.entityId(),
            preferSwitchTarget, payload.killed(), distance, unseen);

        Vec3 pos = DamageEngineClient.blendIndicatorPos(payload.posX(), payload.posY(), payload.posZ(), payload.entityId());
        double maxDist = config.globalIndicatorMaxDistance;
        if (maxDist <= 0 || pos.distanceToSqr(mc.player.position()) <= maxDist * maxDist) {
            if (config.showDamageIndicator && payload.amount() > 0) {
                DamageIndicator.addIndicator(selfVictim, pos.x, pos.y, pos.z, payload.amount(), payload.isCrit(), false);
            }
            if (config.showKillIndicator && payload.killed()) {
                DamageIndicator.addIndicator(selfVictim, pos.x, pos.y, pos.z, payload.amount(), false, true);
            }
        }
    }
}
