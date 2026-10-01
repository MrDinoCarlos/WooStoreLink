package com.nocticraft.woostorelink.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public final class UpdateChecker {

    public static final String DOWNLOAD_URL = "https://modrinth.com/plugin/woostorelink/versions";
    private static final String API_URL =
            "https://api.modrinth.com/v2/project/woostorelink/version?include_changelog=false";

    private final JavaPlugin plugin;

    public UpdateChecker(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Performs one bounded Modrinth request and returns a newer stable version, if present. */
    public Optional<String> findUpdate() {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(API_URL).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5_000);
            connection.setReadTimeout(8_000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "MrDino-WooStoreLink/"
                    + plugin.getDescription().getVersion() + " (+https://mrdino.es)");

            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return Optional.empty();
            }

            JsonArray versions;
            try (InputStreamReader reader = new InputStreamReader(
                    connection.getInputStream(), StandardCharsets.UTF_8)) {
                versions = JsonParser.parseReader(reader).getAsJsonArray();
            }

            String current = plugin.getDescription().getVersion();
            String newest = current;
            for (JsonElement element : versions) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject version = element.getAsJsonObject();
                String type = string(version, "version_type");
                String status = string(version, "status");
                String number = string(version, "version_number");
                if (!"release".equals(type) || "draft".equals(status) || number.isBlank()) {
                    continue;
                }
                if (compare(number, newest) > 0) {
                    newest = number;
                }
            }
            return compare(newest, current) > 0 ? Optional.of(newest) : Optional.empty();
        } catch (Exception exception) {
            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().warning("[Update] Could not query Modrinth: " + exception.getMessage());
            }
            return Optional.empty();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    static int compare(String left, String right) {
        String[] a = numericCore(left).split("\\.");
        String[] b = numericCore(right).split("\\.");
        int length = Math.max(a.length, b.length);
        for (int i = 0; i < length; i++) {
            int av = i < a.length ? parse(a[i]) : 0;
            int bv = i < b.length ? parse(b[i]) : 0;
            if (av != bv) {
                return Integer.compare(av, bv);
            }
        }
        return 0;
    }

    private static String numericCore(String version) {
        String cleaned = version == null ? "0" : version.trim().replaceFirst("^[vV]", "");
        int suffix = cleaned.indexOf('-');
        return (suffix >= 0 ? cleaned.substring(0, suffix) : cleaned).replaceAll("[^0-9.]", "");
    }

    private static int parse(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }
}
