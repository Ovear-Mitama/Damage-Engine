package damage.engine.network;

import damage.engine.DamageEngine;
import damage.engine.DamageEngineClient;
import damage.engine.DamageEngineConfig;
import damage.engine.hud.DamageIndicator;
import damage.engine.hud.DamageSessionManager;
import damage.engine.hud.RatingManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(value = Dist.CLIENT, modid = DamageEngine.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
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
                            mc.player.displayClientMessage(
                                Component.literal("[DE Debug] ").withColor(0xB3EDC4)
                                    .append(Component.literal(payload.debugInfo()).withColor(0xFFFFFF)),
                                false
                            );
                        }
                        mc.player.displayClientMessage(
                            Component.literal("[DE Debug] ").withColor(0xB3EDC4)
                                .append(Component.literal("伤害: " + String.format("%.1f", payload.amount())
                                + (payload.isCrit() ? " 暴击" : "") + " | 实体: " + payload.entityId()
                                + " | 投射物: " + (payload.isProjectile() ? "是" : "否")).withColor(0xFFFFFF)),
                            false
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

                        // Calculate distance for culling
                        double dx = payload.posX() - mc.player.getX();
                        double dy = payload.posY() - mc.player.getY();
                        double dz = payload.posZ() - mc.player.getZ();
                        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

                        if (distance <= config.globalIndicatorMaxDistance) {
                            if (config.showDamageIndicator && payload.amount() > 0 && !payload.killed()) {
                                Vec3 pos = payload.isProjectile()
                                    ? new Vec3(payload.posX(), payload.posY(), payload.posZ())
                                    : DamageEngineClient.blendIndicatorPos(payload.posX(), payload.posY(), payload.posZ(), payload.entityId());
                                DamageIndicator.addIndicator(pos.x, pos.y, pos.z,
                                    payload.amount(), payload.isCrit(), false);
                            }
                            if (config.showKillIndicator && payload.killed()) {
                                Vec3 pos = payload.isProjectile()
                                    ? new Vec3(payload.posX(), payload.posY(), payload.posZ())
                                    : DamageEngineClient.blendIndicatorPos(payload.posX(), payload.posY(), payload.posZ(), payload.entityId());
                                DamageIndicator.addIndicator(pos.x, pos.y, pos.z,
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

                    boolean preferSwitchTarget = false;
                    try {
                        if (mc.hitResult instanceof EntityHitResult ehr) {
                            preferSwitchTarget = ehr.getEntity() != null && ehr.getEntity().getId() == payload.entityId();
                        }
                    } catch (Exception ignored) {}

                    DamageSessionManager.getInstance().addDamage(payload.amount(), payload.isCrit(), payload.entityId(), preferSwitchTarget);

                    if (config.showDamageIndicator && payload.amount() > 0 && !payload.killed()) {
                        Vec3 pos = payload.isProjectile()
                            ? new Vec3(payload.posX(), payload.posY(), payload.posZ())
                            : DamageEngineClient.blendIndicatorPos(payload.posX(), payload.posY(), payload.posZ(), payload.entityId());
                        DamageIndicator.addIndicator(pos.x, pos.y, pos.z,
                            payload.amount(), payload.isCrit(), false);
                    }
                    if (config.showKillIndicator && payload.killed()) {
                        Vec3 pos = payload.isProjectile()
                            ? new Vec3(payload.posX(), payload.posY(), payload.posZ())
                            : DamageEngineClient.blendIndicatorPos(payload.posX(), payload.posY(), payload.posZ(), payload.entityId());
                        DamageIndicator.addIndicator(pos.x, pos.y, pos.z,
                            payload.amount(), false, true);
                    }

                    if (config.debugShowRating && mc.player != null) {
                        RatingManager rm = RatingManager.getInstance();
                        if (rm.isVisible()) {
                            mc.player.displayClientMessage(
                                Component.literal("[DE Debug] ").withColor(0xB3EDC4)
                                    .append(Component.literal("当前评分: " + rm.getGrade() + " | 分数: " + String.format("%.1f", rm.getScore())).withColor(0xFFFFFF)),
                                true
                            );
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
