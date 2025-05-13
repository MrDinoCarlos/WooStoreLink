package com.nocticraft.woostorelink.utils;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class StartupDisplay {

    public static void show(JavaPlugin plugin, LanguageLoader lang) {
        Bukkit.getConsoleSender().sendMessage("§9§m----------------------------------------------------");
        Bukkit.getConsoleSender().sendMessage("§b                ✦ WooStoreLink Plugin ✦");
        Bukkit.getConsoleSender().sendMessage("§9§m----------------------------------------------------");
        Bukkit.getConsoleSender().sendMessage("§e┃ §6Version:   §f" + plugin.getDescription().getVersion());
        Bukkit.getConsoleSender().sendMessage("§e┃ §6Author:    §dMrDinoCarlos");
        Bukkit.getConsoleSender().sendMessage("§e┃ §6Website:   §9https://nocticraft.com/woostorelink");
        Bukkit.getConsoleSender().sendMessage("§e┃ §6API Mode:  §2REST ✔");
        Bukkit.getConsoleSender().sendMessage("§e┃");
        Bukkit.getConsoleSender().sendMessage("§a┃ §l" + lang.getOrDefault("startup-ready", "WSL is ready to receive WooCommerce orders."));
        Bukkit.getConsoleSender().sendMessage("§9§m----------------------------------------------------");
    }
}
