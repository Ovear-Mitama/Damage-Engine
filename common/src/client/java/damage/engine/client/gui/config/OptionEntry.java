package damage.engine.client.gui.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.Collections;
import java.util.List;

/** 配置界面列表项的公共基类：负责悬停高亮与子控件事件转发。 */
public abstract class OptionEntry extends AbstractSelectionList.Entry<OptionEntry> {
    /** 行右侧控件区域的统一尺寸与右边距。 */
    protected static final int CONTROL_WIDTH = 100;
    protected static final int CONTROL_HEIGHT = 20;
    private static final int CONTROL_RIGHT_MARGIN = 10;

    private double hoverAnim = 0;
    private String searchText = "";
    private Component hoverTooltip;
    private Component label;
    private int lastX, lastY, lastW, lastH;

    /** 供顶部搜索框匹配的文本；空字符串表示该行不参与过滤。 */
    public String getSearchText() {
        return searchText;
    }

    protected void setSearchText(Component component) {
        this.searchText = component == null ? "" : component.getString();
    }

    /** 设置行标题（同时登记搜索文本）。 */
    protected void setLabel(String key) {
        setLabel(Component.translatable(key));
    }

    protected void setLabel(Component component) {
        this.label = component;
        setSearchText(component);
    }

    protected Component label() {
        return label;
    }

    /** 统一绘制左侧标题。 */
    protected void renderLabel(GuiGraphics g, int x, int y) {
        if (label != null) {
            g.drawString(Minecraft.getInstance().font, label, x, y + 8, 0xFFFFFFFF);
        }
    }

    /** 行右侧控件的统一坐标。 */
    protected int controlX(int x, int ew) {
        return x + ew - CONTROL_WIDTH - CONTROL_RIGHT_MARGIN;
    }

    protected int controlY(int y) {
        return y + 2;
    }

    /** 摆放并绘制单个控件（按钮 / 滑条）。 */
    protected void renderControl(GuiGraphics g, AbstractWidget control, int x, int y, int ew,
                                 int mx, int my, float dt) {
        control.setX(controlX(x, ew));
        control.setY(controlY(y));
        control.render(g, mx, my, dt);
    }

    /** 按钮类控件：每次绘制前清除焦点，避免残留焦点描边。 */
    protected void renderButton(GuiGraphics g, AbstractWidget button, int x, int y, int ew,
                                int mx, int my, float dt) {
        button.setFocused(false);
        renderControl(g, button, x, y, ew, mx, my, dt);
    }

    /** 文本输入框的统一边框、摆位与绘制。 */
    protected void layoutTextField(GuiGraphics g, EditBox field, int x, int y, int ew,
                                   int mx, int my, float dt) {
        int bx = controlX(x, ew);
        int by = controlY(y);
        field.setX(bx + 4);
        field.setY(by + 6);
        field.setWidth(CONTROL_WIDTH - 8);
        field.setHeight(12);
        g.fill(bx, by, bx + CONTROL_WIDTH, by + CONTROL_HEIGHT, 0x20000000);
        int bc = (field.isFocused() || field.isMouseOver(mx, my)) ? 0xFFFFFFFF : 0xFFA0A0A0;
        g.fill(bx, by, bx + CONTROL_WIDTH, by + 1, bc);
        g.fill(bx, by + CONTROL_HEIGHT - 1, bx + CONTROL_WIDTH, by + CONTROL_HEIGHT, bc);
        g.fill(bx, by, bx + 1, by + CONTROL_HEIGHT, bc);
        g.fill(bx + CONTROL_WIDTH - 1, by, bx + CONTROL_WIDTH, by + CONTROL_HEIGHT, bc);
        field.render(g, mx, my, dt);
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
