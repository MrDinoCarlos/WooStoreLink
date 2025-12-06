package com.nocticraft.woostorelink;

import com.nocticraft.woostorelink.commands.WSLCommand;
import com.nocticraft.woostorelink.delivery.DeliveryService;
import com.nocticraft.woostorelink.utils.*;
import com.nocticraft.woostorelink.utils.menu.MenuRegistry;
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

    // Local cache to prevent duplicate deliveries while the backend is being updated
    private final Set<Integer> recentlyDelivered = new HashSet<>();

    private final LinkManager linkManager = new LinkManager(this);
    public LinkManager getLinkManager() { return linkManager; }

    private AchievementManager achievementManager;
    public AchievementManager getAchievementManager() { return achievementManager; }

    private DeliveryService deliveryService;
    public DeliveryService getDeliveryService() { return deliveryService; }

    private final com.google.gson.Gson gson = new com.google.gson.Gson();
    public com.google.gson.Gson getGson() { return gson; }


    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadLanguage();
        cleanOldLogs();

        fetcher = new DeliveryFetcher(this);

        getLogger().info("Loaded language: " + currentLangCode + " | Example: " + lang.get("plugin-enabled"));
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("wsl").setExecutor(new WSLCommand(this));
        StartupDisplay.show(this, lang);

        int minutes = getConfig().getInt("check-interval-minutes", 1);
        long ticks = minutes * 60L * 20L;

        // Timer que ahora llama a una versión asíncrona internamente
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            logDelivery("[Auto] " + lang.getOrDefault("auto-check", "Checking pending deliveries for online players..."));
            for (Player player : Bukkit.getOnlinePlayers()) {
                processPendingDeliveries(player); // ahora no bloquea el main thread
            }
        }, 20L, ticks);

        // Correct reference to the language loader
        this.achievementManager = new AchievementManager(this, lang);

        // Menu click listener
        getServer().getPluginManager().registerEvents(new MenuRegistry(), this);

        // Servicio de entregas (cola + reintentos periódicos)
        this.deliveryService = new DeliveryService(this);

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
        processPendingDeliveries(event.getPlayer()); // ahora async
        if (deliveryService != null) {
            deliveryService.tryDeliverPlayer(event.getPlayer());
        }
    }

    /**
     * NUEVA implementación: solo lanza la parte HTTP en async.
     * Toda la lógica de dar ítems/comandos sigue en el main thread
     * a través de processFetchedDeliveries(...).
     */
    public void processPendingDeliveries(Player player) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {              // ASYNC HTTP
            List<Delivery> deliveries = fetcher.fetchDeliveries(player.getName());
            if (deliveries.isEmpty()) return;

            // Volvemos al hilo principal para procesar las entregas
            Bukkit.getScheduler().runTask(this, () ->                          // BACK TO MAIN
                    processFetchedDeliveries(player, deliveries));
        });
    }

    /**
     * Lógica original de processPendingDeliveries, pero recibiendo
     * la lista de deliveries ya obtenida desde el hilo asíncrono.
     * Este metodo se ejecuta SIEMPRE en el main thread.
     */
    private void processFetchedDeliveries(Player player, List<Delivery> deliveries) {
        // por si se desconecta entre el HTTP y el procesado
        if (!player.isOnline()) return;

        ConfigurationSection products = getConfig().getConfigurationSection("products");
        if (products == null) {
            logDelivery("[Warn] " + lang.getOrDefault("products-section-missing", "Section 'products' not found in config.yml."));
            if (player.isOp()) {
                player.sendMessage("§c" + lang.getOrDefault("products-section-missing", "Section 'products' not found in config.yml."));
            }
            return;
        }

        Set<Integer> idsToMark = new HashSet<>();

        int deliveredTotal = 0;
        int queuedTotal = 0;

        List<Delivery> toProcess = deliveries.stream()
                .filter(d -> !recentlyDelivered.contains(d.getId()))
                .collect(Collectors.toList());

        for (Delivery d : toProcess) {
            String productName = d.getItem().toLowerCase();

            if (!products.contains(productName)) {
                logDelivery("[Error] " + lang.getOrDefault("product-not-configured", "Product not configured:") + " " + productName);
                if (player.isOp()) {
                    player.sendMessage("§c" + lang.getOrDefault("product-not-configured-player", "Product") + " §e" + productName + "§c "
                            + lang.getOrDefault("product-not-configured-player-2", "is not configured on this server."));
                }
                continue;
            }

            ConfigurationSection productConfig = products.getConfigurationSection(productName);
            if (productConfig == null) continue;

            try {
                int amount = d.getAmount();

                int deliveredCountThisDelivery = 0;
                int queuedCountThisDelivery = 0;

                if (productConfig.contains("type")) {
                    String type = productConfig.getString("type");
                    String value = productConfig.getString("value");

                    if ("item".equalsIgnoreCase(type)) {
                        org.bukkit.Material mat = org.bukkit.Material.matchMaterial(value);
                        if (mat == null) {
                            logDelivery("[Error] Unknown material: " + value);
                        } else {
                            org.bukkit.inventory.ItemStack is = new org.bukkit.inventory.ItemStack(mat, amount);
                            if (getDeliveryService().tryDeliverNow(player, is)) deliveredCountThisDelivery++;
                            else queuedCountThisDelivery++;
                        }

                    } else if ("command".equalsIgnoreCase(type)) {
                        String command = value.replace("{player}", player.getName());
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);

                    } else {
                        logDelivery("[Warn] Unknown product type: " + type + " (product: " + productName + ")");
                    }

                } else {
                    if (productConfig.contains("items")) {
                        java.util.List<String> items = productConfig.getStringList("items");
                        for (String entryLine : items) {
                            String[] split = entryLine.split(" ");
                            String item = split[0];
                            int amt = (split.length > 1) ? Integer.parseInt(split[1]) : 1;

                            org.bukkit.Material mat = org.bukkit.Material.matchMaterial(item);
                            if (mat == null) {
                                logDelivery("[Error] Unknown material: " + item);
                                continue;
                            }
                            org.bukkit.inventory.ItemStack is = new org.bukkit.inventory.ItemStack(mat, amt * amount);
                            if (getDeliveryService().tryDeliverNow(player, is)) deliveredCountThisDelivery++;
                            else queuedCountThisDelivery++;
                        }
                    }
                    if (productConfig.contains("commands")) {
                        java.util.List<String> commands = productConfig.getStringList("commands");
                        for (String cmd : commands) {
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("{player}", player.getName()));
                        }
                    }
                }

                // Logros
                if (achievementManager != null && achievementManager.isEnabled()) {
                    achievementManager.addPurchaseAndCheck(player);
                }

                // Marcar siempre como procesado (aunque se haya encolado)
                idsToMark.add(d.getId());
                recentlyDelivered.add(d.getId());

                // Logs útiles
                if (deliveredCountThisDelivery > 0)
                    logDelivery("[OK] Delivered now to " + player.getName() + ": " + productName + " ×" + deliveredCountThisDelivery);
                if (queuedCountThisDelivery > 0)
                    logDelivery("[QUEUE] Queued for " + player.getName() + ": " + productName + " ×" + queuedCountThisDelivery +
                            " (inventory full)");

                // Acumular totales
                deliveredTotal += deliveredCountThisDelivery;

                // Si era sólo comando (no items ni queue), contamos como entregado "lógico"
                if (deliveredCountThisDelivery == 0 && queuedCountThisDelivery == 0
                        && productConfig.contains("type")
                        && "command".equalsIgnoreCase(productConfig.getString("type"))) {
                    deliveredTotal++;
                }

                queuedTotal += queuedCountThisDelivery;

            } catch (Exception e) {
                logDelivery("[Error] Delivering to " + player.getName() + ": " + e.getMessage());
            }
        }

        // --- Notificar al backend --- (ahora en async para no bloquear)
        if (!idsToMark.isEmpty()) {
            List<Integer> idsCopy = new ArrayList<>(idsToMark);
            Bukkit.getScheduler().runTaskAsynchronously(this, () ->           // ASYNC markAsDelivered
                    fetcher.markAsDelivered(idsCopy));
        }

        // --- Limpiar IDs locales en 10 segundos ---
        Bukkit.getScheduler().runTaskLater(this, () -> {
            idsToMark.forEach(recentlyDelivered::remove);
        }, 200L);

        // --- Mensajes al jugador ---
        if (deliveredTotal > 0) {
            player.sendMessage(color(lang.getOrDefault("player-delivered-count",
                    "You received %count% item(s) from the store.").replace("%count%", String.valueOf(deliveredTotal))));
        }
        if (queuedTotal > 0) {
            player.sendMessage(color(lang.getOrDefault("player-queued-count",
                            "%count% item(s) were queued. Free up space, or open /wsl menu → Deliveries to claim.")
                    .replace("%count%", String.valueOf(queuedTotal))));
        }

        // actualizar lastSync si hubo algo procesado
        if (deliveredTotal > 0 || queuedTotal > 0) {
            linkManager.setLastSync(player.getName(), System.currentTimeMillis() / 1000L);
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
            getLogger().warning("[Error] " + lang.getOrDefault("log-error", "Failed to write delivery log:") + " " + e.getMessage());
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
                    getLogger().info("[GC] " + lang.getOrDefault("log-deleted", "Old log removed:") + " " + file.getName());
                }
            }
        }
    }

    public LanguageLoader getLang() { return lang; }

    private String color(String s) {
        return s.replace("&", "§");
    }
}
