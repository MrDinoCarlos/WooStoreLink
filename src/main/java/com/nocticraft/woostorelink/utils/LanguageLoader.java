package com.nocticraft.woostorelink.utils;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class LanguageLoader {

    private final JavaPlugin plugin;
    private final Map<String, String> messages = new HashMap<>();
    private String activeLanguage = "en";

    public LanguageLoader(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load(String langCode) {
        messages.clear();
        this.activeLanguage = langCode;

        String filename = "messages_" + langCode + ".yml";
        File langFolder = new File(plugin.getDataFolder(), "lang");
        File langFile = new File(langFolder, filename);

        if (!langFolder.exists()) langFolder.mkdirs();

        if (!langFile.exists()) {
            String resourcePath = "lang/" + filename;
            if (plugin.getResource(resourcePath) != null) {
                plugin.saveResource(resourcePath, false);
                plugin.getLogger().info("✅ Language file copied: " + resourcePath);
            } else {
                plugin.getLogger().warning("⚠ Language file not found in JAR: " + resourcePath + ". Falling back to English.");
                load("en");
                return;
            }
        }

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(langFile);
            ConfigurationSection section = config.getConfigurationSection("messages");

            if (section == null) {
                plugin.getLogger().warning("⚠ 'messages:' section not found in " + filename);
                return;
            }

            for (String key : section.getKeys(true)) {
                messages.put(key, section.getString(key));
            }

            plugin.getLogger().info("🌐 Loaded language: " + langCode + " | Example: " + getOrDefault("plugin-enabled", "[Missing key]"));

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

    public String getActiveLanguage() {
        return activeLanguage;
    }
}
