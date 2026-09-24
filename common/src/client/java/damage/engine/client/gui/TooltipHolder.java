package damage.engine.client.gui;

import damage.engine.compat.Tooltip;

/**
 * 1.19 没有 {@code AbstractWidget#setTooltip}(1.19.4 才引入),
 * 自绘控件通过该接口保存并暴露悬停提示。
 */
public interface TooltipHolder {
    void setTooltip(Tooltip tooltip);

    Tooltip getTooltip();
}
