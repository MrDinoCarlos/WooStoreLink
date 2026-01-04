package com.nocticraft.woostorelink.utils;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Handles REST calls to fetch and confirm deliveries.
 * This class has no dependency on the main plugin type beyond JavaPlugin.
 */
public class DeliveryFetcher {

    private final JavaPlugin plugin;
    private final Gson gson = new Gson();
    private final String baseUrl;
    private final String token;

    public DeliveryFetcher(JavaPlugin plugin) {
        this.plugin = plugin;
        FileConfiguration config = plugin.getConfig();
        String domain = config.getString("api-domain", "").replaceAll("/+$", "");
        this.baseUrl = domain;
        this.token = config.getString("api-token", "");
    }

    /** Fetches pending deliveries for a given player name. */
    public List<Delivery> fetchDeliveries(String playerName) {
        try {
            String fullUrl = this.baseUrl + "/wp-json/storelinkformc/v1/pending?token=" +
                    URLEncoder.encode(this.token, "UTF-8") +
                    "&player=" + URLEncoder.encode(playerName, "UTF-8");

            HttpURLConnection conn = (HttpURLConnection) (new URL(fullUrl)).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            int code = conn.getResponseCode();
            if (code == 200) {
                try (InputStreamReader reader = new InputStreamReader(conn.getInputStream())) {

                    Type listType = new TypeToken<List<Delivery>>() {}.getType();
                    JsonElement root = this.gson.fromJson(reader, JsonElement.class);

                    // Compatibilidad: si el backend devolviese directamente un array
                    if (root != null && root.isJsonArray()) {
                        return this.gson.fromJson(root, listType);
                    }

                    // Formato actual: { success: true, deliveries: [...] }
                    if (root != null && root.isJsonObject()) {
                        JsonObject json = root.getAsJsonObject();

                        // Si existe success y es false, no proceses deliveries
                        if (json.has("success") && !json.get("success").getAsBoolean()) {
                            String msg = json.has("message") ? json.get("message").getAsString() : "Unknown error";
                            this.plugin.getLogger().warning("[REST] API success=false: " + msg);
                            return List.of();
                        }

                        JsonElement deliveriesElement = json.get("deliveries");
                        if (deliveriesElement != null && deliveriesElement.isJsonArray()) {
                            return this.gson.fromJson(deliveriesElement, listType);
                        }

                        return List.of();
                    }

                    return List.of();
                }
            }

            this.plugin.getLogger().warning("[REST] HTTP " + code);
            this.printErrorStream(conn);

        } catch (Exception e) {
            this.plugin.getLogger().severe("[REST] Error fetching deliveries: " + e.getMessage());
        }

        return List.of();
    }

    /** Marks each delivery id as delivered on the backend. */
    public void markAsDelivered(List<Integer> deliveryIds) {
        if (deliveryIds.isEmpty()) return;

        for (int deliveryId : deliveryIds) {
            try {
                String endpoint = baseUrl + "/wp-json/storelinkformc/v1/mark-delivered";
                HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

                // timeouts para evitar bloqueos largos
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                String params = "token=" + URLEncoder.encode(token, "UTF-8") +
                        "&id=" + URLEncoder.encode(String.valueOf(deliveryId), "UTF-8");

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(params.getBytes(StandardCharsets.UTF_8));
                }

                int code = conn.getResponseCode();
                if (code == 200) {
                    plugin.getLogger().info("[REST] Marked delivery " + deliveryId + " as delivered.");
                } else {
                    plugin.getLogger().warning("[REST] Failed to mark delivery " + deliveryId + " (HTTP " + code + ")");
                    printErrorStream(conn);
                }

            } catch (Exception e) {
                plugin.getLogger().severe("[REST] Error marking delivery " + deliveryId + ": " + e.getMessage());
            }
        }
    }

    private void printErrorStream(HttpURLConnection conn) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getErrorStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                plugin.getLogger().warning("[REST Error] " + line);
            }
        } catch (Exception ignored) {}
    }
}
