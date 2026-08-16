package damage.engine.client;

import damage.engine.DamageEngineClient;
import damage.engine.DamageEngineConfig;
import damage.engine.hud.DamageIndicator;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-only mode health monitor: detects damage / healing by comparing a living
 * entity's health each tick, then feeds the client-side damage tracker (which
 * spawns damage floats when the server does NOT have Damage Engine installed).
 *
 * <p>Driven by {@code LivingEntityClientMixin} on Fabric and by the Forge
 * {@code LivingTickEvent} on Forge (Forge 1.20.1 cannot mix into LivingEntity -
 * it is loaded too early), so the per-entity state lives in a static map instead
 * of {@code @Unique} mixin fields.</p>
 */
public final class ClientHealthMonitor {
    // Per-entity state: [lastHealth, lastAbsorption, lastDamageTime, initialized]
    private static final Map<Integer, float[]> STATE = new ConcurrentHashMap<>();

    private ClientHealthMonitor() {}

    public static void onTick(LivingEntity self) {
        if (!self.level().isClientSide()) return;

        int id = self.getId();
        float h = self.getHealth();
        float a = self.getAbsorptionAmount();
        float[] st = STATE.get(id);
        if (st == null) {
            STATE.put(id, new float[]{h, a, 0f, 1f});
            return;
        }

        float lastHealth = st[0];
        float lastAbsorption = st[1];

        float healthLost = lastHealth - h;
        float absorptionLost = lastAbsorption - a;
        float actualDamage = healthLost + absorptionLost;

        boolean killed = !self.isAlive() && (lastHealth > 0 || lastAbsorption > 0);

        // Only process damage when server does NOT have the mod (client-only mode).
        // When the server has the mod it sends DamagePayload; using both would
        // double the indicators.
        if ((actualDamage > 0.001f || killed) && !DamageEngineClient.serverHasMod) {
            long now = System.currentTimeMillis();
            if (now - st[2] > 50) { // dedupe rapid health syncs
                float reportedDamage = killed ? Math.max(actualDamage, 0) : actualDamage;
                ClientHealthTracker.onEntityDamaged(self, reportedDamage, killed);
                st[2] = now;
            }
        }

        // Detect healing (health increase) - show globally for all entities
        if (h > lastHealth + 0.001f) {
            float healAmount = h - lastHealth;
            DamageEngineConfig config = DamageEngineConfig.getInstance();
            if (config.showDamage && config.showHealIndicator) {
                Minecraft client = Minecraft.getInstance();
                if (client.player != null && client.level != null) {
                    double dx = self.getX() - client.player.getX();
                    double dy = self.getY() - client.player.getY();
                    double dz = self.getZ() - client.player.getZ();
                    double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

                    // For heal indicators, use the indicator distance (0 = unlimited)
                    float maxDistance = config.globalIndicatorMaxDistance;
                    if (maxDistance <= 0 || distance <= maxDistance) {
                        double posX = self.getX();
                        double posY = self.getY() + self.getEyeHeight() * 0.6;
                        double posZ = self.getZ();
                        DamageIndicator.addIndicator(id, posX, posY, posZ, healAmount, false, false, true);
                    }
                }
            }
        }

        st[0] = h;
        st[1] = a;
    }

    /** Drop state for entities no longer present in the client world. */
    public static void cleanup() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            STATE.clear();
            return;
        }
        STATE.keySet().removeIf(id -> client.level.getEntity(id) == null);
    }
}
