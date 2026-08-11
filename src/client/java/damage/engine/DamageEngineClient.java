package damage.engine;

import damage.engine.hud.DamageSessionManager;
import damage.engine.hud.DamageIndicator;
import damage.engine.hud.RatingManager;
import damage.engine.network.DamagePayload;
import damage.engine.network.NetworkRegistry;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DamageEngineClient implements ClientModInitializer {
    public static KeyMapping configKeyBinding;
    public static KeyMapping toggleHudKeyBinding;
    public static KeyMapping clearDamageKeyBinding;
    
    public static volatile boolean serverHasMod = false;
    public static volatile boolean serverModChecked = false;
    public static int joinCheckTicks = -1;
    
    public static final Logger LOGGER = LoggerFactory.getLogger("damage-engine");

    @Override
    public void onInitializeClient() {
        configKeyBinding = ClientKeybindings.configKeyBinding;
        toggleHudKeyBinding = ClientKeybindings.toggleHudKeyBinding;
        clearDamageKeyBinding = ClientKeybindings.clearDamageKeyBinding;
        KeyMapping.resetMapping();

        // 用 NetworkRegistry 注册 S2C 处理器（不走 Fabric API 的 registerGlobalReceiver）
        NetworkRegistry.registerS2C(DamagePayload.TYPE, DamagePayload.STREAM_CODEC, (payload, ctx) -> {
            if (!serverHasMod) {
                serverHasMod = true;
                serverModChecked = true;
            }

            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> {
                if (mc.level == null || mc.player == null) return;
                
                DamageEngineConfig config = DamageEngineConfig.getInstance();
                
                if (config.debugShowDamageInfo) {
                    DamagePayload dp = (DamagePayload) payload;
                    if (dp.debugInfo() != null && !dp.debugInfo().isEmpty()) {
                        mc.player.displayClientMessage(
                            Component.literal("[DE Debug] ").withColor(0xB3EDC4)
                                .append(Component.literal(dp.debugInfo()).withColor(0xFFFFFF)),
                            false
                        );
                    }
                    mc.player.displayClientMessage(
                        Component.literal("[DE Debug] ").withColor(0xB3EDC4)
                            .append(Component.literal("伤害: " + String.format("%.1f", dp.amount()) 
                            + (dp.isCrit() ? " 暴击" : "") + " | 实体: " + dp.entityId()
                            + " | 投射物: " + (dp.isProjectile() ? "是" : "否")).withColor(0xFFFFFF)),
                        false
                    );
                }

                DamagePayload dp = (DamagePayload) payload;
                boolean isSelfDamage = dp.attackerId() == mc.player.getId();

                // Handle global damage indicators (damage caused by others)
                if (!isSelfDamage && config.showGlobalDamageIndicator) {
                    // Skip if attacker is invisible
                    if (dp.attackerId() > 0 && mc.level != null) {
                        net.minecraft.world.entity.Entity attackerEntity = mc.level.getEntity(dp.attackerId());
                        if (attackerEntity instanceof net.minecraft.world.entity.LivingEntity le && le.isInvisible()) {
                            return;
                        }
                    }

                    // Calculate distance for culling
                    double dx = dp.posX() - mc.player.getX();
                    double dy = dp.posY() - mc.player.getY();
                    double dz = dp.posZ() - mc.player.getZ();
                    double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

                    if (config.globalIndicatorMaxDistance <= 0 || distance <= config.globalIndicatorMaxDistance) {
                        if (config.showDamageIndicator && dp.amount() > 0) {
                            Vec3 pos = blendIndicatorPos(dp.posX(), dp.posY(), dp.posZ(), dp.entityId());
                            DamageIndicator.addIndicator(pos.x, pos.y, pos.z,
                                dp.amount(), dp.isCrit(), false);
                        }
                        if (config.showKillIndicator && dp.killed()) {
                            Vec3 pos = blendIndicatorPos(dp.posX(), dp.posY(), dp.posZ(), dp.entityId());
                            DamageIndicator.addIndicator(pos.x, pos.y, pos.z,
                                dp.amount(), false, true);
                        }
                    }
                }

                // Only process self damage for session tracking
                if (!isSelfDamage) {
                    if (config.recordOtherPlayers) {
                        DamageSessionManager.getInstance().addOtherPlayerDamage(dp.amount(), dp.isCrit(), dp.attackerId());
                    }
                    return;
                }
                
                boolean preferSwitchTarget = false;
                try {
                    if (mc.hitResult instanceof EntityHitResult ehr) {
                        preferSwitchTarget = ehr.getEntity() != null && ehr.getEntity().getId() == dp.entityId();
                    }
                } catch (Exception ignored) {}
                
                DamageSessionManager.getInstance().addDamage(dp.amount(), dp.isCrit(), dp.entityId(), preferSwitchTarget);
                
                Vec3 pos = blendIndicatorPos(dp.posX(), dp.posY(), dp.posZ(), dp.entityId());
                double maxDist = config.globalIndicatorMaxDistance;
                if (maxDist <= 0 || pos.distanceToSqr(mc.player.position()) <= maxDist * maxDist) {
                    if (config.showDamageIndicator && dp.amount() > 0) {
                        DamageIndicator.addIndicator(pos.x, pos.y, pos.z,
                            dp.amount(), dp.isCrit(), false);
                    }
                    if (config.showKillIndicator && dp.killed()) {
                        DamageIndicator.addIndicator(pos.x, pos.y, pos.z,
                            dp.amount(), false, true);
                    }
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
        });
    }
    
    public static Vec3 blendIndicatorPos(double baseX, double baseY, double baseZ, int entityId) {
        return damage.engine.client.IndicatorPos.blend(baseX, baseY, baseZ, entityId);
    }
}
