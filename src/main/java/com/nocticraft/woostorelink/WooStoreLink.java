package com.nocticraft.woostorelink;

import com.nocticraft.woostorelink.commands.DeliveriesCommand;
import com.nocticraft.woostorelink.utils.LanguageLoader;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class WooStoreLink extends JavaPlugin implements Listener {

    private Connection connection;
    private LanguageLoader lang;
    private String currentLangCode = "en"; // default


    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadLanguage();
        cleanOldLogs();
        connectToDatabase();

        getLogger().info("🌐 Loaded language: " + currentLangCode + " | Example: " + lang.get("plugin-enabled"));

        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("deliveries").setExecutor(new DeliveriesCommand(this));

        logDelivery(lang.getOrDefault("plugin-enabled", "WooStoreLink successfully enabled."));

        int minutes = getConfig().getInt("check-interval-minutes", 1);
        long ticks = minutes * 60L * 20L;

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            logDelivery("⏰ " + lang.getOrDefault("auto-check", "Checking pending deliveries for online players..."));
            for (Player player : Bukkit.getOnlinePlayers()) {
                processPendingDeliveries(player);
            }
        }, 20L, ticks);
    }

    private void loadLanguage() {
        currentLangCode = getConfig().getString("language", "en");
        lang = new LanguageLoader(this);
        lang.load(currentLangCode);
    }

    @Override
    public void onDisable() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException e) {
            logDelivery("❌ " + lang.getOrDefault("error-closing-connection", "Error closing connection:") + " " + e.getMessage());
        }
    }

    public void connectToDatabase() {
        FileConfiguration config = getConfig();
        String host = config.getString("mysql.host");
        int port = config.getInt("mysql.port");
        String db = config.getString("mysql.database");
        String user = config.getString("mysql.user");
        String pass = config.getString("mysql.password");

        String url = "jdbc:mysql://" + host + ":" + port + "/" + db + "?autoReconnect=true&useSSL=false&serverTimezone=UTC";

        try {
            connection = DriverManager.getConnection(url, user, pass);
            logDelivery("✅ " + lang.getOrDefault("db-connected", "MySQL connection established."));
        } catch (SQLException e) {
            connection = null;
            logDelivery("❌ " + lang.getOrDefault("db-error", "MySQL connection error:") + " " + e.getMessage());
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        processPendingDeliveries(event.getPlayer());
    }

    public void processPendingDeliveries(Player player) {
        try {
            if (connection == null || connection.isClosed()) {
                logDelivery("⚠ " + lang.getOrDefault("reconnecting-db", "Reestablishing MySQL connection..."));
                connectToDatabase();
            }
        } catch (SQLException e) {
            logDelivery("❌ " + lang.getOrDefault("connection-check-error", "Connection check error:") + " " + e.getMessage());
            connectToDatabase();
        }

        if (connection == null) {
            logDelivery("❌ " + lang.getOrDefault("delivery-failed", "Could not deliver to") + " " + player.getName());
            player.sendMessage("§c" + lang.getOrDefault("player-db-error", "Failed to check your deliveries due to a database error."));
            return;
        }

        try {
            PreparedStatement stmt = connection.prepareStatement(
                    "SELECT id, item, amount FROM pending_deliveries WHERE player = ? AND delivered = 0");
            stmt.setString(1, player.getName());
            ResultSet rs = stmt.executeQuery();

            boolean deliveredSomething = false;

            ConfigurationSection productSection = getConfig().getConfigurationSection("products");
            if (productSection == null) {
                logDelivery("⚠ " + lang.getOrDefault("products-section-missing", "Section 'products' not found in config.yml."));
                if (player.isOp()) {
                    player.sendMessage("§c" + lang.getOrDefault("products-section-missing", "Section 'products' not found in config.yml."));
                }
                return;
            }

            while (rs.next()) {
                int id = rs.getInt("id");
                String productName = rs.getString("item").toLowerCase();
                int amount = rs.getInt("amount");
                String item = null;

                for (String key : productSection.getKeys(false)) {
                    if (key.equalsIgnoreCase(productName)) {
                        item = productSection.getString(key);
                        break;
                    }
                }

                if (item == null || item.isEmpty()) {
                    logDelivery("❌ " + lang.getOrDefault("product-not-configured", "Product not configured:") + " " + productName);
                    if (player.isOp()) {
                        player.sendMessage("§c" + lang.getOrDefault("product-not-configured-player", "Product") + " §e" + productName + "§c " + lang.getOrDefault("product-not-configured-player-2", "is not configured on this server."));
                    }
                    continue;
                }

                String type = productSection.getConfigurationSection(productName).getString("type", "item");
                String value = productSection.getConfigurationSection(productName).getString("value");

                if (type.equalsIgnoreCase("item")) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "give " + player.getName() + " " + value + " " + amount);
                } else if (type.equalsIgnoreCase("command")) {
                    String command = value.replace("{player}", player.getName());
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                }

                PreparedStatement update = connection.prepareStatement("UPDATE pending_deliveries SET delivered = 1 WHERE id = ?");
                update.setInt(1, id);
                update.executeUpdate();

                logDelivery("✔ " + lang.getOrDefault("delivered", "Delivered to") + " " + player.getName() + ": " + item + " x" + amount);
                deliveredSomething = true;
            }

            if (deliveredSomething) {
                player.sendMessage("§a" + lang.getOrDefault("player-delivered", "You have received your pending delivery from the store."));
            }

        } catch (SQLException e) {
            logDelivery("❌ " + lang.getOrDefault("delivery-error", "Delivery error for") + " " + player.getName() + ": " + e.getMessage());
        }
    }

    public void logDelivery(String message) {
        String date = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        File logsDir = new File(getDataFolder(), "transaction-logs");
        File logFile = new File(logsDir, date + ".log");

        try {
            if (!logsDir.exists()) logsDir.mkdirs();
            if (!logFile.exists()) logFile.createNewFile();

            try (FileWriter fw = new FileWriter(logFile, true)) {
                String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
                fw.write("[" + time + "] " + message + "\n");
            }
        } catch (IOException e) {
            getLogger().warning("❌ " + lang.getOrDefault("log-error", "Failed to write delivery log:") + " " + e.getMessage());
        }
    }

    public void cleanOldLogs() {
        int days = getConfig().getInt("log-retention-days", 30);
        File logsDir = new File(getDataFolder(), "transaction-logs");

        if (!logsDir.exists()) return;

        File[] files = logsDir.listFiles();
        if (files == null) return;

        long now = System.currentTimeMillis();
        long cutoff = now - (days * 24L * 60 * 60 * 1000);

        for (File file : files) {
            if (file.isFile() && file.getName().endsWith(".log") && file.lastModified() < cutoff) {
                if (file.delete()) {
                    getLogger().info("🗑️ " + lang.getOrDefault("log-deleted", "Old log removed:") + " " + file.getName());
                }
            }
        }
    }

    public Connection getConnection() {
        return connection;
    }

    public LanguageLoader getLang() {
        return lang;
    }
}
