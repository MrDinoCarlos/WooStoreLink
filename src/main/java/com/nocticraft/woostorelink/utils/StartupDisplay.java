package com.nocticraft.woostorelink.utils;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;

public class StartupDisplay {

    public static void show(JavaPlugin plugin, Connection connection, LanguageLoader lang) {
        Bukkit.getConsoleSender().sendMessage("§9§m----------------------------------------------------");
        Bukkit.getConsoleSender().sendMessage("§b                ✦ WooStoreLink Plugin ✦");
        Bukkit.getConsoleSender().sendMessage("§9§m----------------------------------------------------");
        Bukkit.getConsoleSender().sendMessage("§e┃ §6Version:   §f" + plugin.getDescription().getVersion());
        Bukkit.getConsoleSender().sendMessage("§e┃ §6Author:    §dMrDinoCarlos");
        Bukkit.getConsoleSender().sendMessage("§e┃ §6Website:   §9https://nocticraft.com/woostorelink");
        Bukkit.getConsoleSender().sendMessage("§e┃ §6MySQL:     " + (connection != null ? "§2Connected ✔" : "§4Not Connected ✘"));
        Bukkit.getConsoleSender().sendMessage("§e┃");
        Bukkit.getConsoleSender().sendMessage("§a┃ §l" + lang.getOrDefault("startup-ready", "WSL is ready to receive WooCommerce orders."));
        Bukkit.getConsoleSender().sendMessage("§9§m----------------------------------------------------");
    }
}
