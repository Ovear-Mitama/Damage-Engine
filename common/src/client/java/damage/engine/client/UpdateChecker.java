package damage.engine.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import damage.engine.DamageEngineConfig;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class UpdateChecker {
    private static final String MODRINTH_PROJECT_SLUG = "damage-engine";
    private static final String MODRINTH_API_URL = "https://api.modrinth.com/v2/project/" + MODRINTH_PROJECT_SLUG + "/version";
    private static final String CURRENT_VERSION = damage.engine.DamageEngineMeta.VERSION;
    // Minecraft version this mod is built for
    private static final String MC_VERSION = "1.20.1";

    private static String latestVersion = null;
    private static boolean updateAvailable = false;
    private static boolean checked = false;
    private static String errorMessage = null;

    public static void checkAsync() {
        if (!DamageEngineConfig.getInstance().checkUpdate) return;
        if (checked) return;

        new Thread(() -> {
            try {
                // Only consider versions for the current loader platform (Fabric/Forge
                // releases share the same project on Modrinth). The URL already filters by
                // loaders; this is a defensive second check.
                String url = MODRINTH_API_URL + "?game_versions=[%22" + URLEncoder.encode(MC_VERSION, StandardCharsets.UTF_8) + "%22]&loaders=[%22" + damage.engine.DamageEngineMeta.PLATFORM + "%22]";
                HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Damage-Engine/" + CURRENT_VERSION + " (Minecraft Mod)")
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonArray versions = JsonParser.parseString(response.body()).getAsJsonArray();
                    if (versions.size() > 0) {
                        // Find the highest version number that matches BOTH this MC
                        // version and the current loader platform (Fabric/Forge share
                        // the same Modrinth project). Traverse all returned versions
                        // instead of stopping at the first (date) match.
                        String best = null;
                        for (int i = 0; i < versions.size(); i++) {
                            JsonObject ver = versions.get(i).getAsJsonObject();
                            if (!hasLoader(ver, damage.engine.DamageEngineMeta.PLATFORM)) continue;
                            String versionNumber = ver.get("version_number").getAsString();
                            JsonArray gameVersions = ver.getAsJsonArray("game_versions");
                            boolean matchesMc = false;
                            for (int j = 0; j < gameVersions.size(); j++) {
                                if (MC_VERSION.equals(gameVersions.get(j).getAsString())) {
                                    matchesMc = true;
                                    break;
                                }
                            }
                            if (matchesMc && (best == null || compareVersions(versionNumber, best) > 0)) {
                                best = versionNumber;
                            }
                        }
                        if (best != null) {
                            latestVersion = best;
                            // Compare versions: latestVersion > CURRENT_VERSION means update available
                            updateAvailable = compareVersions(latestVersion, CURRENT_VERSION) > 0;
                        }
                    }
                }
            } catch (Exception e) {
                errorMessage = e.getMessage();
            }
            checked = true;
        }, "DamageEngine-UpdateCheck").start();
    }

    private static boolean hasLoader(JsonObject entry, String platform) {
        // Strict: require explicit loader info - a release that does not declare
        // its loaders must not be offered to any platform (Fabric/Forge versions
        // share one Modrinth project, so a missing/unknown loader could leak a
        // cross-platform update suggestion).
        if (!entry.has("loaders") || entry.get("loaders").isJsonNull()) {
            return false;
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

    /**
     * Compare two version strings. Returns positive if v1 > v2, negative if v1 < v2, 0 if equal.
     */
    private static int compareVersions(String v1, String v2) {
        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");
        int len = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < len; i++) {
            int n1 = i < parts1.length ? parseIntSafe(parts1[i]) : 0;
            int n2 = i < parts2.length ? parseIntSafe(parts2[i]) : 0;
            if (n1 != n2) return n1 - n2;
        }
        return 0;
    }

    private static int parseIntSafe(String s) {
        try {
            // Strip non-numeric suffix (e.g. "3a", "3-beta")
            StringBuilder sb = new StringBuilder();
            for (char c : s.toCharArray()) {
                if (c >= '0' && c <= '9') sb.append(c);
                else break;
            }
            return sb.length() > 0 ? Integer.parseInt(sb.toString()) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public static String getLatestVersion() {
        return latestVersion;
    }

    public static String getCurrentVersion() {
        return CURRENT_VERSION;
    }

    public static boolean hasChecked() {
        return checked;
    }

    public static String getErrorMessage() {
        return errorMessage;
    }
}