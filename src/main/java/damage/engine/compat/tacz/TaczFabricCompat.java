package damage.engine.compat.tacz;

import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fabric-side hook for TaCZ: Refabricated compatibility.
 *
 * <p>Subscribes to the official {@link EntityHurtByGunEvent.PRE} Fabric event,
 * which fires right before the target's {@code LivingEntity.hurt} and carries
 * TaCZ's own headshot result ({@code isHeadShot()}). We record the victim's id
 * so the shared damage tracker can mark TaCZ headshots as crits. Only loaded
 * when TaCZ is present at runtime.</p>
 */
public final class TaczFabricCompat {
    private static final Logger LOGGER = LoggerFactory.getLogger("damage-engine-tacz");

    private TaczFabricCompat() {}

    public static void init() {
        EntityHurtByGunEvent.PRE.register(event -> {
            if (!event.isHeadShot()) return;
            Entity target = event.getHurtEntity();
            if (target != null) {
                TaczServerHeadshotTracker.markHeadshot(target.getId());
            }
        });
        LOGGER.info("TaCZ Refabricated detected - headshot crit compatibility enabled");
    }
}
