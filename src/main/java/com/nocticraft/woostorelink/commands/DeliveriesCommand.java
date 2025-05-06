package com.nocticraft.woostorelink.commands;

import com.nocticraft.woostorelink.WooStoreLink;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;

public class DeliveriesCommand implements CommandExecutor {

    private final WooStoreLink plugin;

    public DeliveriesCommand(WooStoreLink plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        var lang = plugin.getLang();

        if (args.length == 0) {
            sender.sendMessage("§e" + lang.getOrDefault("usage", "Usage: /deliveries <reload|check|check-now|log <player>>"));
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload":
                if (!sender.hasPermission("woostorelink.admin")) {
                    sender.sendMessage("§c" + lang.getOrDefault("no-permission", "You do not have permission to use this command."));
                    return true;
                }
                plugin.reloadConfig();
                plugin.cleanOldLogs();
                plugin.connectToDatabase();
                plugin.getLang().load(plugin.getConfig().getString("language", "en"));
                sender.sendMessage("§a" + lang.getOrDefault("reloaded", "WooStoreLink reloaded successfully."));
                return true;

            case "check":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§c" + lang.getOrDefault("player-only", "This command can only be used by players."));
                    return true;
                }
                Player player = (Player) sender;
                showPendingDeliveries(player);
                return true;

            case "check-now":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§c" + lang.getOrDefault("player-only", "This command can only be used by players."));
                    return true;
                }
                Player checkNowPlayer = (Player) sender;
                plugin.processPendingDeliveries(checkNowPlayer);
                return true;

            case "log":
                if (!sender.hasPermission("woostorelink.admin")) {
                    sender.sendMessage("§c" + lang.getOrDefault("no-permission", "You do not have permission to use this command."));
                    return true;
                }

                if (args.length < 2) {
                    sender.sendMessage("§e" + lang.getOrDefault("log-usage", "Usage: /deliveries log <player>"));
                    return true;
                }

                String playerName = args[1];
                showLogForPlayer(sender, playerName);
                return true;

            default:
                sender.sendMessage("§e" + lang.getOrDefault("usage", "Usage: /deliveries <reload|check|check-now|log <player>>"));
                return true;
        }
    }

    private void showPendingDeliveries(Player player) {
        try {
            PreparedStatement stmt = plugin.getConnection().prepareStatement(
                    "SELECT item, cantidad FROM entregas_pendientes WHERE jugador = ? AND entregado = 0");
            stmt.setString(1, player.getName());
            ResultSet rs = stmt.executeQuery();

            boolean found = false;
            while (rs.next()) {
                String item = rs.getString("item");
                int cantidad = rs.getInt("cantidad");
                player.sendMessage("§a" + plugin.getLang().getOrDefault("pending-item", "Pending:") + " §e" + item + " x" + cantidad);
                found = true;
            }

            if (!found) {
                player.sendMessage("§7" + plugin.getLang().getOrDefault("no-pending", "You have no pending deliveries."));
            }

        } catch (SQLException e) {
            player.sendMessage("§c" + plugin.getLang().getOrDefault("error-checking", "Error checking your deliveries."));
            plugin.logDelivery("❌ Error in /deliveries check: " + e.getMessage());
        }
    }

    private void showLogForPlayer(CommandSender sender, String playerName) {
        File logsDir = new File(plugin.getDataFolder(), "transaction-logs");
        if (!logsDir.exists()) {
            sender.sendMessage("§c" + plugin.getLang().getOrDefault("log-folder-missing", "Log folder not found."));
            return;
        }

        sender.sendMessage("§6" + plugin.getLang().getOrDefault("logs-for", "Logs for") + " " + playerName + ":");

        for (File file : logsDir.listFiles()) {
            if (!file.getName().endsWith(".log")) continue;

            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.toLowerCase().contains(playerName.toLowerCase())) {
                        sender.sendMessage("§7" + line);
                    }
                }
            } catch (Exception e) {
                sender.sendMessage("§c" + plugin.getLang().getOrDefault("log-read-error", "Failed to read log file:") + " " + file.getName());
            }
        }
    }
}
