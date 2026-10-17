package damage.engine.client.gui.config;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * 配置界面的自定义提示框：深色半透明底、无边框，跟随鼠标并在贴边时翻到另一侧。
 * 文字按像素宽度自动换行。
 */
public final class ConfigTooltips {
    private static final int PAD = 3;
    private static final int MAX_WIDTH = 200;
    private static final int BACKGROUND = 0xF0101014;
    private static final int TEXT_COLOR = 0xFFE6E6E6;

    private ConfigTooltips() {}

    public static void draw(GuiGraphicsExtractor g, Font font, Component text,
                            int mouseX, int mouseY, int screenWidth, int screenHeight) {
        List<String> lines = wrapText(font, text.getString(), MAX_WIDTH);
        int boxWidth = 0;
        for (String line : lines) boxWidth = Math.max(boxWidth, font.width(line));
        boxWidth += PAD * 2;
        int boxHeight = lines.size() * font.lineHeight + PAD * 2;

        int x = mouseX + 10;
        int y = mouseY + 12;
        if (x + boxWidth > screenWidth) x = mouseX - 10 - boxWidth;
        if (y + boxHeight > screenHeight) y = mouseY - 12 - boxHeight;
        x = Mth.clamp(x, 0, Math.max(0, screenWidth - boxWidth));
        y = Mth.clamp(y, 0, Math.max(0, screenHeight - boxHeight));

        g.fill(x, y, x + boxWidth, y + boxHeight, BACKGROUND);
        for (int i = 0; i < lines.size(); i++) {
            g.text(font, lines.get(i), x + PAD, y + PAD + i * font.lineHeight, TEXT_COLOR, false);
        }
    }

    /** 按像素宽度折行：优先在最后一个空格处断开，中文长句则按字符断开。 */
    private static List<String> wrapText(Font font, String text, int maxWidth) {
        List<String> result = new ArrayList<>();
        for (String paragraph : text.split("\n", -1)) {
            List<String> lines = new ArrayList<>();
            StringBuilder line = new StringBuilder();
            int lastSpace = -1;
            for (int index = 0; index < paragraph.length(); index++) {
                char c = paragraph.charAt(index);
                if (font.width(line.toString() + c) > maxWidth && line.length() > 0) {
                    if (lastSpace > 0) {
                        String rest = line.substring(lastSpace + 1);
                        lines.add(line.substring(0, lastSpace));
                        line.setLength(0);
                        line.append(rest);
                    } else {
                        lines.add(line.toString());
                        line.setLength(0);
                    }
                    lastSpace = -1;
                }
                if (c == ' ') lastSpace = line.length();
                line.append(c);
            }
            if (line.length() > 0) lines.add(line.toString());
            if (lines.isEmpty()) lines.add("");
            result.addAll(lines);
        }
        return result.isEmpty() ? List.of("") : result;
    }
}
