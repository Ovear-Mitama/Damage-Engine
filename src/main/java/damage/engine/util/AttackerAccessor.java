package damage.engine.util;

import net.minecraft.entity.Entity;
import org.jetbrains.annotations.Nullable;

public interface AttackerAccessor {
    @Nullable Entity damageEngine$getAttacker();
}
