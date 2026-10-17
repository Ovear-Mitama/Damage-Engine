package damage.engine.client.gui.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.util.Mth;

/** 配置界面通用的可滚动列表。 */
public class OptionListWidget extends AbstractSelectionList<OptionEntry> {
    private double targetScroll = 0;
    private boolean isSmoothScrolling = false;
    private boolean scrolling = false;

    public OptionListWidget(Minecraft mc, int w, int h, int y, int itemHeight) {
        super(mc, w, h, y, itemHeight);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (mx >= getScrollbarX() && mx <= getScrollbarX() + 6) {
            scrolling = true;
            return true;
        }
        // 点击另一行的输入框时，清掉其余输入框的焦点（避免旧的焦点边框残留）
        EditBox hit = null;
        for (GuiEventListener child : this.children()) {
            if (child instanceof OptionEntry entry) {
                for (GuiEventListener inner : entry.children()) {
                    if (inner instanceof EditBox box && box.isMouseOver(mx, my)) hit = box;
                }
            }
        }
        for (GuiEventListener child : this.children()) {
            if (child instanceof OptionEntry entry) {
                for (GuiEventListener inner : entry.children()) {
                    if (inner instanceof EditBox box) box.setFocused(box == hit);
                }
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        scrolling = false;
        return super.mouseReleased(mx, my, btn);
    }

    @Override protected void renderListBackground(GuiGraphics g) {}
    @Override protected void renderListSeparators(GuiGraphics g) {}
    @Override protected void renderSelection(GuiGraphics g, int t, int w, int h, int oc, int ic) {}
    @Override public void updateWidgetNarration(NarrationElementOutput n) {}

    public void clearEntriesPublic() { this.clearEntries(); }
    public void addEntryPublic(OptionEntry e) { this.addEntry(e); }

    /** 当前鼠标位置下需要显示的自定义提示。 */
    public net.minecraft.network.chat.Component getTooltipAt(double mx, double my) {
        // 鼠标不在列表可视区域内（例如悬停在顶部搜索/标签栏）时不显示行提示
        if (mx < this.getX() || mx > this.getRight()
            || my < this.getY() || my > this.getY() + this.getHeight()) {
            return null;
        }
        for (GuiEventListener child : this.children()) {
            if (child instanceof OptionEntry entry) {
                net.minecraft.network.chat.Component tip = entry.getTooltipAt((int) mx, (int) my);
                if (tip != null) return tip;
            }
        }
        return null;
    }

    @Override public int getRowWidth() { return this.width - 20; }
    @Override public int getRowLeft() { return this.getX() + 10; }
    public int getScrollbarX() { return this.getRight() - 6; }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (scrolling) {
            int bh = this.getHeight(), ch = this.getMaxScroll() + this.getHeight();
            if (ch > bh) {
                int sbh = Math.max(32, (int) ((float) (bh * bh) / (float) ch));
                if (sbh > bh) sbh = bh;
                double d = Math.max(1, this.getMaxScroll() / (double) (bh - sbh));
                this.setScrollAmount(this.getScrollAmount() + dy * d);
            }
            return true;
        }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public void renderWidget(GuiGraphics g, int mx, int my, float delta) {
        this.enableScissor(g);
        this.renderListItems(g, mx, my, delta);
        g.disableScissor();
        int sx = this.getScrollbarX(), sy = this.getY(), sh = this.getHeight();
        int ch = this.getMaxScroll() + this.getHeight();
        if (ch > this.getHeight()) {
            int bh = Math.max(32, (int) ((float) (this.getHeight() * this.getHeight()) / (float) ch));
            if (bh > this.getHeight()) bh = this.getHeight();
            int bt = (int) this.getScrollAmount() * (this.getHeight() - bh) / this.getMaxScroll() + this.getY();
            if (bt < this.getY()) bt = this.getY();
            g.fill(sx, sy, sx + 6, sy + sh, 0x80000000);
            g.fill(sx, bt, sx + 6, bt + bh, 0xA0FFFFFF);
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmt, double vAmt) {
        this.targetScroll = this.getScrollAmount() - vAmt * 80.0;
        this.targetScroll = Math.max(0, Math.min(this.targetScroll, this.getMaxScroll()));
        this.isSmoothScrolling = true;
        return true;
    }

    public void updateSmoothScroll(float delta) {
        if (isSmoothScrolling) {
            double cur = this.getScrollAmount();
            if (Math.abs(cur - targetScroll) < 0.5) {
                this.setScrollAmount(targetScroll);
                isSmoothScrolling = false;
            } else {
                this.setScrollAmount(Mth.lerp(0.5f * delta, cur, targetScroll));
            }
        } else {
            targetScroll = this.getScrollAmount();
        }
    }
}
