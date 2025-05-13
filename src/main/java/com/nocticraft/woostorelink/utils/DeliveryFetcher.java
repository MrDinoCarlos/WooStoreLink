package com.nocticraft.woostorelink.utils;

import com.google.gson.Gson;
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
import java.util.stream.Collectors;

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
            String fullUrl = baseUrl + "/wp-json/minecraftstorelink/v1/pending?token=" + token + "&player=" + URLEncoder.encode(playerName, "UTF-8");
            HttpURLConnection conn = (HttpURLConnection) new URL(fullUrl).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

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

        String endpoint = baseUrl + "/wp-json/minecraftstorelink/v1/mark-delivered";
        try {
            URL url = new URL(endpoint);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

            String ids = deliveryIds.stream().map(String::valueOf).collect(Collectors.joining(","));
            String params = "token=" + URLEncoder.encode(token, "UTF-8") +
                    "&ids=" + URLEncoder.encode(ids, "UTF-8");

            try (OutputStream os = conn.getOutputStream()) {
                os.write(params.getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            if (code == 200) {
                plugin.getLogger().info("✔ Marked deliveries as delivered: " + ids);
            } else {
                plugin.getLogger().warning("❌ Failed to mark deliveries as delivered (code " + code + ")");
                printErrorStream(conn);
            }

        } catch (Exception e) {
            plugin.getLogger().severe("❌ Error marking deliveries as delivered: " + e.getMessage());
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
