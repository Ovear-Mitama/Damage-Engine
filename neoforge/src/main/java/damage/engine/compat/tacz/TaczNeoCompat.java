package damage.engine.compat.tacz;

import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NeoForge-side hook for TaCZ (Timeless and Classics Zero) NeoForge port compatibility.
 *
 * <p>Subscribes to the official {@link EntityHurtByGunEvent.Pre} game event, which
 * fires right before the target's {@code LivingEntity.hurt} and carries TaCZ's own
 * headshot result ({@code isHeadShot()}). We record the victim's id so the shared
 * damage tracker can mark TaCZ headshots as crits. Only loaded when TaCZ is present
 * at runtime.</p>
 */
public final class TaczNeoCompat {
    private static final Logger LOGGER = LoggerFactory.getLogger("damage-engine-tacz");

    private TaczNeoCompat() {}

    public static void init() {
        // EntityHurtByGunEvent.Pre is posted on the NeoForge game event bus (SERVER side only)
        NeoForge.EVENT_BUS.addListener(TaczNeoCompat::onEntityHurtByGun);
        LOGGER.info("TaCZ NeoForge detected - headshot crit compatibility enabled");
    }

    private static void onEntityHurtByGun(EntityHurtByGunEvent.Pre event) {
        if (!event.isHeadShot()) return;
        Entity target = event.getHurtEntity();
        if (target != null) {
            TaczServerHeadshotTracker.markHeadshot(target.getId());
        }
    }
}
