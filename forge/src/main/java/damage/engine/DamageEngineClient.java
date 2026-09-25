package damage.engine;

import damage.engine.client.gui.HomeScreen;
import damage.engine.network.NetworkHandler;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.ConfigGuiHandler;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    
    // 同 DamageEngine:LogUtils 只在 1.18.2+ 的 MC 库里,1.18/1.18.1 上会 NoClassDefFoundError
    public static final Logger LOGGER = LoggerFactory.getLogger("damageengine");

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

        // Register keybindings + config screen via ModContainer
        modEventBus.addListener((FMLClientSetupEvent event) -> {
            // 1.18.2 用 ClientRegistry 注册按键(1.19+ 才有 RegisterKeyMappingsEvent)。
            // 它内部会写 Minecraft#options.keyMappings,放到 enqueueWork 里在主线程执行。
            event.enqueueWork(() -> {
                net.minecraftforge.client.ClientRegistry.registerKeyBinding(configKeyBinding);
                net.minecraftforge.client.ClientRegistry.registerKeyBinding(toggleHudKeyBinding);
                net.minecraftforge.client.ClientRegistry.registerKeyBinding(clearDamageKeyBinding);
            });

            ModContainer container = net.minecraftforge.fml.ModList.get()
                .getModContainerById("damageengine").orElseThrow();
            // 1.18.2 的配置界面扩展点叫 ConfigGuiHandler.ConfigGuiFactory
            // (1.19 才改名为 ConfigScreenHandler.ConfigScreenFactory)
            container.registerExtensionPoint(
                ConfigGuiHandler.ConfigGuiFactory.class,
                () -> new ConfigGuiHandler.ConfigGuiFactory((mc, screen) -> new HomeScreen(screen))
            );
        });

        // ---- Client HUD / tick / matrix capture via native Forge events ----
        // Vanilla Forge 1.20.1 does not reliably load mods.toml-declared mixin
        // configs (that is a NeoForge feature), so the client functionality that
        // used to live in HudRenderMixin / ClientTickMixin / ForgeWorldRenderMixin
        // is wired up with events instead.

        // HUD 惯性要在原版 HUD 画之前算好,并在"整层 HUD"模式下先把偏移压进矩阵栈,
        // 这样后面所有层(原版 + 其它模组的 HUD 层)才会一起跟着让位。
        // 1.18.2 没有 RenderGuiEvent(1.19+ 才有),用 RenderGameOverlayEvent.Pre(ALL),
        // 它由 ForgeIngameGui.render 在整个 HUD 渲染的第一步触发。Pre 会按 ElementType
        // 多次触发,只取 ALL。
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
            (net.minecraftforge.client.event.RenderGameOverlayEvent.Pre ev) -> {
                if (ev.getType() != net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType.ALL) return;
                damage.engine.hud.DamageHud.INSTANCE.updateHudInertia();
                damage.engine.hud.DamageHud.INSTANCE.beginGlobalShift(ev.getMatrixStack());
            });

        // HUD render (replaces HudRenderMixin). 1.18.2 没有 RenderGuiEvent(1.19+ 才有),
        // 用 RenderGameOverlayEvent.Post(ElementType.ALL) - 它由 ForgeIngameGui.render
        // 在整个 HUD 渲染的最后一步触发,此时原版 GUI 的混合状态是生效的;
        // 而 RenderGameOverlayEvent.Pre 触发太早,alpha 通道会被忽略
        // (信息面板会渲染成不透明的黑块)。Post 会按 ElementType 多次触发,只取 ALL。
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
            (net.minecraftforge.client.event.RenderGameOverlayEvent.Post ev) -> {
                if (ev.getType() != net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType.ALL) return;
                Minecraft mc = Minecraft.getInstance();
                if (mc.screen instanceof damage.engine.client.gui.DamageConfigScreen) {
                    damage.engine.hud.DamageHud.INSTANCE.endGlobalShift(ev.getMatrixStack());
                    return;
                }
                if (mc.screen instanceof damage.engine.client.gui.HudEditorScreen) {
                    damage.engine.hud.DamageHud.INSTANCE.endGlobalShift(ev.getMatrixStack());
                    return;
                }
                // Force a clean 2D state just in case another mod polluted it.
                com.mojang.blaze3d.systems.RenderSystem.enableBlend();
                com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
                com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
                com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                try {
                    // 此时整层偏移(若有)还在栈上,DE 自己的 HUD 也就跟着走了
                    damage.engine.hud.DamageHud.INSTANCE.onHudRender(ev.getMatrixStack(), ev.getPartialTicks());
                    damage.engine.hud.DamageIndicator.render(ev.getMatrixStack(), ev.getPartialTicks());
                } finally {
                    damage.engine.hud.DamageHud.INSTANCE.endGlobalShift(ev.getMatrixStack());
                }
            });

        // Matrix capture (replaces ForgeWorldRenderMixin)
        registerLevelMatrixCapture();

        // Client-only mode health monitor (replaces LivingEntityClientMixin, which
        // Forge 1.20.1 cannot apply - LivingEntity loads too early). 1.18.2 里叫
        // LivingEvent.LivingUpdateEvent(LivingTickEvent 是 1.19+ 才改的名),它在客户端
        // 由客户端实体 tick 时触发。
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
            (net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent ev) -> {
                damage.engine.client.ClientHealthMonitor.onTick(ev.getEntityLiving());
            });

        // Client tick (replaces ClientTickMixin): server-mod check + keybinds + cleanup
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
            (net.minecraftforge.event.TickEvent.ClientTickEvent ev) -> {                if (ev.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
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
                    damage.engine.client.ClientHealthMonitor.cleanup();
                }
            });
    }

    public static Vec3 blendIndicatorPos(double baseX, double baseY, double baseZ, int entityId) {
        return damage.engine.client.IndicatorPos.blend(baseX, baseY, baseZ, entityId);
    }

    /**
     * 世界渲染末尾的矩阵抓取(替代 ForgeWorldRenderMixin)。事件名跨 Forge 版本不同:
     * <ul>
     *   <li>Forge 40+(1.18.2):用 {@code RenderLevelStageEvent} 的 AFTER_TRIPWIRE_BLOCKS 阶段
     *       ({@code RenderLevelLastEvent} 类还在,但 Forge 40 已不再触发它)</li>
     *   <li>Forge 38/39(1.18/1.18.1):只有 {@code RenderLevelLastEvent}
     *       ({@code RenderLevelStageEvent} 尚不存在)</li>
     * </ul>
     * 两个事件类在对方版本里都不存在,若直接写在本方法体里,缺类的一端加载本类时就会
     * NoClassDefFoundError,所以各自放进独立的嵌套类,先 Class.forName 探测再加载。
     */
    private static void registerLevelMatrixCapture() {
        if (tryRegisterLevelCapture("net.minecraftforge.client.event.RenderLevelStageEvent",
                "damage.engine.DamageEngineClient$LevelStageMatrixCapture")) {
            return;
        }
        tryRegisterLevelCapture(null, "damage.engine.DamageEngineClient$LevelLastMatrixCapture");
    }

    private static boolean tryRegisterLevelCapture(String probeClass, String captureClass) {
        try {
            if (probeClass != null) Class.forName(probeClass);
            Class.forName(captureClass).getMethod("register").invoke(null);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * 用事件里的 PoseStack(与 Fabric 的 BEFORE_ENTITIES matrixStack 同源),而不是
     * RenderSystem 的 modelview(此时它不是纯相机视图)。1.18.x 的矩阵类型是
     * com.mojang.math.Matrix4f(1.19.3 才换成 org.joml)。
     */
    private static void captureLevelMatrices(com.mojang.math.Matrix4f projection, com.mojang.math.Matrix4f view) {
        damage.engine.hud.DamageIndicator.captureMatrices(
            new com.mojang.math.Matrix4f(projection),
            new com.mojang.math.Matrix4f(view));
    }

    /**
     * Forge 40+(1.18.2):按渲染阶段触发。
     * <p>
     * register() 必须是 public:这两个类是被反射调用的(见
     * {@link #tryRegisterLevelCapture}),而 {@code Class#getMethod} 只返回 public 方法,
     * 写成包内可见会抛 NoSuchMethodException 并被吞掉,结果是矩阵抓取静默失效(跳字不显示)。
     */
    public static final class LevelStageMatrixCapture {
        public static void register() {
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.client.event.RenderLevelStageEvent ev) -> {
                    if (ev.getStage() == net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_TRIPWIRE_BLOCKS) {
                        captureLevelMatrices(ev.getProjectionMatrix(), ev.getPoseStack().last().pose());
                    }
                });
        }
    }

    /** Forge 38/39(1.18/1.18.1):世界渲染末尾一次性触发。同上,register() 需为 public。 */
    public static final class LevelLastMatrixCapture {
        public static void register() {
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.client.event.RenderLevelLastEvent ev) ->
                    captureLevelMatrices(ev.getProjectionMatrix(), ev.getPoseStack().last().pose()));
        }
    }
}
