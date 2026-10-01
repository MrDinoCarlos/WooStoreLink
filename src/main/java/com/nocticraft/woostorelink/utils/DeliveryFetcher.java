package com.nocticraft.woostorelink.utils;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/** Performs bounded REST calls. Call this class only from WooStoreLink's network worker. */
public final class DeliveryFetcher {

    private static final Type DELIVERY_LIST = new TypeToken<List<Delivery>>() { }.getType();
    private static final long ERROR_LOG_COOLDOWN_MS = 30_000L;

    private final JavaPlugin plugin;
    private final Gson gson = new Gson();
    private final String baseUrl;
    private final String token;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    private final int maxDeliveriesPerPlayer;
    private final AtomicLong lastErrorLog = new AtomicLong(0L);

    public DeliveryFetcher(JavaPlugin plugin) {
        this.plugin = plugin;
        FileConfiguration config = plugin.getConfig();
        this.baseUrl = config.getString("api-domain", "").trim().replaceAll("/+$", "");
        this.token = config.getString("api-token", "").trim();
        this.connectTimeoutMs = secondsToMillis(config.getInt("network.connect-timeout-seconds", 5), 5);
        this.readTimeoutMs = secondsToMillis(config.getInt("network.read-timeout-seconds", 10), 10);
        this.maxDeliveriesPerPlayer = Math.max(1,
                Math.min(200, config.getInt("network.max-deliveries-per-player", 50)));
    }

    public boolean isConfigured() {
        return (baseUrl.startsWith("https://") || baseUrl.startsWith("http://"))
                && !token.isBlank()
                && !"REPLACE_WITH_YOUR_API_TOKEN".equals(token);
    }

    /** Fetches all requested players with one API request. */
    public Map<String, List<Delivery>> fetchDeliveries(Collection<String> playerNames) {
        LinkedHashMap<String, List<Delivery>> result = emptyResult(playerNames);
        if (!isConfigured() || result.isEmpty()) {
            return result;
        }

        HttpURLConnection connection = null;
        try {
            connection = open("/wp-json/storelinkformc/v1/pending-batch", "POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

            JsonObject payload = new JsonObject();
            payload.add("players", gson.toJsonTree(result.keySet()));
            payload.addProperty("limit", maxDeliveriesPerPlayer);
            writeBody(connection, gson.toJson(payload));

            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                logHttpError("fetch deliveries", status, readBody(connection.getErrorStream()));
                return result;
            }

            JsonObject response = gson.fromJson(readBody(connection.getInputStream()), JsonObject.class);
            if (response == null || !response.has("deliveries") || !response.get("deliveries").isJsonObject()) {
                logError("The delivery API returned an invalid response.");
                return result;
            }

            JsonObject grouped = response.getAsJsonObject("deliveries");
            for (Map.Entry<String, JsonElement> entry : grouped.entrySet()) {
                if (!result.containsKey(entry.getKey()) || !entry.getValue().isJsonArray()) {
                    continue;
                }
                List<Delivery> deliveries = gson.fromJson(entry.getValue(), DELIVERY_LIST);
                result.put(entry.getKey(), deliveries == null ? List.of() : deliveries);
            }
        } catch (Exception exception) {
            logError("Could not fetch deliveries: " + safeMessage(exception));
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return result;
    }

    /** Confirms several deliveries with one HTTP request. */
    public boolean markAsDelivered(Collection<Integer> deliveryIds) {
        if (!isConfigured() || deliveryIds == null || deliveryIds.isEmpty()) {
            return deliveryIds == null || deliveryIds.isEmpty();
        }

        List<Integer> ids = deliveryIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .limit(200)
                .collect(Collectors.toList());
        if (ids.isEmpty()) {
            return true;
        }

        HttpURLConnection connection = null;
        try {
            connection = open("/wp-json/storelinkformc/v1/mark-delivered", "POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            JsonObject payload = new JsonObject();
            payload.add("ids", gson.toJsonTree(ids));
            writeBody(connection, gson.toJson(payload));

            int status = connection.getResponseCode();
            if (status >= 200 && status < 300) {
                return true;
            }
            logHttpError("confirm deliveries", status, readBody(connection.getErrorStream()));
        } catch (Exception exception) {
            logError("Could not confirm deliveries: " + safeMessage(exception));
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return false;
    }

    private HttpURLConnection open(String path, String method) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + path).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(connectTimeoutMs);
        connection.setReadTimeout(readTimeoutMs);
        connection.setUseCaches(false);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Authorization", "Bearer " + token);
        connection.setRequestProperty("X-StoreLink-Token", token);
        connection.setRequestProperty("User-Agent", "WooStoreLink/2.0.2 (Minecraft 1.20-26.3)");
        return connection;
    }

    private void writeBody(HttpURLConnection connection, String body) throws Exception {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream stream = connection.getOutputStream()) {
            stream.write(bytes);
        }
    }

    private String readBody(InputStream stream) throws Exception {
        if (stream == null) {
            return "";
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            return reader.lines().limit(256).collect(Collectors.joining("\n"));
        }
    }

    private LinkedHashMap<String, List<Delivery>> emptyResult(Collection<String> names) {
        LinkedHashMap<String, List<Delivery>> result = new LinkedHashMap<>();
        if (names == null) {
            return result;
        }
        for (String name : names) {
            if (name != null && name.matches("[A-Za-z0-9_]{3,16}")) {
                result.putIfAbsent(name, Collections.emptyList());
            }
        }
        return result;
    }

    private void logHttpError(String action, int status, String body) {
        String detail = body == null || body.isBlank() ? "" : " - " + body.replaceAll("[\\r\\n]+", " ");
        logError("Failed to " + action + " (HTTP " + status + ")" + detail);
    }

    private void logError(String message) {
        long now = System.currentTimeMillis();
        long previous = lastErrorLog.get();
        if (now - previous >= ERROR_LOG_COOLDOWN_MS && lastErrorLog.compareAndSet(previous, now)) {
            plugin.getLogger().warning("[REST] " + message);
        }
    }

    private static int secondsToMillis(int configured, int fallback) {
        int seconds = configured > 0 ? Math.min(configured, 60) : fallback;
        return seconds * 1000;
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}
