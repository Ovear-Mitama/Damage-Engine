package damage.engine.client;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.Style;

/**
 * Text utilities for 1.20.1 compatibility.
 * withColor(int) was added in MC 1.20.5; in 1.20.1 we use withStyle + TextColor.fromRgb.
 */
public class TextHelper {
    public static MutableComponent colored(Component text, int color) {
        return text.copy().withStyle(style -> style.withColor(TextColor.fromRgb(color)));
    }
}
