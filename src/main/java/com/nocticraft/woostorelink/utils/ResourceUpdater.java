package com.nocticraft.woostorelink.utils;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class ResourceUpdater {

    private ResourceUpdater() {
    }

    public static void updateConfig(JavaPlugin plugin) {
        updateYaml(plugin, "config.yml", "config-version", buildConfigHeader(plugin), true);
    }

    public static void updateLanguages(JavaPlugin plugin, String... languageCodes) {
        for (String languageCode : languageCodes) {
            updateYaml(plugin, "lang/messages_" + languageCode + ".yml", "language-version", null, false);
        }
    }

    private static void updateYaml(JavaPlugin plugin, String resourcePath, String versionPath, List<String> header, boolean reloadConfig) {
        File target = new File(plugin.getDataFolder(), resourcePath);
        if (!target.exists()) {
            plugin.saveResource(resourcePath, false);
            return;
        }

        try (InputStream stream = plugin.getResource(resourcePath)) {
            if (stream == null) {
                plugin.getLogger().warning("Bundled resource not found: " + resourcePath);
                return;
            }

            YamlConfiguration bundled = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
            YamlConfiguration current = YamlConfiguration.loadConfiguration(target);

            String bundledVersion = bundled.getString(versionPath, "0");
            String currentVersion = current.getString(versionPath, "0");
            if (compareVersions(currentVersion, bundledVersion) >= 0) {
                return;
            }

            copyMissingValues(bundled, current, "");
            current.set(versionPath, bundledVersion);
            if (header != null) {
                current.options().setHeader(header);
            }
            current.save(target);

            if (reloadConfig) {
                plugin.reloadConfig();
            }
            plugin.getLogger().info("Updated " + resourcePath + " from " + currentVersion + " to " + bundledVersion
                    + " without overwriting existing settings.");
        } catch (Exception e) {
            plugin.getLogger().warning("Could not update " + resourcePath + ": " + e.getMessage());
        }
    }

    private static void copyMissingValues(ConfigurationSection source, ConfigurationSection target, String basePath) {
        for (String key : source.getKeys(false)) {
            String path = basePath.isEmpty() ? key : basePath + "." + key;
            if (source.isConfigurationSection(key)) {
                if (!target.isConfigurationSection(path)) {
                    target.createSection(path);
                }
                copyMissingValues(source.getConfigurationSection(key), target, path);
            } else if (!target.contains(path)) {
                target.set(path, source.get(key));
            }
        }
    }

    private static int compareVersions(String left, String right) {
        String[] leftParts = left == null ? new String[]{"0"} : left.split("[.-]");
        String[] rightParts = right == null ? new String[]{"0"} : right.split("[.-]");
        int length = Math.max(leftParts.length, rightParts.length);

        for (int i = 0; i < length; i++) {
            int leftValue = i < leftParts.length ? parseVersionPart(leftParts[i]) : 0;
            int rightValue = i < rightParts.length ? parseVersionPart(rightParts[i]) : 0;
            if (leftValue != rightValue) {
                return Integer.compare(leftValue, rightValue);
            }
        }
        return 0;
    }

    private static int parseVersionPart(String part) {
        try {
            return Integer.parseInt(part.replaceAll("\\D", ""));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static List<String> buildConfigHeader(JavaPlugin plugin) {
        return List.of(
                "",
                "  __        __          ____  _                 _     _       _",
                "  \\ \\      / /__   ___ / ___|| |_ ___  _ __ ___| |   (_)_ __ | | __",
                "   \\ \\ /\\ / / _ \\ / _ \\\\___ \\| __/ _ \\| '__/ _ \\ |   | | '_ \\| |/ /",
                "    \\ V  V / (_) | (_) |___) | || (_) | | |  __/ |___| | | | |   <",
                "     \\_/\\_/ \\___/ \\___/|____/ \\__\\___/|_|  \\___|_____|_|_| |_|_|\\_\\",
                "",
                "  WooStoreLink " + plugin.getDescription().getVersion() + " - Release 1.0",
                "  Minecraft compatibility: 1.20.x, 1.21.x, 1.26.x",
                "  Keep config-version untouched. It lets WooStoreLink add new options",
                "  during updates without replacing your existing server settings.",
                "");
    }
}
