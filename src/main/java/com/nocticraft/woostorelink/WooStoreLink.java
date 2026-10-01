package com.nocticraft.woostorelink;

import com.nocticraft.woostorelink.commands.WSLCommand;
import com.nocticraft.woostorelink.delivery.DeliveryService;
import com.nocticraft.woostorelink.utils.*;
import com.nocticraft.woostorelink.utils.menu.MenuRegistry;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WooStoreLink extends JavaPlugin implements Listener {

    private static final Pattern WC_ID_PATTERN = Pattern.compile("(?i)(?:^|[\\s(#\\[])(?:id|product|product_id|variation|variation_id)\\s*[:#-]?\\s*(\\d+)(?:\\D|$)");

    private LanguageLoader lang;
    private String currentLangCode = "en";
    private volatile DeliveryFetcher fetcher;
    private ExecutorService networkExecutor;
    private ExecutorService updateExecutor;
    private ExecutorService logExecutor;
    private BukkitTask autoCheckTask;
    private final Set<UUID> inFlightPlayers = ConcurrentHashMap.newKeySet();
    private final Queue<FetchedBatch> processingQueue = new ConcurrentLinkedQueue<>();
    private final Set<UUID> updateNotified = new HashSet<>();
    private volatile String availableUpdate;

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

    private record FetchTarget(UUID uuid, String name) { }
    private record FetchedBatch(UUID uuid, String name, List<Delivery> deliveries) { }


    @Override
    public void onEnable() {
        networkExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "WooStoreLink-Network");
            thread.setDaemon(true);
            return thread;
        });
        logExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "WooStoreLink-Log");
            thread.setDaemon(true);
            return thread;
        });
        updateExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "WooStoreLink-UpdateCheck");
            thread.setDaemon(true);
            return thread;
        });

        saveDefaultConfig();
        ResourceUpdater.updateConfig(this);
        reloadConfig();
        ResourceUpdater.updateLanguages(this, "en", "es", "fr", "de");
        loadLanguage();
        cleanOldLogs();

        fetcher = new DeliveryFetcher(this);

        getLogger().info("Loaded language: " + currentLangCode + " | Example: " + lang.get("plugin-enabled"));
        Bukkit.getPluginManager().registerEvents(this, this);
        WSLCommand command = new WSLCommand(this);
        PluginCommand wslCommand = Objects.requireNonNull(
                getCommand("wsl"), "Command 'wsl' is missing from plugin.yml");
        wslCommand.setExecutor(command);
        wslCommand.setTabCompleter(command);
        StartupDisplay.show(this, lang);

        // Correct reference to the language loader
        this.achievementManager = new AchievementManager(this, lang);

        // Menu click listener
        getServer().getPluginManager().registerEvents(new MenuRegistry(), this);

        // Servicio de entregas (cola + reintentos periódicos)
        this.deliveryService = new DeliveryService(this);

        // Results are deliberately spread over ticks: one player's batch per tick.
        Bukkit.getScheduler().runTaskTimer(this, this::processNextFetchedBatch, 1L, 1L);
        scheduleAutoCheck();
        checkForUpdates();

    }

    public void loadLanguage() {
        currentLangCode = getConfig().getString("language", "en");
        lang = new LanguageLoader(this);
        lang.load(currentLangCode);
    }

    @Override
    public void onDisable() {
        if (autoCheckTask != null) {
            autoCheckTask.cancel();
        }
        if (deliveryService != null) {
            deliveryService.shutdown();
        }
        shutdownExecutor(networkExecutor);
        shutdownExecutor(updateExecutor);
        shutdownExecutor(logExecutor);
        Bukkit.getConsoleSender().sendMessage("§c✖ WooStoreLink has been disabled.");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(this, () -> processPendingDeliveries(event.getPlayer()), 20L);
        Bukkit.getScheduler().runTaskLater(this, () -> notifyUpdate(event.getPlayer()), 40L);
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
        requestDeliveryChecks(List.of(player));
    }

    public void reloadPluginConfiguration() {
        reloadConfig();
        loadLanguage();
        fetcher = new DeliveryFetcher(this);
        scheduleAutoCheck();
    }

    private void scheduleAutoCheck() {
        if (autoCheckTask != null) {
            autoCheckTask.cancel();
        }
        int minutes = Math.max(1, Math.min(1440, getConfig().getInt("check-interval-minutes", 1)));
        long interval = minutes * 60L * 20L;
        autoCheckTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (Bukkit.getOnlinePlayers().isEmpty()) {
                return;
            }
            logDelivery("[Auto] " + lang.getOrDefault("auto-check", "Checking pending deliveries for online players..."));
            requestDeliveryChecks(Bukkit.getOnlinePlayers());
        }, 30L * 20L, interval);
    }

    private void requestDeliveryChecks(Collection<? extends Player> players) {
        if (fetcher == null || !fetcher.isConfigured() || networkExecutor == null) {
            return;
        }

        int maxPlayers = Math.max(1, Math.min(500,
                getConfig().getInt("network.max-players-per-request", 100)));
        List<FetchTarget> targets = new ArrayList<>();
        for (Player player : players) {
            if (targets.size() >= maxPlayers) {
                break;
            }
            if (player.isOnline() && inFlightPlayers.add(player.getUniqueId())) {
                targets.add(new FetchTarget(player.getUniqueId(), player.getName()));
            }
        }
        if (targets.isEmpty()) {
            return;
        }

        try {
            networkExecutor.execute(() -> {
                Map<String, List<Delivery>> fetched = fetcher.fetchDeliveries(
                        targets.stream().map(FetchTarget::name).collect(Collectors.toList()));
                for (FetchTarget target : targets) {
                    List<Delivery> deliveries = fetched.getOrDefault(target.name(), List.of());
                    if (deliveries.isEmpty()) {
                        inFlightPlayers.remove(target.uuid());
                    } else {
                        processingQueue.offer(new FetchedBatch(target.uuid(), target.name(), deliveries));
                    }
                }
            });
        } catch (RejectedExecutionException exception) {
            targets.forEach(target -> inFlightPlayers.remove(target.uuid()));
        }
    }

    private void processNextFetchedBatch() {
        FetchedBatch batch = processingQueue.poll();
        if (batch == null) {
            return;
        }
        try {
            Player player = Bukkit.getPlayer(batch.uuid());
            if (player != null && player.isOnline() && player.getName().equals(batch.name())) {
                processFetchedDeliveries(player, batch.deliveries());
            }
        } finally {
            inFlightPlayers.remove(batch.uuid());
        }
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
            String productKey = resolveProductConfigKey(d, products);

            if (productKey == null) {
                String deliveryItem = d.getItem() == null ? "" : d.getItem();
                logDelivery("[Error] " + lang.getOrDefault("product-not-configured", "Product not configured:") + " " + deliveryItem);
                if (player.isOp()) {
                    player.sendMessage("§c" + lang.getOrDefault("product-not-configured-player", "Product") + " §e" + deliveryItem + "§c "
                            + lang.getOrDefault("product-not-configured-player-2", "is not configured on this server."));
                }
                continue;
            }

            ConfigurationSection productConfig = products.getConfigurationSection(productKey);
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
                        logDelivery("[Warn] Unknown product type: " + type + " (product: " + productKey + ")");
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
                    logDelivery("[OK] Delivered now to " + player.getName() + ": " + productKey + " ×" + deliveredCountThisDelivery);
                if (queuedCountThisDelivery > 0)
                    logDelivery("[QUEUE] Queued for " + player.getName() + ": " + productKey + " ×" + queuedCountThisDelivery +
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

        // Confirm all processed deliveries in a single request on the bounded network worker.
        if (!idsToMark.isEmpty()) {
            List<Integer> idsCopy = new ArrayList<>(idsToMark);
            try {
                networkExecutor.execute(() -> {
                    if (fetcher.markAsDelivered(idsCopy) && isEnabled()) {
                        Bukkit.getScheduler().runTask(this,
                                () -> idsCopy.forEach(recentlyDelivered::remove));
                    }
                });
            } catch (RejectedExecutionException ignored) {
                // The plugin is stopping. Keep IDs cached to prevent duplicate rewards.
            }
        }

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

    private String resolveProductConfigKey(Delivery delivery, ConfigurationSection products) {
        if (delivery == null || products == null) return null;

        List<String> candidates = new ArrayList<>();
        addIdCandidates(candidates, delivery.getVariationId(), true);
        addIdCandidates(candidates, delivery.getProductId(), false);

        String rawItem = delivery.getItem();
        if (rawItem != null) {
            String item = rawItem.trim();
            if (!item.isEmpty()) {
                candidates.add(item);
                candidates.add(item.toLowerCase(Locale.ROOT));

                Integer parsedId = extractWooCommerceId(item);
                if (parsedId != null) {
                    addIdCandidates(candidates, parsedId, true);
                    addIdCandidates(candidates, parsedId, false);
                }
            }
        }

        Set<String> seen = new LinkedHashSet<>(candidates);
        for (String candidate : seen) {
            if (products.contains(candidate)) return candidate;
        }

        Map<String, String> lowerCaseKeys = new HashMap<>();
        for (String key : products.getKeys(false)) {
            lowerCaseKeys.put(key.toLowerCase(Locale.ROOT), key);
        }
        for (String candidate : seen) {
            String matchedKey = lowerCaseKeys.get(candidate.toLowerCase(Locale.ROOT));
            if (matchedKey != null) return matchedKey;
        }

        return null;
    }

    private void addIdCandidates(List<String> candidates, int id, boolean variation) {
        if (id <= 0) return;

        String value = String.valueOf(id);
        candidates.add(value);
        candidates.add("id:" + value);
        candidates.add("wc:" + value);
        candidates.add((variation ? "variation:" : "product:") + value);
    }

    private Integer extractWooCommerceId(String item) {
        Matcher matcher = WC_ID_PATTERN.matcher(item);
        if (!matcher.find()) {
            return item.matches("\\d+") ? Integer.parseInt(item) : null;
        }

        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public void logDelivery(String message) {
        String date = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
        if (logExecutor == null) {
            return;
        }
        try {
            logExecutor.execute(() -> writeLogLine(date, time, message));
        } catch (RejectedExecutionException ignored) {
            // Shutdown already started.
        }
    }

    private void writeLogLine(String date, String time, String message) {
        File logsDir = new File(getDataFolder(), "transaction-logs");
        File logFile = new File(logsDir, date + ".log");
        try {
            if (!logsDir.exists() && !logsDir.mkdirs()) {
                throw new IOException("Could not create " + logsDir);
            }
            try (FileWriter writer = new FileWriter(logFile, true)) {
                writer.write("[" + time + "] " + message + System.lineSeparator());
            }
        } catch (IOException exception) {
            getLogger().warning("[Error] Failed to write delivery log: " + exception.getMessage());
        }
    }

    private void checkForUpdates() {
        if (!getConfig().getBoolean("update-check.enabled", true)) {
            return;
        }
        try {
            updateExecutor.execute(() -> new UpdateChecker(this).findUpdate().ifPresent(version -> {
                availableUpdate = version;
                String message = lang.getOrDefault("update-console",
                                "A new WooStoreLink version %version% is available: %url%")
                        .replace("%version%", version)
                        .replace("%url%", UpdateChecker.DOWNLOAD_URL);
                getLogger().warning(message);
                if (isEnabled()) {
                    Bukkit.getScheduler().runTask(this,
                            () -> Bukkit.getOnlinePlayers().forEach(this::notifyUpdate));
                }
            }));
        } catch (RejectedExecutionException ignored) {
            // Shutdown already started.
        }
    }

    private void notifyUpdate(Player player) {
        String version = availableUpdate;
        if (version == null || !player.hasPermission("woostorelink.update-notify")
                || !updateNotified.add(player.getUniqueId())) {
            return;
        }
        player.sendMessage(color(lang.getOrDefault("update-available",
                        "&6WooStoreLink &e%version% &6is available: &b%url%")
                .replace("%version%", version)
                .replace("%url%", UpdateChecker.DOWNLOAD_URL)));
    }

    private void shutdownExecutor(ExecutorService executor) {
        if (executor == null) {
            return;
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
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
