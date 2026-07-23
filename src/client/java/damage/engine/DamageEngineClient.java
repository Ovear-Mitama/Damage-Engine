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
    
    /** Whether the server has Damage Engine installed. Only valid after serverModChecked is true. */
    public static volatile boolean serverHasMod = false;
    /** Whether we have completed the server mod presence check. */
    public static volatile boolean serverModChecked = false;
    /** Tick counter for delayed server mod check. */
    public static int joinCheckTicks = -1;
    
    public static final Logger LOGGER = LoggerFactory.getLogger("damage-engine");

    @Override
    public void onInitializeClient() {
        // Sync keybinding references from ClientKeybindings (created by KeyBindingMixin)
        configKeyBinding = ClientKeybindings.configKeyBinding;
        toggleHudKeyBinding = ClientKeybindings.toggleHudKeyBinding;
        clearDamageKeyBinding = ClientKeybindings.clearDamageKeyBinding;
        KeyMapping.resetMapping();

        // Register S2C payload handler (receiver for damage data from server)
        NetworkRegistry.registerS2C(DamagePayload.TYPE, DamagePayload.STREAM_CODEC, (payload, ctx) -> {
            // Set flag immediately to block client-side health tracker
            // before the next entity tick, preventing double counting.
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
                if (dp.attackerId() != mc.player.getId()) {
                    return;
                }
                
                boolean preferSwitchTarget = false;
                try {
                    if (mc.hitResult instanceof EntityHitResult ehr) {
                        preferSwitchTarget = ehr.getEntity() != null && ehr.getEntity().getId() == dp.entityId();
                    }
                } catch (Exception ignored) {}
                
                DamageSessionManager.getInstance().addDamage(dp.amount(), dp.isCrit(), dp.entityId(), preferSwitchTarget);
                
                if (config.showDamageIndicator && dp.amount() > 0) {
                    Vec3 pos = dp.isProjectile() 
                        ? new Vec3(dp.posX(), dp.posY(), dp.posZ())
                        : blendIndicatorPos(dp.posX(), dp.posY(), dp.posZ(), dp.entityId());
                    DamageIndicator.addIndicator(pos.x, pos.y, pos.z,
                        dp.amount(), dp.isCrit(), false);
                }
                if (config.showKillIndicator && dp.killed()) {
                    Vec3 pos = dp.isProjectile() 
                        ? new Vec3(dp.posX(), dp.posY(), dp.posZ())
                        : blendIndicatorPos(dp.posX(), dp.posY(), dp.posZ(), dp.entityId());
                    DamageIndicator.addIndicator(pos.x, pos.y, pos.z,
                        dp.amount(), false, true);
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
        Minecraft client = Minecraft.getInstance();
        if (client.hitResult instanceof EntityHitResult ehr && ehr.getEntity() != null 
            && ehr.getEntity().getId() == entityId) {
            Vec3 hit = ehr.getLocation();
            double blend = 0.35;
            return new Vec3(
                baseX + (hit.x - baseX) * blend,
                baseY + (hit.y - baseY) * blend,
                baseZ + (hit.z - baseZ) * blend
            );
        }
        return new Vec3(baseX, baseY, baseZ);
    }
}
