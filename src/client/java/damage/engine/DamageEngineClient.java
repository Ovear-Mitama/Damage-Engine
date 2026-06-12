package damage.engine;

import damage.engine.client.gui.DamageConfigScreen;
import damage.engine.network.DamagePayload;
import damage.engine.network.HandshakePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import damage.engine.hud.DamageHud;
import damage.engine.hud.DamageIndicator;
import damage.engine.hud.DamageSessionManager;
import damage.engine.hud.RatingManager;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
// import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents; // Not available in this Fabric API version
import net.minecraft.network.chat.Component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DamageEngineClient implements ClientModInitializer {
    public static KeyMapping configKeyBinding;
    public static KeyMapping toggleHudKeyBinding;
    public static KeyMapping clearDamageKeyBinding;
    
    /** Whether the server has Damage Engine installed. Only valid after serverModChecked is true. */
    public static boolean serverHasMod = false;
    /** Whether we have completed the server mod presence check. */
    public static boolean serverModChecked = false;
    private static int joinCheckTicks = -1;
    
    public static final Logger LOGGER = LoggerFactory.getLogger("damage-engine");
    private static final KeyMapping.Category KEYBIND_CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("damage-engine", "general"));

    private static KeyMapping registerKeyBinding(String translationKey, int defaultKeyCode) {
        return KeyBindingHelper.registerKeyBinding(new KeyMapping(
            translationKey,
            defaultKeyCode,
            KEYBIND_CATEGORY
        ));
    }
    
    @Override
    public void onInitializeClient() {
        HudRenderCallback.EVENT.register(new DamageHud());
        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            DamageIndicator.render(context, tickCounter.getGameTimeDeltaTicks());
        });
        
        // Capture world projection matrix for FOV/zoom compatibility
        // WorldRenderEvents not available in this Fabric API version
        /* WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            DamageIndicator.captureProjection(new org.joml.Matrix4f(com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix()));
        }); */
        
        // 检测服务端是否安装了本 mod
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            serverHasMod = false;
            serverModChecked = false;
            joinCheckTicks = 40; // 2 seconds delay before checking
        });

        configKeyBinding = registerKeyBinding("key.damage_engine.config", GLFW.GLFW_KEY_UNKNOWN);
        toggleHudKeyBinding = registerKeyBinding("key.damage_engine.toggle_hud", GLFW.GLFW_KEY_UNKNOWN);
        clearDamageKeyBinding = registerKeyBinding("key.damage_engine.clear_damage", GLFW.GLFW_KEY_UNKNOWN);
        
        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
            if (minecraft.player != null) {
                // 检测服务端 mod 安装状态
                if (joinCheckTicks > 0) {
                    joinCheckTicks--;
                    if (joinCheckTicks == 0) {
                        if (minecraft.getSingleplayerServer() != null) {
                            // 单人游戏：集成服务器一定安装了本 mod
                            serverHasMod = true;
                        } else {
                            // 多人游戏：通过握手包检测
                            serverHasMod = ClientPlayNetworking.canSend(HandshakePayload.ID);
                        }
                        serverModChecked = true;
                    }
                }
                
                // 首次进入世界时显示提示信息
                DamageEngineConfig config = DamageEngineConfig.getInstance();
                if (!config.hasShownWelcomeMessage) {
                    minecraft.player.displayClientMessage(
                        Component.translatable("text.damage-engine.welcome_message").withColor(0xB1EAC2),
                        false
                    );
                    config.hasShownWelcomeMessage = true;
                    config.save();
                }
                
                if (configKeyBinding != null) {
                    while (configKeyBinding.consumeClick()) {
                        minecraft.setScreen(new DamageConfigScreen(minecraft.screen));
                    }
                }
                if (toggleHudKeyBinding != null) {
                    while (toggleHudKeyBinding.consumeClick()) {
                        config.showDamage = !config.showDamage;
                        config.save();
                    }
                }
                if (clearDamageKeyBinding != null) {
                    while (clearDamageKeyBinding.consumeClick()) {
                        DamageSessionManager.getInstance().reset();
                        DamageIndicator.clearAll();
                    }
                }
            }

            if (!minecraft.isPaused()) {
                DamageSessionManager.getInstance().tick();
                DamageIndicator.tickAndCleanup();
            }
        });
        
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("damage_engine")
                .then(ClientCommandManager.literal("clear")
                    .executes(ctx -> {
                        DamageSessionManager.getInstance().reset();
                        DamageIndicator.clearAll();
                        if (ctx.getSource().getPlayer() != null) {
                            ctx.getSource().getPlayer().displayClientMessage(
                                Component.literal("[Damage Engine] " ).withColor(0xB3EDC4).append(Component.translatable("text.damage-engine.cleared").withColor(0xFFFFFF)),
                                false
                            );
                        }
                        return 1;
                    }))

            );
            

        });

        ClientPlayNetworking.registerGlobalReceiver(DamagePayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().level == null || context.client().player == null) return;
                
                // 收到了 DamagePayload 说明服务端安装了本 mod
                if (!serverModChecked) {
                    serverHasMod = true;
                    serverModChecked = true;
                }
                
                DamageEngineConfig config = DamageEngineConfig.getInstance();
                
                // Debug: show damage info (includes server-side debugInfo)
                if (config.debugShowDamageInfo) {
                    if (payload.debugInfo() != null && !payload.debugInfo().isEmpty()) {
                        context.client().player.displayClientMessage(
                            Component.literal("[DE Debug] ").withColor(0xB3EDC4).append(Component.literal(payload.debugInfo()).withColor(0xFFFFFF)),
                            false
                        );
                    }
                    context.client().player.displayClientMessage(
                        Component.literal("[DE Debug] ").withColor(0xB3EDC4)
                            .append(Component.literal("伤害: " + String.format("%.1f", payload.amount()) 
                            + (payload.isCrit() ? " 暴击" : "") + " | 实体: " + payload.entityId()
                            + " | 投射物: " + (payload.isProjectile() ? "是" : "否")).withColor(0xFFFFFF)),
                        false
                    );
                }

                if (payload.attackerId() != context.client().player.getId()) {
                    return;
                }
                
                boolean preferSwitchTarget = false;
                try {
                    if (context.client().hitResult instanceof EntityHitResult ehr) {
                        preferSwitchTarget = ehr.getEntity() != null && ehr.getEntity().getId() == payload.entityId();
                    }
                } catch (Exception ignored) {
                }
                
                DamageSessionManager.getInstance().addDamage(payload.amount(), payload.isCrit(), payload.entityId(), preferSwitchTarget);
                
                // Add damage indicator
                if (config.showDamageIndicator && payload.amount() > 0) {
                    Vec3 pos = payload.isProjectile() 
                        ? new Vec3(payload.posX(), payload.posY(), payload.posZ())
                        : blendIndicatorPos(payload.posX(), payload.posY(), payload.posZ(), payload.entityId());
                    DamageIndicator.addIndicator(pos.x, pos.y, pos.z,
                        payload.amount(), payload.isCrit(), false);
                }
                if (config.showKillIndicator && payload.killed()) {
                    Vec3 pos = payload.isProjectile() 
                        ? new Vec3(payload.posX(), payload.posY(), payload.posZ())
                        : blendIndicatorPos(payload.posX(), payload.posY(), payload.posZ(), payload.entityId());
                    DamageIndicator.addIndicator(pos.x, pos.y, pos.z,
                        payload.amount(), false, true);
                }
                
                // Debug: show rating
                if (config.debugShowRating && context.client().player != null) {
                    RatingManager rm = RatingManager.getInstance();
                    if (rm.isVisible()) {
                        context.client().player.displayClientMessage(
                            Component.literal("[DE Debug] ").withColor(0xB3EDC4)
                                .append(Component.literal("当前评分: " + rm.getGrade() + " | 分数: " + String.format("%.1f", rm.getScore())).withColor(0xFFFFFF)),
                            true
                        );
                    }
                }
            });
        });
        

    }
    
    /**
     * Blend indicator position toward crosshair hit point for non-projectile attacks.
     * Moves the indicator ~35% toward where the player is aiming, so it doesn't always
     * appear at the entity center but also doesn't block the crosshair.
     */
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
