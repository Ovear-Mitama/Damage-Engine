package damage.engine;

import damage.engine.client.gui.HomeScreen;
import damage.engine.network.NetworkHandler;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

public class DamageEngineClient {
    public static KeyMapping configKeyBinding;
    public static KeyMapping toggleHudKeyBinding;
    public static KeyMapping clearDamageKeyBinding;
    
    /** Whether the server has Damage Engine installed. Only valid after serverModChecked is true. */
    public static volatile boolean serverHasMod = false;
    /** Whether we have completed the server mod presence check. */
    public static volatile boolean serverModChecked = false;
    /** Tick counter for delayed server mod check. */
    public static int joinCheckTicks = -1;
    /** Platform-specific handshake sender, called by ClientTickMixin. */
    public static Runnable handshakeSender = null;
    
    public static final Logger LOGGER = LogUtils.getLogger();

    private static final damage.engine.hud.DamageHud damageHud = new damage.engine.hud.DamageHud();

    public DamageEngineClient() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Set up handshake sender for multiplayer server mod detection
        handshakeSender = () -> NetworkHandler.sendHandshakeToServer();

        // Create keybindings directly
        configKeyBinding = new KeyMapping(
            "key.damage_engine.config",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "key.category.damage-engine.general"
        );
        toggleHudKeyBinding = new KeyMapping(
            "key.damage_engine.toggle_hud",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "key.category.damage-engine.general"
        );
        clearDamageKeyBinding = new KeyMapping(
            "key.damage_engine.clear_damage",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "key.category.damage-engine.general"
        );

        // Sync to ClientKeybindings holder class for config screen access
        ClientKeybindings.configKeyBinding = configKeyBinding;
        ClientKeybindings.toggleHudKeyBinding = toggleHudKeyBinding;
        ClientKeybindings.clearDamageKeyBinding = clearDamageKeyBinding;

        // Register keybindings via Forge event
        modEventBus.addListener(this::registerKeys);

        // Register config screen via ModContainer
        modEventBus.addListener((FMLClientSetupEvent event) -> {
            ModContainer container = net.minecraftforge.fml.ModList.get()
                .getModContainerById("damageengine").orElseThrow();
            container.registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, screen) -> new HomeScreen(screen))
            );
        });

        // ---- Client HUD / tick / matrix capture via native Forge events ----
        // Vanilla Forge 1.20.1 does not reliably load mods.toml-declared mixin
        // configs (that is a NeoForge feature), so the client functionality that
        // used to live in HudRenderMixin / ClientTickMixin / ForgeWorldRenderMixin
        // is wired up with events instead.

        // HUD render (replaces HudRenderMixin). RenderGuiEvent.Post fires at the
        // very end of Gui.render, where the vanilla GUI blend state is active -
        // RenderGuiOverlayEvent ran too early and its GuiGraphics ignored the
        // alpha channel entirely (info panel rendered as an opaque black box).
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
            (net.minecraftforge.client.event.RenderGuiEvent.Post ev) -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.screen instanceof damage.engine.client.gui.DamageConfigScreen) return;
                if (mc.screen instanceof damage.engine.client.gui.HudEditorScreen) return;
                // Force a clean 2D state just in case another mod polluted it.
                com.mojang.blaze3d.systems.RenderSystem.enableBlend();
                com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
                com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
                com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                damageHud.onHudRender(ev.getGuiGraphics(), ev.getPartialTick());
                damage.engine.hud.DamageIndicator.render(ev.getGuiGraphics(), ev.getPartialTick());
            });

        // Matrix capture (replaces ForgeWorldRenderMixin)
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
            (net.minecraftforge.client.event.RenderLevelStageEvent ev) -> {
                if (ev.getStage() == net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_TRIPWIRE_BLOCKS) {
                    // Use the event's PoseStack (same source as Fabric's
                    // BEFORE_ENTITIES matrixStack) instead of RenderSystem's
                    // modelview, which is not the pure camera view at this point.
                    damage.engine.hud.DamageIndicator.captureMatrices(
                        new org.joml.Matrix4f(ev.getProjectionMatrix()),
                        new org.joml.Matrix4f(ev.getPoseStack().last().pose()));
                }
            });

        // Client tick (replaces ClientTickMixin): server-mod check + keybinds + cleanup
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
            (net.minecraftforge.event.TickEvent.ClientTickEvent ev) -> {
                if (ev.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
                Minecraft mc = Minecraft.getInstance();
                if (mc.player == null) return;

                if (DamageEngineClient.joinCheckTicks > 0) {
                    DamageEngineClient.joinCheckTicks--;
                    if (DamageEngineClient.joinCheckTicks == 0) {
                        if (!DamageEngineClient.serverModChecked) {
                            if (mc.getSingleplayerServer() != null) {
                                DamageEngineClient.serverHasMod = true;
                            } else {
                                DamageEngineClient.serverHasMod = false;
                                if (DamageEngineClient.handshakeSender != null) {
                                    DamageEngineClient.handshakeSender.run();
                                }
                            }
                            DamageEngineClient.serverModChecked = true;
                        }
                    }
                }

                damage.engine.DamageEngineConfig config = damage.engine.DamageEngineConfig.getInstance();
                if (ClientKeybindings.configKeyBinding != null) {
                    while (ClientKeybindings.configKeyBinding.consumeClick()) {
                        mc.setScreen(new HomeScreen(mc.screen));
                    }
                }
                if (ClientKeybindings.toggleHudKeyBinding != null) {
                    while (ClientKeybindings.toggleHudKeyBinding.consumeClick()) {
                        config.showDamage = !config.showDamage;
                        config.save();
                    }
                }
                if (ClientKeybindings.clearDamageKeyBinding != null) {
                    while (ClientKeybindings.clearDamageKeyBinding.consumeClick()) {
                        damage.engine.hud.DamageSessionManager.getInstance().reset();
                        damage.engine.hud.DamageIndicator.clearAll();
                    }
                }

                if (!mc.isPaused()) {
                    damage.engine.hud.DamageSessionManager.getInstance().tick();
                    damage.engine.hud.DamageIndicator.tickAndCleanup();
                    damage.engine.client.ClientAttackTracker.getInstance().cleanup();
                }
            });
    }

    private void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(configKeyBinding);
        event.register(toggleHudKeyBinding);
        event.register(clearDamageKeyBinding);
    }
}
