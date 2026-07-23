package damage.engine;

import damage.engine.client.gui.DamageConfigScreen;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

@Mod(value = "damageengine", dist = Dist.CLIENT)
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
    
    public static final Logger LOGGER = LogUtils.getLogger();

    public DamageEngineClient(IEventBus modEventBus, ModContainer modContainer) {
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
        KeyMapping.resetMapping();

        // Register keybindings via NeoForge event
        modEventBus.addListener(DamageEngineClient::registerKeys);

        // Register config screen
        modContainer.registerExtensionPoint(
            IConfigScreenFactory.class,
            (container, screen) -> new DamageConfigScreen(screen)
        );
    }

    @SubscribeEvent
    static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(configKeyBinding);
        event.register(toggleHudKeyBinding);
        event.register(clearDamageKeyBinding);
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
