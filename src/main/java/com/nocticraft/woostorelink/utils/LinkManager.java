package com.nocticraft.woostorelink.utils;

import com.nocticraft.woostorelink.WooStoreLink;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class LinkManager {

    private final Map<String, String> pendingEmail = new HashMap<>(); // name -> email (temporal)
    private final Map<String, Long> lastSync = new HashMap<>();       // name -> epoch seconds

    // Nuevo: persistimos estado de vinculación
    private final Map<String, Boolean> linked = new HashMap<>();
    private final WooStoreLink plugin;
    private final File store;

    public LinkManager(WooStoreLink plugin) {
        this.plugin = plugin;
        this.store = new File(plugin.getDataFolder(), "link-state.yml");
        load();
    }

    // ---- pending email ----
    public void setPendingEmail(String name, String email) { pendingEmail.put(name.toLowerCase(), email); }
    public String getPendingEmail(String name) { return pendingEmail.get(name.toLowerCase()); }
    public void clear(String name) {
        pendingEmail.remove(name.toLowerCase());
    }

    // ---- last sync ----
    public void setLastSync(String name, long epochSec) { lastSync.put(name.toLowerCase(), epochSec); }
    public long getLastSync(String name) { return lastSync.getOrDefault(name.toLowerCase(), 0L); }

    // ---- linked state (nuevo) ----
    public boolean isLinked(String name) {
        return linked.getOrDefault(name.toLowerCase(), false);
    }

    public void setLinked(String name, boolean value) {
        linked.put(name.toLowerCase(), value);
        save();
    }

    private void save() {
        try {
            store.getParentFile().mkdirs();
            YamlConfiguration yml = new YamlConfiguration();
            for (var e : linked.entrySet()) {
                yml.set("linked." + e.getKey(), e.getValue());
            }
            yml.save(store);
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to save link-state.yml: " + ex.getMessage());
        }
    }

    private void load() {
        if (!store.exists()) return;
        try {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(store);
            if (yml.isConfigurationSection("linked")) {
                for (String key : yml.getConfigurationSection("linked").getKeys(false)) {
                    linked.put(key.toLowerCase(), yml.getBoolean("linked." + key));
                }
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to load link-state.yml: " + ex.getMessage());
        }
    }
}
