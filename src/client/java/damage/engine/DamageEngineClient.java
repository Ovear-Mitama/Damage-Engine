package damage.engine;

import damage.engine.hud.DamageSessionManager;
import damage.engine.hud.DamageIndicator;
import damage.engine.hud.RatingManager;
import damage.engine.network.DamagePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
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

        // 进入服务器/世界时重置服务端 mod 检测状态；单机世界由 ClientTickMixin 直接标记
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            serverHasMod = false;
            serverModChecked = false;
            joinCheckTicks = 40;
        });

        // 标准 Fabric API 接收 S2C 伤害包
        ClientPlayNetworking.registerGlobalReceiver(DamagePayload.TYPE, (payload, context) -> {
            if (!serverHasMod) {
                serverHasMod = true;
                serverModChecked = true;
            }

            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> {
                if (mc.level == null || mc.player == null) return;
                
                DamageEngineConfig config = DamageEngineConfig.getInstance();
                
                if (config.debugShowDamageInfo) {
                    DamagePayload dp = payload;
                    if (dp.debugInfo() != null && !dp.debugInfo().isEmpty()) {
                        mc.player.sendSystemMessage(
                            Component.literal("[DE Debug] ").withColor(0xB3EDC4)
                                .append(Component.literal(dp.debugInfo()).withColor(0xFFFFFF))
                        );
                    }
                    mc.player.sendSystemMessage(
                        Component.literal("[DE Debug] ").withColor(0xB3EDC4)
                            .append(Component.literal("伤害: " + String.format("%.1f", dp.amount()) 
                            + (dp.isCrit() ? " 暴击" : "") + " | 实体: " + dp.entityId()
                            + " | 投射物: " + (dp.isProjectile() ? "是" : "否")).withColor(0xFFFFFF))
                    );
                }

                DamagePayload dp = payload;
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

                    // 分项设置:玩家使用玩家显示距离,非玩家实体按实体规则(注册名/All);
                    // 感知过滤开启时,只有"看不见且听不见"的受害生物才隐藏跳字
                    net.minecraft.world.entity.Entity victim = dp.entityId() > 0 && mc.level != null
                        ? mc.level.getEntity(dp.entityId()) : null;
                    if (damage.engine.client.GlobalDamageFilter.shouldShow(config, mc, victim, dp.posX(), dp.posY(), dp.posZ())) {
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
                
                // 准星命中的实体交给会话判断(末影龙需从部件映射到本体),命中时强制切换信息面板目标
                DamageSessionManager.getInstance().addDamage(dp.amount(), dp.isCrit(), dp.entityId(),
                    mc.hitResult instanceof EntityHitResult ehr ? ehr.getEntity() : null);
                
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
                        mc.player.sendOverlayMessage(
                            Component.literal("[DE Debug] ").withColor(0xB3EDC4)
                                .append(Component.literal("当前评分: " + rm.getGrade() + " | 分数: " + String.format("%.1f", rm.getScore())).withColor(0xFFFFFF))
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
