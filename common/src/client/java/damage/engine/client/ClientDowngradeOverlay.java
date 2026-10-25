package damage.engine.client;

import damage.engine.DamageEngineConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * 客户端降级警告：进入世界后检测到服务端未安装本模组时，在左上角显示 10 秒红色提示，
 * 说明客户端模式下的功能限制。带淡入淡出动画，可通过配置开关关闭。
 */
public final class ClientDowngradeOverlay {
    private static final long TOTAL_NS = 10_000_000_000L;
    private static final long FADE_IN_NS = 500_000_000L;
    private static final long FADE_OUT_NS = 800_000_000L;
    /** 红色：与配置界面里「关闭」状态使用的颜色一致（0xFC887E）。 */
    private static final int TEXT_RGB = 0xFC887E;
    /** 不透明深色底。 */
    private static final int BG_RGB = 0x101010;
    private static final int PADDING = 3;
    /** 文本低于该透明度（5/255）时直接停画：极低 alpha 下字体渲染会出残影/闪烁。 */
    private static final int MIN_TEXT_ALPHA = 5;

    /** 用单调时钟计时，避免系统时间被校准时 elapsed 倒退导致重新变亮（闪一下）。 */
    private static long startNanos = -1L;

    private ClientDowngradeOverlay() {
    }

    /** 检测到服务端没有本模组时开始显示。 */
    public static void trigger() {
        startNanos = System.nanoTime();
    }

    /** 离开世界 / 确认服务端有模组时清掉，避免残留。 */
    public static void reset() {
        startNanos = -1L;
    }

    /** 由客户端 tick 收尾：过期或配置关闭时清掉状态（render 只读，不修改计时）。 */
    public static void tick() {
        if (startNanos < 0L) return;
        if (!DamageEngineConfig.getInstance().showClientDowngradeWarning
            || System.nanoTime() - startNanos >= TOTAL_NS) {
            reset();
        }
    }

    public static void render(GuiGraphicsExtractor g) {
        if (startNanos < 0L) return;
        DamageEngineConfig config = DamageEngineConfig.getInstance();
        if (!config.showClientDowngradeWarning) return;
        long elapsed = System.nanoTime() - startNanos;
        if (elapsed >= TOTAL_NS) return;
        float alpha;
        if (elapsed < FADE_IN_NS) {
            alpha = smoothstep(elapsed / (float) FADE_IN_NS);
        } else if (elapsed > TOTAL_NS - FADE_OUT_NS) {
            alpha = smoothstep((TOTAL_NS - elapsed) / (float) FADE_OUT_NS);
        } else {
            alpha = 1f;
        }
        alpha = Math.clamp(alpha, 0f, 1f);
        int a = (int) (alpha * 255f);
        if (a <= 0) return;

        Minecraft client = Minecraft.getInstance();
        Font font = client.font;
        Component title = Component.translatable("text.damage-engine.client_downgrade_title");
        String[] lines = Component.translatable("text.damage-engine.client_downgrade_lines")
            .getString().split("\n");

        int x = 4;
        int y = 4;
        if (config.debugShowPerformance) {
            // 调试性能叠加层同样固定在左上角，警告往下让位避免互相遮挡
            y += font.lineHeight * 4 + 10;
        }
        int lineHeight = font.lineHeight + 1;
        int width = font.width(title);
        for (String line : lines) {
            width = Math.max(width, font.width(line));
        }
        int height = lineHeight * (lines.length + 1);

        // 背景正常淡出到完全透明，不做阈值截断（避免深色底突然消失的跳变）
        g.fill(x - PADDING, y - PADDING, x + width + PADDING, y + height + PADDING,
            (a << 24) | BG_RGB);
        // 文本在极低透明度下停画：与伤害数字淡出修复一致，避免字体残影造成的闪烁
        if (a >= MIN_TEXT_ALPHA) {
            int textColor = (a << 24) | TEXT_RGB;
            g.text(font, title, x, y, textColor);
            int ty = y + lineHeight;
            for (String line : lines) {
                g.text(font, line, x, ty, textColor);
                ty += lineHeight;
            }
        }
    }

    /** 平滑过渡曲线：两端变化更缓，避免开始/结束处出现突兀的亮度跳变。 */
    private static float smoothstep(float t) {
        t = Math.clamp(t, 0f, 1f);
        return t * t * (3f - 2f * t);
    }
}
