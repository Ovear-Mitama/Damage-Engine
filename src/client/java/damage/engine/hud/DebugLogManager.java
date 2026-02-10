package damage.engine.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class DebugLogManager {
    private static final DebugLogManager INSTANCE = new DebugLogManager();
    private final List<LogEntry> logs = new ArrayList<>();
    private static final long LOG_LIFETIME = 10000; 

    private DebugLogManager() {}

    public static DebugLogManager getInstance() {
        return INSTANCE;
    }

    public void addLog(String message) {
        logs.add(new LogEntry(message, System.currentTimeMillis()));
        // Keep size reasonable
        if (logs.size() > 20) {
            logs.remove(0);
        }
    }

    public void render(DrawContext context) {
        if (logs.isEmpty()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer tr = client.textRenderer;
        long now = System.currentTimeMillis();
        
        int x = 5;
        int y = 5;
        int lineHeight = 10;

        Iterator<LogEntry> it = logs.iterator();
        while (it.hasNext()) {
            LogEntry entry = it.next();
            long age = now - entry.timestamp;
            
            if (age > LOG_LIFETIME) {
                it.remove();
                continue;
            }

            int alpha = 255;
            if (age > LOG_LIFETIME - 1000) {
                alpha = (int) (255 * (LOG_LIFETIME - age) / 1000f);
            }
            if (alpha < 5) alpha = 5;

            int color = 0xFFFFFF | (alpha << 24);
            context.drawTextWithShadow(tr, Text.literal(entry.message), x, y, color);
            y += lineHeight;
        }
    }

    private static class LogEntry {
        String message;
        long timestamp;
        
        public LogEntry(String message, long timestamp) {
            this.message = message;
            this.timestamp = timestamp;
        }
    }
}
