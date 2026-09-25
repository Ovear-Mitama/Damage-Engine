package damage.engine.client.gui;

import net.minecraft.client.gui.components.EditBox;

/**
 * EditBox 位置/尺寸辅助(1.18 与 1.20.1 一样,{@code AbstractWidget} 没有公开的
 * setX/setY/setWidth,故这里保持空实现:位置由构造时决定,视觉位置由父级自绘)。
 */
public class WidgetHelper {
    public static void position(EditBox box, int x, int y, int width, int height) {
        // 1.18 同样无法在构造后移动 EditBox;保持与 1.20.1 分支一致的空实现。
    }
}
