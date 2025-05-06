package com.nocticraft.woostorelink.utils;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class LanguageLoader {

    private final JavaPlugin plugin;
    private final Map<String, String> messages = new HashMap<>();

    public LanguageLoader(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load(String langCode) {
        messages.clear();

        File langFolder = new File(plugin.getDataFolder(), "lang");
        if (!langFolder.exists()) langFolder.mkdirs();

        File langFile = new File(langFolder, "messages_" + langCode + ".yml");
        String resourcePath = "lang/messages_" + langCode + ".yml";

        if (!langFile.exists()) {
            if (plugin.getResource(resourcePath) != null) {
                plugin.saveResource(resourcePath, false);
                plugin.getLogger().info("✅ Copied language file: " + resourcePath);
            } else {
                plugin.getLogger().warning("⚠ Language file not found in JAR: " + resourcePath + ". Defaulting to English.");
                langFile = new File(langFolder, "messages_en.yml");
                plugin.saveResource("lang/messages_en.yml", false);
            }
        }

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(langFile);
            for (String key : config.getKeys(true)) {
                messages.put(key, config.getString(key));
            }
        } catch (Exception e) {
            plugin.getLogger().severe("❌ Failed to load language file: " + e.getMessage());
        }
    }

    public String get(String key) {
        return messages.getOrDefault(key, "§c[Missing lang: " + key + "]");
    }

    public String getOrDefault(String key, String fallback) {
        return messages.getOrDefault(key, fallback);
    }
}
