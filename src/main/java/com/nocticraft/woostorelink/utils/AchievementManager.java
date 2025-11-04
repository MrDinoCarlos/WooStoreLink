package com.nocticraft.woostorelink.utils;

import com.nocticraft.woostorelink.WooStoreLink;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Manages purchase counter, unlocked achievements and reward execution.
 * Data is persisted to plugins/WooStoreLink/data/players.yml.
 */
public class AchievementManager {
    private final WooStoreLink plugin;
    private final LanguageLoader lang;

    private boolean enabled;
    private String counterName;

    private final Map<String, Achievement> achievements = new LinkedHashMap<>();

    private File dataFile;
    private FileConfiguration data;

    public AchievementManager(WooStoreLink plugin, LanguageLoader lang) {
        this.plugin = plugin;
        this.lang = lang;
        loadConfig();
        loadData();
    }

    public boolean isEnabled() { return enabled; }

    /** Increments the player's purchases by one and evaluates achievements. */
    public void addPurchaseAndCheck(Player player) {
        if (!enabled) return;
        UUID uuid = player.getUniqueId();
        int purchases = getPurchases(uuid) + 1;
        setPurchases(uuid, purchases);
        checkAndGrant(player, purchases);
        saveDataAsync();
    }

    public List<Achievement> getAll() { return new ArrayList<>(achievements.values()); }

    public boolean isUnlocked(UUID uuid, String id) {
        return getUnlocked(uuid).contains(id.toLowerCase(Locale.ROOT));
    }

    public int getPurchases(UUID uuid) {
        return data.getInt("players." + uuid + ".purchases", 0);
    }

    public Set<String> getUnlocked(UUID uuid) {
        List<String> raw = data.getStringList("players." + uuid + ".unlocked");
        return new HashSet<>(raw.stream().map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toSet()));
    }

    public String getCounterName() {
        return lang.get("achievements-counter-name").replace("%counter%", counterName);
    }

    /** Reloads both configuration and player data. */
    public void reload() {
        loadConfig();
        loadData();
    }

    // ----- internal loading -----

    private void loadConfig() {
        FileConfiguration cfg = plugin.getConfig();

        this.enabled = cfg.getBoolean("achievements.enabled", false);
        this.counterName = cfg.getString("achievements.counter-name", "Purchases");

        achievements.clear();

        ConfigurationSection root = cfg.getConfigurationSection("achievements");
        if (root == null) return;

        // Read the YAML list as a list of maps
        List<Map<?, ?>> list = root.getMapList("list");
        if (list == null) return;

        for (Map<?, ?> m : list) {
            if (m == null) continue;

            Object idObj = m.containsKey("id") ? m.get("id") : "undefined";
            String id = String.valueOf(idObj).toLowerCase(Locale.ROOT);

            int count = toInt(m.get("count"), 1);

            Object titleObj = m.containsKey("title") ? m.get("title") : id;
            String title = String.valueOf(titleObj);

            Object descObj = m.containsKey("description") ? m.get("description") : "";
            String description = String.valueOf(descObj);

            boolean broadcast = toBool(m.get("broadcast"), false);


            // rewards.{items|commands}
            List<String> itemRewards = Collections.emptyList();
            List<String> commandRewards = Collections.emptyList();

            Object rewardsObj = m.get("rewards");
            if (rewardsObj instanceof Map<?, ?> rm) {
                itemRewards = toStringList(rm.get("items"));
                commandRewards = toStringList(rm.get("commands"));
            }

            achievements.put(id, new Achievement(id, count, title, description, broadcast, itemRewards, commandRewards));
        }
    }

    /* ---------- small helpers ---------- */

    private static int toInt(Object o, int def) {
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception ignored) { return def; }
    }

    private static boolean toBool(Object o, boolean def) {
        if (o instanceof Boolean b) return b;
        if (o == null) return def;
        return Boolean.parseBoolean(String.valueOf(o));
    }

    private static List<String> toStringList(Object o) {
        if (o instanceof List<?> l) {
            return l.stream().map(String::valueOf).collect(java.util.stream.Collectors.toList());
        }
        return Collections.emptyList();
    }


    private void loadData() {
        File folder = new File(plugin.getDataFolder(), "data");
        if (!folder.exists()) folder.mkdirs();
        dataFile = new File(folder, "players.yml");
        if (!dataFile.exists()) {
            try { dataFile.createNewFile(); } catch (IOException ignored) {}
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void saveDataAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try { data.save(dataFile); } catch (IOException e) { e.printStackTrace(); }
        });
    }

    private void setPurchases(UUID uuid, int value) {
        data.set("players." + uuid + ".purchases", value);
    }

    private void addUnlocked(UUID uuid, String id) {
        List<String> list = data.getStringList("players." + uuid + ".unlocked");
        if (!list.contains(id)) {
            list.add(id);
            data.set("players." + uuid + ".unlocked", list);
        }
    }

    // ----- evaluation & rewards -----

    private void checkAndGrant(Player player, int purchases) {
        UUID uuid = player.getUniqueId();
        Set<String> unlocked = getUnlocked(uuid);

        for (Achievement a : achievements.values()) {
            if (unlocked.contains(a.getId())) continue;
            if (purchases >= a.getCount()) {
                grant(player, a);
                addUnlocked(uuid, a.getId());
            }
        }
    }

    private void grant(Player player, Achievement a) {
        // Notify player
        String counter = getCounterName();
        String msg = lang.get("achievement-unlocked-self")
                .replace("%title%", a.getTitle())
                .replace("%count%", String.valueOf(a.getCount()))
                .replace("%counter%", counter);
        player.sendMessage(color(msg));

        // Optional broadcast
        if (a.isBroadcast()) {
            String br = lang.get("achievement-unlocked-broadcast")
                    .replace("%player%", player.getName())
                    .replace("%title%", a.getTitle())
                    .replace("%count%", String.valueOf(a.getCount()))
                    .replace("%counter%", counter);
            Bukkit.broadcastMessage(color(br));
        }

        // Item rewards
        for (String def : a.getItemRewards()) {
            try {
                String[] parts = def.split("\\s+");
                Material mat = Material.matchMaterial(parts[0]);
                int amount = parts.length >= 2 ? Integer.parseInt(parts[1]) : 1;
                if (mat == null) continue;
                ItemStack item = new ItemStack(mat, amount);
                Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
                if (!overflow.isEmpty()) {
                    overflow.values().forEach(i -> player.getWorld().dropItemNaturally(player.getLocation(), i));
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("[WSL] Failed to give item reward '" + def + "' for achievement " + a.getId());
                String warn = lang.get("achievement-reward-failed").replace("%id%", a.getId());
                player.sendMessage(color(warn));
            }
        }

        // Command rewards
        for (String cmd : a.getCommandRewards()) {
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("{player}", player.getName()));
            } catch (Exception ex) {
                plugin.getLogger().warning("[WSL] Failed to execute command reward '" + cmd + "' for achievement " + a.getId());
                String warn = lang.get("achievement-reward-failed").replace("%id%", a.getId());
                player.sendMessage(color(warn));
            }
        }
    }

    private static String color(String s) { return s.replace("&", "§"); }
}
