package com.nocticraft.woostorelink.utils;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class LanguageLoader {

    private final JavaPlugin plugin;
    private Map<String, String> messages;

    public LanguageLoader(JavaPlugin plugin) {
        this.plugin = plugin;
        this.messages = new HashMap<>();
    }

    public void load(String langCode) {
        messages.clear();  // limpia anteriores si recargas
        File langFile = new File(plugin.getDataFolder(), "lang/messages_" + langCode + ".yml");
        if (!langFile.exists()) {
            plugin.getLogger().warning("⚠ Language file not found: messages_" + langCode + ".yml. Defaulting to English.");
            langFile = new File(plugin.getDataFolder(), "lang/messages_en.yml");
        }

        if (langFile.exists()) {
            try {
                YamlConfiguration config = YamlConfiguration.loadConfiguration(langFile);
                for (String key : config.getKeys(true)) {
                    messages.put(key, config.getString(key));
                }
            } catch (Exception e) {
                plugin.getLogger().severe("❌ Failed to load language file: " + e.getMessage());
            }
        }
    }


    public String get(String key) {
        return messages.getOrDefault(key, "§c[Missing lang: " + key + "]");
    }

    public String getOrDefault(String key, String fallback) {
        return messages.getOrDefault(key, fallback);
    }
}
