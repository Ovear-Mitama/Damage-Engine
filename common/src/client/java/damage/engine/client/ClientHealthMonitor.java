package damage.engine.client;

import damage.engine.DamageEngineClient;
import damage.engine.DamageEngineConfig;
import damage.engine.hud.indicator.DamageIndicator;
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
    // Per-entity state: [lastHealth, lastAbsorption, lastDamageTimeMs, initialized]
    // 时间戳必须用 double 存：float 只有约 7 位有效数字，装不下 1.7e12 的毫秒时间戳，
    // 差值会被量化到 ~131 秒，导致第一次伤害后长时间不再计数。
    private static final Map<Integer, double[]> STATE = new ConcurrentHashMap<>();

    private ClientHealthMonitor() {}

    public static void onTick(LivingEntity self) {
        if (!self.level().isClientSide()) return;
        // Target dummies recharge their health before it reaches the client, so health
        // diffs are meaningless for them. Their damage numbers come from
        // DummyDamageCompat via the dummy mod's own message instead.
        if (isTargetDummy(self)) return;

        int id = self.getId();
        float h = self.getHealth();
        float a = self.getAbsorptionAmount();
        double[] st = STATE.get(id);
        if (st == null) {
            STATE.put(id, new double[]{h, a, 0d, 1d});
            return;
        }

        float lastHealth = (float) st[0];
        float lastAbsorption = (float) st[1];

        float healthLost = lastHealth - h;
        float absorptionLost = lastAbsorption - a;
        float actualDamage = healthLost + absorptionLost;

        boolean killed = !self.isAlive() && (lastHealth > 0 || lastAbsorption > 0);

        // Only process damage when server does NOT have the mod (client-only mode).
        // When the server has the mod it sends DamagePayload; using both would
        // double the indicators.
        if ((actualDamage > 0.001f || killed) && !DamageEngineClient.serverHasMod) {
            long now = System.currentTimeMillis();
            long lastProcessed = (long) st[2];
            if (killed || now - lastProcessed > 50) { // dedupe rapid health syncs
                float reportedDamage = killed ? Math.max(actualDamage, 0) : actualDamage;
                ClientHealthTracker.onEntityDamaged(self, reportedDamage, killed);
                st[2] = now;
                // 只有真正处理完才推进基线；50ms 内的掉血保留基线，
                // 下一 tick 把累计差值一起结算，避免连续命中被永久丢弃。
                st[0] = h;
                st[1] = a;
                return;
            }
            return;
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
                        DamageIndicator.addIndicator(self, posX, posY, posZ, healAmount, false, false, true);
                    }
                }
            }
        }

        st[0] = h;
        st[1] = a;
    }

    /** Target dummies are handled by DummyDamageCompat, not by health diffs. */
    private static final ClassValue<Boolean> TARGET_DUMMY = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            return type.getName().startsWith("net.mehvahdjukaar.dummmmmmy.");
        }
    };

    private static boolean isTargetDummy(LivingEntity self) {
        // ClassValue 按类缓存：避免每 tick 对每个生物都调用 Class#getName() 生成字符串
        return TARGET_DUMMY.get(self.getClass());
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
