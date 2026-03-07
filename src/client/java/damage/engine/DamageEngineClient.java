package damage.engine;

import damage.engine.client.gui.DamageConfigScreen;
import damage.engine.network.DamagePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import damage.engine.hud.DamageHud;
import damage.engine.hud.DamageSessionManager;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DamageEngineClient implements ClientModInitializer {
    public static KeyBinding configKeyBinding;
    public static KeyBinding toggleHudKeyBinding;
    
    public static final Logger LOGGER = LoggerFactory.getLogger("damage-engine");
    
    @Override
    public void onInitializeClient() {
        HudRenderCallback.EVENT.register(new DamageHud());
        
        // Debug KeyInput methods
        try {
            Class<?> keyInputClass = Class.forName("net.minecraft.client.input.KeyInput");
            for (java.lang.reflect.Method m : keyInputClass.getMethods()) {
                 LOGGER.info("DEBUG_KEYINPUT_METHOD: " + m.getName() + " -> " + m.getReturnType().getName());
            }
            // Also fields
            for (java.lang.reflect.Field f : keyInputClass.getFields()) {
                 LOGGER.info("DEBUG_KEYINPUT_FIELD: " + f.getName() + " -> " + f.getType().getName());
            }
        } catch (Exception e) { e.printStackTrace(); }
        
        // Debug EntryListWidget methods
        try {
            Class<?> listClass = net.minecraft.client.gui.widget.EntryListWidget.class;
            for (java.lang.reflect.Method m : listClass.getMethods()) {
                if (m.getName().toLowerCase().contains("background")) {
                    LOGGER.info("DEBUG_LIST_METHOD: " + m.getName());
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        
        // Debug SimpleSoundInstance
        try {
            Class<?> ssiClass = Class.forName("net.minecraft.client.sound.SimpleSoundInstance");
            LOGGER.info("DEBUG_SSI: Found SimpleSoundInstance");
            for (java.lang.reflect.Method m : ssiClass.getMethods()) {
                if (m.getName().contains("UI")) {
                    LOGGER.info("DEBUG_SSI_METHOD: " + m.getName());
                }
            }
        } catch (Exception e) {
            LOGGER.info("DEBUG_SSI: Not found");
        }
        
        // Debug PositionedSoundInstance.master signatures again
        try {
            Class<?> psiClass = net.minecraft.client.sound.PositionedSoundInstance.class;
            for (java.lang.reflect.Method m : psiClass.getMethods()) {
                if (m.getName().equals("master")) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("master(");
                    for (Class<?> p : m.getParameterTypes()) {
                        sb.append(p.getName()).append(", ");
                    }
                    sb.append(")");
                    LOGGER.info("DEBUG_PSI_MASTER: " + sb.toString());
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        
        // Debug Click class
        try {
            Class<?> clickClass = Class.forName("net.minecraft.client.gui.Click");
            LOGGER.info("DEBUG_CLICK: Found Click class");
            for (java.lang.reflect.Method m : clickClass.getMethods()) {
                 LOGGER.info("DEBUG_CLICK_METHOD: " + m.getName() + " -> " + m.getReturnType().getName());
            }
        } catch (Exception e) {
            LOGGER.info("DEBUG_CLICK: Not found");
        }
        
        // Debug KeyBinding methods
        try {
            Class<?> kbClass = net.minecraft.client.option.KeyBinding.class;
            for (java.lang.reflect.Method m : kbClass.getMethods()) {
                if (m.getName().toLowerCase().contains("name") || m.getName().toLowerCase().contains("key")) {
                    LOGGER.info("DEBUG_KB_METHOD: " + m.getName() + " -> " + m.getReturnType().getName());
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        
        // Robust KeyBinding registration via reflection
        try {
            Class<?> kbClass = net.minecraft.client.option.KeyBinding.class;
            java.lang.reflect.Constructor<?> targetCtor = null;
            Object categoryArg = "category.damage_engine";
            
            // 1. Try to find standard constructor: (String, int, String) or (String, InputUtil.Type, int, String)
            for (java.lang.reflect.Constructor<?> c : kbClass.getConstructors()) {
                Class<?>[] types = c.getParameterTypes();
                if (types.length == 3 && types[0] == String.class && types[1] == int.class && types[2] == String.class) {
                    targetCtor = c;
                    break;
                }
                if (types.length == 4 && types[0] == String.class && types[1] == net.minecraft.client.util.InputUtil.Type.class && types[2] == int.class && types[3] == String.class) {
                    targetCtor = c;
                    break;
                }
            }
            
            // 2. If not found, try constructor with Category object
            if (targetCtor == null) {
                Class<?> catClass = null;
                try {
                     // Try to find Category class
                     for (Class<?> inner : kbClass.getDeclaredClasses()) {
                         if (inner.getSimpleName().equals("Category")) {
                             catClass = inner;
                             break;
                         }
                     }
                     if (catClass == null) catClass = Class.forName("net.minecraft.client.option.KeyBinding$Category");
                } catch (Exception ignored) {}
                
                if (catClass != null) {
                    // Try to create Category instance
                    Object catInstance = null;
                    // Try constructor (String)
                    try {
                        java.lang.reflect.Constructor<?> catCtor = catClass.getDeclaredConstructor(String.class);
                        catCtor.setAccessible(true);
                        catInstance = catCtor.newInstance("damage_engine");
                    } catch (Exception e) {
                        // Try constructor (Identifier)
                        try {
                            java.lang.reflect.Constructor<?> catCtor = catClass.getDeclaredConstructor(Identifier.class);
                            catCtor.setAccessible(true);
                            catInstance = catCtor.newInstance(Identifier.of("damage-engine", "general"));
                        } catch (Exception ignored2) {}
                    }
                    
                    if (catInstance != null) {
                        categoryArg = catInstance;
                        // Find constructor taking Category
                        for (java.lang.reflect.Constructor<?> c : kbClass.getConstructors()) {
                            Class<?>[] types = c.getParameterTypes();
                            if (types.length >= 3) {
                                if (types[types.length-1] == catClass) {
                                    targetCtor = c;
                                    break;
                                }
                            }
                        }
                    }
                }
            }
            
            if (targetCtor != null) {
                Class<?>[] types = targetCtor.getParameterTypes();
                Object[] args = new Object[types.length];
                args[0] = "key.damage_engine.config";
                
                if (types.length == 3) {
                    args[1] = GLFW.GLFW_KEY_UNKNOWN;
                    args[2] = categoryArg;
                } else if (types.length == 4) {
                    args[1] = net.minecraft.client.util.InputUtil.Type.KEYSYM;
                    args[2] = GLFW.GLFW_KEY_UNKNOWN;
                    args[3] = categoryArg;
                }
                
                configKeyBinding = (KeyBinding) targetCtor.newInstance(args);
                
                // Toggle HUD key
                args[0] = "key.damage_engine.toggle_hud";
                toggleHudKeyBinding = (KeyBinding) targetCtor.newInstance(args);
                
                KeyBindingHelper.registerKeyBinding(configKeyBinding);
                KeyBindingHelper.registerKeyBinding(toggleHudKeyBinding);
                LOGGER.info("Successfully registered keybindings via reflection.");
            } else {
                LOGGER.error("Could not find suitable KeyBinding constructor.");
            }
            
        } catch (Exception e) {
             LOGGER.error("Failed to register keybindings via reflection", e);
        }
        
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null) {
                if (configKeyBinding != null) {
                    while (configKeyBinding.wasPressed()) {
                        client.setScreen(new DamageConfigScreen(client.currentScreen));
                    }
                }
                if (toggleHudKeyBinding != null) {
                    while (toggleHudKeyBinding.wasPressed()) {
                        DamageEngineConfig config = DamageEngineConfig.getInstance();
                        config.showDamage = !config.showDamage;
                        config.save();
                    }
                }
            }

            if (!client.isPaused()) {
                DamageSessionManager.getInstance().tick();
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(DamagePayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().world == null || context.client().player == null) return;
                
                if (DamageEngineConfig.getInstance().debugMode) {
                    if (payload.debugInfo() != null && !payload.debugInfo().isEmpty()) {
                        context.client().player.sendMessage(Text.literal("[调试] ").withColor(0xFBFB54).append(Text.literal(payload.debugInfo()).withColor(0xFFFFFF)), false);
                    }
                }

                if (payload.attackerId() != context.client().player.getId()) {
                    return;
                }
                
                DamageSessionManager.getInstance().addDamage(payload.amount(), payload.isCrit());
            });
        });
        // Print PositionedSoundInstance methods for debugging
        try {
            Class<?> psiClass = net.minecraft.client.sound.PositionedSoundInstance.class;
            for (java.lang.reflect.Method m : psiClass.getMethods()) {
                if (m.getName().equals("master")) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(m.getName()).append("(");
                    for (Class<?> p : m.getParameterTypes()) {
                        sb.append(p.getName()).append(", ");
                    }
                    sb.append(") -> ").append(m.getReturnType().getName());
                    System.out.println("DEBUG_METHOD: " + sb.toString());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
