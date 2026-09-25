package damage.engine.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ComponentRenderUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * 1.18 兼容层:1.18 没有 {@code net.minecraft.client.gui.components.Tooltip}(1.19.4 才引入),
 * {@code AbstractWidget} 也没有 {@code setTooltip}。
 * <p>
 * 这里只保留业务代码用到的 {@code create(Component)} 与 {@code toCharSequence(Minecraft)},
 * 提示文本由各处的自绘控件自己保存(见 {@code damage.engine.client.gui.TooltipHolder}),
 * 并在悬停时用 {@link GuiGraphics#renderTooltip(net.minecraft.client.gui.Font, List, int, int)} 画出来。
 */
public final class Tooltip {
    private final Component message;

    private Tooltip(Component message) {
        this.message = message;
    }

    public static Tooltip create(Component message) {
        return new Tooltip(message);
    }

    public Component getMessage() {
        return this.message;
    }

    /**
     * 转成可交给 {@code Screen#renderTooltip} 的多行文本(按换行/宽度拆分,保留样式)。
     * <p>
     * 1.19.4+ 的原版 Tooltip 会按屏幕宽度折行,1.18 这边没有,若只按 {@code \n} 断行,
     * 长提示(如伤害溢出、HUD 惯性)会一路铺到屏幕外。这里按半屏宽(至少 200px)折行,
     * 与原版观感一致。
     */
    public List<FormattedCharSequence> toCharSequence(Minecraft minecraft) {
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int wrapWidth = Math.max(screenWidth / 2, 200);
        return ComponentRenderUtils.wrapComponents(this.message, wrapWidth, minecraft.font);
    }
}
