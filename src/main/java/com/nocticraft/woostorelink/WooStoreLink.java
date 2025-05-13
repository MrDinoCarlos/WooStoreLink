package com.nocticraft.woostorelink;

import com.nocticraft.woostorelink.commands.WSLCommand;
import com.nocticraft.woostorelink.utils.*;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class WooStoreLink extends JavaPlugin implements Listener {

    private LanguageLoader lang;
    private String currentLangCode = "en";
    private DeliveryFetcher fetcher;

    // 🔒 Cache local para evitar entregas duplicadas antes de que el backend responda
    private final Set<Integer> recentlyDelivered = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadLanguage();
        cleanOldLogs();

        fetcher = new DeliveryFetcher(this);

        getLogger().info("🌐 Loaded language: " + currentLangCode + " | Example: " + lang.get("plugin-enabled"));
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("wsl").setExecutor(new WSLCommand(this));
        StartupDisplay.show(this, lang);

        int minutes = getConfig().getInt("check-interval-minutes", 1);
        long ticks = minutes * 60L * 20L;

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            logDelivery("⏰ " + lang.getOrDefault("auto-check", "Checking pending deliveries for online players..."));
            for (Player player : Bukkit.getOnlinePlayers()) {
                processPendingDeliveries(player);
            }
        }, 20L, ticks);
    }

    public void loadLanguage() {
        currentLangCode = getConfig().getString("language", "en");
        lang = new LanguageLoader(this);
        lang.load(currentLangCode);
    }

    @Override
    public void onDisable() {
        Bukkit.getConsoleSender().sendMessage("§c✖ WooStoreLink has been disabled.");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        processPendingDeliveries(event.getPlayer());
    }

    public void processPendingDeliveries(Player player) {
        List<Delivery> deliveries = fetcher.fetchDeliveries(player.getName());
        if (deliveries.isEmpty()) return;

        ConfigurationSection products = getConfig().getConfigurationSection("products");
        if (products == null) {
            logDelivery("⚠ " + lang.getOrDefault("products-section-missing", "Section 'products' not found in config.yml."));
            if (player.isOp()) {
                player.sendMessage("§c" + lang.getOrDefault("products-section-missing", "Section 'products' not found in config.yml."));
            }
            return;
        }

        Set<Integer> idsToMark = new HashSet<>();

        List<Delivery> toProcess = deliveries.stream()
                .filter(d -> !recentlyDelivered.contains(d.getId()))
                .collect(Collectors.toList());

        for (Delivery d : toProcess) {
            String productName = d.getItem().toLowerCase();

            if (!products.contains(productName)) {
                logDelivery("❌ " + lang.getOrDefault("product-not-configured", "Product not configured:") + " " + productName);
                if (player.isOp()) {
                    player.sendMessage("§c" + lang.getOrDefault("product-not-configured-player", "Product") + " §e" + productName + "§c " + lang.getOrDefault("product-not-configured-player-2", "is not configured on this server."));
                }
                continue;
            }

            ConfigurationSection productConfig = products.getConfigurationSection(productName);
            if (productConfig == null) continue;

            try {
                int amount = d.getAmount();

                if (productConfig.contains("type")) {
                    String type = productConfig.getString("type");
                    String value = productConfig.getString("value");

                    if ("item".equalsIgnoreCase(type)) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "give " + player.getName() + " " + value + " " + amount);
                    } else if ("command".equalsIgnoreCase(type)) {
                        String command = value.replace("{player}", player.getName());
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                    }
                } else {
                    if (productConfig.contains("items")) {
                        List<String> items = productConfig.getStringList("items");
                        for (String entryLine : items) {
                            String[] split = entryLine.split(" ");
                            String item = split[0];
                            int amt = (split.length > 1) ? Integer.parseInt(split[1]) : 1;
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "give " + player.getName() + " " + item + " " + (amt * amount));
                        }
                    }

                    if (productConfig.contains("commands")) {
                        List<String> commands = productConfig.getStringList("commands");
                        for (String cmd : commands) {
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("{player}", player.getName()));
                        }
                    }
                }

                idsToMark.add(d.getId());
                recentlyDelivered.add(d.getId()); // ⏱️ marcar como entregado temporalmente
                logDelivery("✔ " + lang.getOrDefault("delivered", "Delivered to") + " " + player.getName() + ": " + productName + " x" + amount);

            } catch (Exception e) {
                logDelivery("❌ Error delivering to " + player.getName() + ": " + e.getMessage());
            }
        }

        if (!idsToMark.isEmpty()) {
            fetcher.markAsDelivered(new ArrayList<>(idsToMark));
        }

        // ⌛ Limpiar los IDs tras 10 segundos
        Bukkit.getScheduler().runTaskLater(this, () -> {
            idsToMark.forEach(recentlyDelivered::remove);
        }, 200L); // 200 ticks = 10s

        if (!idsToMark.isEmpty()) {
            player.sendMessage("§a" + lang.getOrDefault("player-delivered", "You have received your pending delivery from the store."));
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

    public LanguageLoader getLang() {
        return lang;
    }
}
