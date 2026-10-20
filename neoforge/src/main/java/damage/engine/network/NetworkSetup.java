package damage.engine.network;

import damage.engine.DamageEngine;
import damage.engine.DamageEngineClient;
import damage.engine.DamageEngineConfig;
import damage.engine.hud.indicator.DamageIndicator;
import damage.engine.hud.session.DamageSessionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(value = Dist.CLIENT, modid = DamageEngine.MOD_ID)
public class NetworkSetup {

    @SubscribeEvent
    static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");

        // S2C: Damage payload - client receives damage data from server
        registrar.optional().playToClient(
            DamagePayload.TYPE,
            DamagePayload.STREAM_CODEC,
            (payload, ctx) -> {
                // Set flag immediately (on network thread) to block client-side
                // health tracker before the next entity tick, preventing double counting.
                if (!DamageEngineClient.serverHasMod) {
                    DamageEngineClient.serverHasMod = true;
                    DamageEngineClient.serverModChecked = true;
                }

                Minecraft mc = Minecraft.getInstance();
                mc.execute(() -> {
                    if (mc.level == null || mc.player == null) return;

                    DamageEngineConfig config = DamageEngineConfig.getInstance();

                    if (config.debugShowDamageInfo) {
                        if (payload.debugInfo() != null && !payload.debugInfo().isEmpty()) {
                            mc.player.sendSystemMessage(
                                Component.literal("[DE Debug] ").withColor(0xB3EDC4)
                                    .append(Component.literal(payload.debugInfo()).withColor(0xFFFFFF))
                            );
                        }
                        mc.player.sendSystemMessage(
                            Component.literal("[DE Debug] ").withColor(0xB3EDC4)
                                .append(Component.literal("伤害: " + String.format("%.1f", payload.amount())
                                + (payload.isCrit() ? " 暴击" : "") + " | 实体: " + payload.entityId()
                                + " | 投射物: " + (payload.isProjectile() ? "是" : "否")).withColor(0xFFFFFF))
                        );
                    }

                    boolean isSelfDamage = payload.attackerId() == mc.player.getId();

                    // Handle global damage indicators (damage caused by others)
                    if (!isSelfDamage && config.showGlobalDamageIndicator) {
                        // Skip if attacker is invisible
                        if (payload.attackerId() > 0 && mc.level != null) {
                            net.minecraft.world.entity.Entity attackerEntity = mc.level.getEntity(payload.attackerId());
                            if (attackerEntity instanceof net.minecraft.world.entity.LivingEntity le && le.isInvisible()) {
                                return;
                            }
                        }

                        // 分项设置:玩家使用玩家显示距离,非玩家实体按实体规则(注册名/All)
                        net.minecraft.world.entity.Entity victim = payload.entityId() > 0 && mc.level != null
                            ? mc.level.getEntity(payload.entityId()) : null;
                        Float maxDist = config.resolveGlobalIndicatorDistance(victim);
                        if (maxDist == null) {
                            return; // 该受害实体被屏蔽或未配置显示
                        }

                        // Calculate distance for culling
                        double dx = payload.posX() - mc.player.getX();
                        double dy = payload.posY() - mc.player.getY();
                        double dz = payload.posZ() - mc.player.getZ();
                        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

                        if (maxDist <= 0 || distance <= maxDist) {
                            if (config.showDamageIndicator && payload.amount() > 0) {
                                Vec3 pos = DamageEngineClient.blendIndicatorPos(payload.posX(), payload.posY(), payload.posZ(), payload.entityId());
                                DamageIndicator.addIndicator(victim, pos.x, pos.y, pos.z,
                                    payload.amount(), payload.isCrit(), false);
                            }
                            if (config.showKillIndicator && payload.killed()) {
                                Vec3 pos = DamageEngineClient.blendIndicatorPos(payload.posX(), payload.posY(), payload.posZ(), payload.entityId());
                                DamageIndicator.addIndicator(victim, pos.x, pos.y, pos.z,
                                    payload.amount(), false, true);
                            }
                        }
                    }

                    // Only process self damage for session tracking
                    if (!isSelfDamage) {
                        if (config.recordOtherPlayers) {
                            DamageSessionManager.getInstance().addOtherPlayerDamage(payload.amount(), payload.isCrit(), payload.attackerId());
                        }
                        return;
                    }

                    // 准星命中的实体交给会话判断(末影龙需从部件映射到本体),命中时强制切换信息面板目标
                    net.minecraft.world.entity.Entity crosshair = mc.hitResult instanceof EntityHitResult ehr
                        ? ehr.getEntity() : null;
                    boolean preferSwitchTarget = DamageSessionManager.isSameVictim(crosshair, payload.entityId());
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

                    DamageSessionManager.getInstance().addDamage(payload.amount(), payload.isCrit(),
                        payload.entityId(), preferSwitchTarget, payload.killed(), distance, unseen);

                    Vec3 pos = DamageEngineClient.blendIndicatorPos(payload.posX(), payload.posY(), payload.posZ(), payload.entityId());
                    double maxDist = config.globalIndicatorMaxDistance;
                    if (maxDist <= 0 || pos.distanceToSqr(mc.player.position()) <= maxDist * maxDist) {
                        if (config.showDamageIndicator && payload.amount() > 0) {
                            DamageIndicator.addIndicator(selfVictim, pos.x, pos.y, pos.z,
                                payload.amount(), payload.isCrit(), false);
                        }
                        if (config.showKillIndicator && payload.killed()) {
                            DamageIndicator.addIndicator(selfVictim, pos.x, pos.y, pos.z,
                                payload.amount(), false, true);
                        }
                    }
                });
            }
        );

        // C2S: Handshake payload - client pings server to check mod presence
        registrar.optional().playToServer(
            HandshakePayload.TYPE,
            HandshakePayload.STREAM_CODEC,
            (payload, ctx) -> {
                // Handler的存在本身表示服务端安装了本mod
            }
        );
    }
}
