package damage.engine.compat.tacz;

import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import damage.engine.compat.tacz.TaczServerHeadshotTracker;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Forge-side TaCZ headshot hook. Vanilla Forge TaCZ fires the forge-event-bus
 * EntityHurtByGunEvent.Pre (with isHeadShot) BEFORE dealing the damage, so the
 * mark is set before our broadcastPostDamage runs for that hit - unlike Post,
 * which fires after damage and could be missed when another target's damage
 * flushes our pending payload first (the leak that made the NEXT body shot
 * show as a crit).
 */
public class TaczForgeCompat {

    public static void register(IEventBus bus) {
        bus.addListener((EntityHurtByGunEvent.Pre event) -> {
            if (event.isHeadShot() && event.getHurtEntity() != null) {
                TaczServerHeadshotTracker.markHeadshot(event.getHurtEntity().getId());
            }
        });
    }
}
