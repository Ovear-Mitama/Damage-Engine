package damage.engine.client.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import damage.engine.DamageEngineConfig;
import damage.engine.DamageEngineMeta;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class HomeScreen extends Screen {
    private final Screen parent;
    private final DamageEngineConfig config;
    private int buttonsLeftX;

    private enum UpdateState { IDLE, CHECKING, LATEST, OUTDATED, ERROR }

    private UpdateState updateState = UpdateState.IDLE;
    private String updateFoundVersion = "";
    private boolean updateChecked = false;

    private static final String UPDATE_URL =
        "https://api.modrinth.com/v2/project/damage-engine/version?game_versions=%5B%221.21.11%22%5D&loaders=%5B%22" + DamageEngineMeta.PLATFORM + "%22%5D";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();

    public HomeScreen(Screen parent) {
        super(Component.translatable("title.damage-engine.home"));
        this.parent = parent;
        this.config = DamageEngineConfig.getInstance();
    }

    @Override
    protected void init() {
        this.clearWidgets();

        int centerX = this.width / 2;
        int buttonWidth = 100;
        int buttonHeight = 22;
        int buttonSpacing = 8;
        int totalButtonsWidth = buttonWidth * 2 + buttonSpacing;

        // Buttons placed lower on screen
        int buttonY = this.height / 2 + 30;

        // Left edge of the centered button row; the title above is aligned to it
        this.buttonsLeftX = centerX - totalButtonsWidth / 2;

        // Settings button
        this.addRenderableWidget(new StyledButton(
            this.buttonsLeftX, buttonY,
            buttonWidth, buttonHeight,
            Component.translatable("button.damage-engine.settings"),
            () -> this.minecraft.gui.setScreen(new DamageConfigScreen(this))
        ));

        // Config Management button
        this.addRenderableWidget(new StyledButton(
            this.buttonsLeftX + buttonWidth + buttonSpacing, buttonY,
            buttonWidth, buttonHeight,
            Component.translatable("button.damage-engine.config_management"),
            () -> this.minecraft.gui.setScreen(new ProfileManagerScreen(this))
        ));

        // Auto-check for updates once on entering the home screen
        // (init() re-runs on window resize; guard so we don't re-request every time)
        if (!updateChecked) {
            updateChecked = true;
            this.checkForUpdates();
        }
    }

    private void checkForUpdates() {
        if (updateState == UpdateState.CHECKING) return;
        updateState = UpdateState.CHECKING;

        Thread thread = new Thread(() -> {
            String latest = null;
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(UPDATE_URL))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "damage-engine/" + DamageEngineMeta.VERSION)
                    .GET()
                    .build();
                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    JsonArray array = JsonParser.parseString(response.body()).getAsJsonArray();
                    if (!array.isEmpty()) {
                        // Only consider versions for the current loader platform (Fabric/NeoForge
                        // releases share the same project on Modrinth). The URL already filters by
                        // loaders; this is a defensive second check.
                        for (int i = 0; i < array.size(); i++) {
                            JsonObject entry = array.get(i).getAsJsonObject();
                            if (!hasLoader(entry, DamageEngineMeta.PLATFORM)) continue;
                            if (entry.has("version_number") && !entry.get("version_number").isJsonNull()) {
                                String v = entry.get("version_number").getAsString();
                                if (latest == null || compareVersions(v, latest) > 0) {
                                    latest = v;
                                }
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
                // network / parse failure -> error state
            }

            String found = latest;
            Minecraft.getInstance().execute(() -> {
                if (found == null || found.isEmpty()) {
                    updateState = UpdateState.ERROR;
                } else if (compareVersions(found, DamageEngineMeta.VERSION) > 0) {
                    updateFoundVersion = found;
                    updateState = UpdateState.OUTDATED;
                } else {
                    updateState = UpdateState.LATEST;
                }
            });
        });
        thread.setDaemon(true);
        thread.start();
    }

    private static boolean hasLoader(JsonObject entry, String platform) {
        if (!entry.has("loaders") || entry.get("loaders").isJsonNull()) {
            return true; // No loader info: accept defensively
        }
        JsonArray loaders = entry.get("loaders").getAsJsonArray();
        for (int i = 0; i < loaders.size(); i++) {
            var el = loaders.get(i);
            if (el.isJsonPrimitive() && platform.equals(el.getAsString())) {
                return true;
            }
        }
        return false;
    }

    private static int compareVersions(String a, String b) {
        // Strip pre-release/build metadata suffixes (e.g. "1.4.3-fabric", "1.4.3+build5")
        // so pure version numbers compare numerically
        String[] pa = stripSuffix(a).split("\\.");
        String[] pb = stripSuffix(b).split("\\.");
        int n = Math.max(pa.length, pb.length);
        for (int i = 0; i < n; i++) {
            String sa = i < pa.length ? pa[i] : "";
            String sb = i < pb.length ? pb[i] : "";
            // Missing segments count as 0, so "1.4.3" == "1.4.3.0" and "1.4" == "1.4.0"
            Integer na = sa.isEmpty() ? 0 : parseNumOrNull(sa);
            Integer nb = sb.isEmpty() ? 0 : parseNumOrNull(sb);
            if (na != null && nb != null) {
                if (!na.equals(nb)) return Integer.compare(na, nb);
            } else {
                int cmp = sa.compareTo(sb);
                if (cmp != 0) return cmp;
            }
        }
        return 0;
    }

    private static String stripSuffix(String s) {
        int idx = s.indexOf('-');
        if (idx < 0) idx = s.indexOf('+');
        return idx < 0 ? s : s.substring(0, idx);
    }

    private static Integer parseNumOrNull(String s) {
        if (s.isEmpty()) return null;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float delta) {
        // 1.21.11: in-game background blur is applied once by the render pipeline;
        // calling renderBackground() here would blur twice and crash.
        // On the main menu (no world) draw the panorama background without blur.
        if (this.minecraft != null && this.minecraft.level == null) {
            this.extractPanorama(guiGraphics, delta);
        }

        int centerX = this.width / 2;
        int titleY = this.height / 4;
        // Title left-aligned to the left edge of the centered button row below
        int titleLeftX = this.buttonsLeftX;

        // Bigger "Damage Engine" title with scale + scan overlay on top
        guiGraphics.pose().pushMatrix();
        float titleScale = 2.0f;
        guiGraphics.pose().scale(titleScale, titleScale);
        String titleText = Component.translatable("title.damage-engine.home").getString();
        int scaledX = (int)(titleLeftX / titleScale);
        int scaledY = (int)(titleY / titleScale);
        guiGraphics.text(this.font, titleText, scaledX, scaledY, 0xFFFFFFFF);
        guiGraphics.pose().popMatrix();

        // "Developed by mitama" - left aligned below title
        String devText = Component.translatable("text.damage-engine.developed_by").getString();
        int greenColor = 0xFFB5F0C6;
        guiGraphics.text(this.font, devText, titleLeftX, titleY + (int)(this.font.lineHeight * titleScale) + 6, greenColor);

        // Update status text at the bottom of the screen
        if (updateState != UpdateState.IDLE) {
            String statusText = "";
            int statusColor = 0xFFA0A0A0; // grey
            switch (updateState) {
                case CHECKING -> {
                    statusText = Component.translatable("text.damage-engine.checking_update").getString();
                    statusColor = 0xFFA0A0A0; // grey
                }
                case LATEST -> {
                    statusText = Component.translatable("text.damage-engine.update_latest", DamageEngineMeta.VERSION).getString();
                    statusColor = greenColor; // green
                }
                case OUTDATED -> {
                    statusText = Component.translatable("text.damage-engine.update_available", updateFoundVersion, DamageEngineMeta.VERSION).getString();
                    statusColor = 0xFFFFB74D; // orange
                }
                case ERROR -> {
                    statusText = Component.translatable("text.damage-engine.update_check_failed").getString();
                    statusColor = 0xFFFF7E7E; // red
                }
            }
            guiGraphics.centeredText(this.font, statusText, centerX, this.height - 30, statusColor);
        }

        // Render widgets + tooltips manually (avoid super.render() blur)
        for (var child : this.children()) {
            if (child instanceof AbstractWidget w) {
                w.extractRenderState(guiGraphics, mouseX, mouseY, delta);
                // Tooltip is rendered automatically by the widget system in 1.21.11
            }
        }
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }

    private static class StyledButton extends AbstractWidget {
        private final Runnable onPress;

        public StyledButton(int x, int y, int width, int height, Component message, Runnable onPress) {
            super(x, y, width, height, message);
            this.onPress = onPress;
        }

        @Override
        public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean bl) {
            if (this.active && this.visible && event.button() == 0) {
                if (this.isMouseOver(event.x(), event.y())) {
                    this.playDownSound(Minecraft.getInstance().getSoundManager());
                    this.onPress.run();
                    return true;
                }
            }
            return false;
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float delta) {
            if (this.active && isHovered()) guiGraphics.requestCursor(DamageConfigScreen.CURSOR_HAND);
            int bgColor = this.active ? 0x20000000 : 0x10000000;
            guiGraphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bgColor);

            int borderColor;
            if (!this.active) {
                borderColor = 0xFF404040;
            } else if (isHovered()) {
                borderColor = 0xFFFFFFFF;
            } else {
                borderColor = 0xFFA0A0A0;
            }
            int x = getX(); int y = getY(); int w = getWidth(); int h = getHeight();
            guiGraphics.fill(x, y, x + w, y + 1, borderColor);
            guiGraphics.fill(x, y + h - 1, x + w, y + h, borderColor);
            guiGraphics.fill(x, y, x + 1, y + h, borderColor);
            guiGraphics.fill(x + w - 1, y, x + w, y + h, borderColor);

            int textColor = this.active ? 0xFFFFFFFF : 0xFF808080;
            guiGraphics.centeredText(Minecraft.getInstance().font, getMessage(), getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, textColor);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput builder) {
            this.defaultButtonNarrationText(builder);
        }
    }
}
