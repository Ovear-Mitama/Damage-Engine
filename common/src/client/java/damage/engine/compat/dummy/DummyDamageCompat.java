package damage.engine.compat.dummy;

import damage.engine.DamageEngineClient;
import damage.engine.DamageEngineConfig;
import damage.engine.hud.indicator.DamageIndicator;
import damage.engine.hud.session.DamageSessionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Client-only fallback for MmmMmmMmmMmm (dummmmmmy) target dummies.
 *
 * <p>Dummies recharge their health before the value is synced to the client, so the
 * health-diff based client-only tracker never observes any damage on them. The dummy
 * mod itself broadcasts a damage-number message to clients; we hook that message and
 * feed the normal indicator/session pipeline.</p>
 *
 * <p>Only active when the server does NOT have Damage Engine installed, otherwise
 * the server payload already covers the same hit.</p>
 */
public final class DummyDamageCompat {
    private DummyDamageCompat() {}

    public static void onDamageNumber(int entityId, float amount, boolean crit) {
        if (!(amount > 0f)) return;
        if (!DamageEngineClient.serverModChecked || DamageEngineClient.serverHasMod) return;

        DamageEngineConfig config = DamageEngineConfig.getInstance();
        if (!config.showDamage || !config.showDamageIndicator) return;

        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            if (client.level == null || client.player == null) return;
            Entity dummy = client.level.getEntity(entityId);
            if (dummy == null) return;

            double maxDistance = config.globalIndicatorMaxDistance;
            if (maxDistance > 0 && dummy.distanceToSqr(client.player) > maxDistance * maxDistance) return;

            boolean preferSwitchTarget = !(client.hitResult instanceof EntityHitResult hit)
                || hit.getEntity() == null || hit.getEntity().getId() == entityId;
            DamageSessionManager.getInstance().addDamage(amount, crit, entityId, preferSwitchTarget);

            Vec3 pos = DamageEngineClient.blendIndicatorPos(
                dummy.getX(), dummy.getY() + dummy.getEyeHeight() * 0.6, dummy.getZ(), entityId);
            DamageIndicator.addIndicator(dummy, pos.x, pos.y, pos.z, amount, crit, false);
        });
    }
}
