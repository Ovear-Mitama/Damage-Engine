package damage.engine;

import damage.engine.hud.rating.RatingManager;
import damage.engine.hud.session.DamageSessionManager;
import damage.engine.network.DamageMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.client.ClientCommandHandler;
import org.lwjgl.input.Keyboard;

/** 客户端入口：按键、命令与伤害包处理。 */
public final class DamageEngineClient {
    public static final String KEY_CATEGORY = "key.categories.damage_engine";

    public static KeyBinding clearKey;
    public static KeyBinding configKey;
    public static KeyBinding toggleHudKey;

    private DamageEngineClient() {
    }

    public static void init() {
        clearKey = new KeyBinding("key.damage_engine.clear_damage", Keyboard.KEY_NONE, KEY_CATEGORY);
        configKey = new KeyBinding("key.damage_engine.config", Keyboard.KEY_NONE, KEY_CATEGORY);
        toggleHudKey = new KeyBinding("key.damage_engine.toggle_hud", Keyboard.KEY_NONE, KEY_CATEGORY);
        ClientRegistry.registerKeyBinding(clearKey);
        ClientRegistry.registerKeyBinding(configKey);
        ClientRegistry.registerKeyBinding(toggleHudKey);

        ClientCommandHandler.instance.registerCommand(new CommandDamageEngine());
    }

    /** 由网络包在客户端主线程调用。 */
    public static void onDamage(DamageMessage message) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.player == null) return;

        Entity victim = mc.world.getEntityByID(message.targetId);
        boolean preferSwitch = mc.objectMouseOver != null && mc.objectMouseOver.entityHit == victim;
        DamageSessionManager.getInstance().addDamage(
            message.amount, message.crit, message.targetId,
            preferSwitch, message.killed, message.distance, message.unseen);
        // 评分/加分提示
        RatingManager.getInstance().addHit(message.amount, message.crit, message.killed,
            message.distance, message.unseen);
        DamageIndicator.add(message.x, message.y, message.z, message.amount, message.crit,
            message.killed, message.targetId);
    }

    public static void clearAll() {
        DamageSessionManager.getInstance().reset();
        RatingManager.getInstance().reset();
        DamageIndicator.clear();
    }
}
