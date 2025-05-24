package com.nocticraft.woostorelink.utils;

import com.google.gson.Gson;
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
import com.google.gson.JsonObject;


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

    public List<Delivery> fetchDeliveries(String playerName) {
        try {
            String fullUrl = baseUrl + "/wp-json/storelinkformc/v1/pending?token=" + URLEncoder.encode(token, "UTF-8") +
                    "&player=" + URLEncoder.encode(playerName, "UTF-8");

            HttpURLConnection conn = (HttpURLConnection) new URL(fullUrl).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            // No Authorization header, token is in URL


            int code = conn.getResponseCode();
            if (code == 200) {
                InputStreamReader reader = new InputStreamReader(conn.getInputStream());
                JsonObject json = gson.fromJson(reader, JsonObject.class);
                Type listType = new TypeToken<List<Delivery>>() {}.getType();
                return gson.fromJson(json.get("deliveries"), listType);

            } else {
                plugin.getLogger().warning("❌ Failed to fetch deliveries. HTTP Code: " + code);
                printErrorStream(conn);
            }

        } catch (Exception e) {
            plugin.getLogger().severe("❌ Error fetching deliveries: " + e.getMessage());
        }
        return List.of();
    }

    public void markAsDelivered(List<Integer> deliveryIds) {
        if (deliveryIds.isEmpty()) return;

        for (int deliveryId : deliveryIds) {
            try {
                String endpoint = baseUrl + "/wp-json/storelinkformc/v1/mark-delivered";
                HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                // No Authorization header
                String params = "token=" + URLEncoder.encode(token, "UTF-8") +
                        "&id=" + URLEncoder.encode(String.valueOf(deliveryId), "UTF-8");


                try (OutputStream os = conn.getOutputStream()) {
                    os.write(params.getBytes(StandardCharsets.UTF_8));
                }

                int code = conn.getResponseCode();
                if (code == 200) {
                    plugin.getLogger().info("✔ Marked delivery " + deliveryId + " as delivered.");
                } else {
                    plugin.getLogger().warning("❌ Failed to mark delivery " + deliveryId + " (code " + code + ")");
                    printErrorStream(conn);
                }

            } catch (Exception e) {
                plugin.getLogger().severe("❌ Error marking delivery " + deliveryId + ": " + e.getMessage());
            }
        }
    }

    private void printErrorStream(HttpURLConnection conn) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getErrorStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                plugin.getLogger().warning("[API Error] " + line);
            }
        } catch (Exception ignored) {
        }
    }
}
