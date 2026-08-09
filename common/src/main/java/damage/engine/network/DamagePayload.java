package damage.engine.network;

/**
 * S2C damage payload, pure data record (platform-independent).
 * Serialization is handled by platform-specific network code.
 */
public record DamagePayload(int entityId, float amount, boolean isCrit, int attackerId, String debugInfo,
                            double posX, double posY, double posZ, boolean isProjectile, boolean killed) {
}
