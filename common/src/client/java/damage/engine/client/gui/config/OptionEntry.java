package damage.engine.client.gui.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.Collections;
import java.util.List;

/** 配置界面列表项的公共基类：负责悬停高亮与子控件事件转发。 */
public abstract class OptionEntry extends AbstractSelectionList.Entry<OptionEntry> {
    private double hoverAnim = 0;
    private String searchText = "";
    private Component hoverTooltip;
    private int lastX, lastY, lastW, lastH;

    /** 供顶部搜索框匹配的文本；空字符串表示该行不参与过滤。 */
    public String getSearchText() {
        return searchText;
    }

    protected void setSearchText(Component component) {
        this.searchText = component == null ? "" : component.getString();
    }

    protected void setHoverTooltip(Component tooltip) {
        this.hoverTooltip = tooltip;
    }

    /** 鼠标悬停在该行时返回自定义提示内容。 */
    public Component getTooltipAt(int mouseX, int mouseY) {
        if (hoverTooltip == null) return null;
        if (mouseX < lastX || mouseX > lastX + lastW || mouseY < lastY || mouseY > lastY + lastH) {
            return null;
        }
        return hoverTooltip;
    }

    /** 鼠标是否落在该行的渲染范围内。 */
    protected boolean isMouseOverRow(double mouseX, double mouseY) {
        return mouseX >= lastX && mouseX <= lastX + lastW
            && mouseY >= lastY && mouseY <= lastY + lastH;
    }

    @Override
    public void render(GuiGraphics g, int idx, int y, int x, int ew, int eh, int mx, int my,
                       boolean hovered, float dt) {
        lastX = x;
        lastY = y;
        lastW = ew;
        lastH = eh;
        boolean isHov = mx >= x && mx <= x + ew && my >= y && my < y + eh;
        if (isHov && shouldHighlight()) hoverAnim = Mth.lerp(0.1f, hoverAnim, 1.0f);
        else hoverAnim = Mth.lerp(0.1f, hoverAnim, 0.0f);
        if (hoverAnim > 0.01 && shouldHighlight()) {
            // 行内容左右各留了 10px 内边距，高亮需要补回列表左右边缘
            int left = x - 10;
            int right = x + ew + 10;
            g.fill(left, y, right, y + eh + 2, ((int) (26 * hoverAnim) << 24) | 0xFFFFFF);
        }
        renderContent(g, idx, y, x, ew, eh, mx, my, isHov, dt);
    }

    public abstract void renderContent(GuiGraphics g, int idx, int y, int x, int ew, int eh,
                                       int mx, int my, boolean hovered, float dt);

    public List<? extends GuiEventListener> children() { return Collections.emptyList(); }

    public List<? extends NarratableEntry> narratables() { return Collections.emptyList(); }

    protected boolean shouldHighlight() { return true; }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        for (GuiEventListener c : children()) {
            if (c instanceof EditBox tf) {
                if (tf.isMouseOver(mx, my)) { tf.setFocused(true); return true; }
                else tf.setFocused(false);
            } else if (c.mouseClicked(mx, my, btn)) return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        for (GuiEventListener c : children()) if (c.mouseReleased(mx, my, btn)) return true;
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        for (GuiEventListener c : children()) if (c.mouseDragged(mx, my, btn, dx, dy)) return true;
        return false;
    }

    @Override
    public boolean keyPressed(int kc, int sc, int mod) {
        for (GuiEventListener c : children()) if (c.keyPressed(kc, sc, mod)) return true;
        return false;
    }

    @Override
    public boolean charTyped(char ch, int mod) {
        for (GuiEventListener c : children()) if (c.charTyped(ch, mod)) return true;
        return false;
    }

    @Override
    public boolean keyReleased(int kc, int sc, int mod) {
        for (GuiEventListener c : children()) if (c.keyReleased(kc, sc, mod)) return true;
        return false;
    }
}
