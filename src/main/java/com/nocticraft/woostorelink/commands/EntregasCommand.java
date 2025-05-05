package com.nocticraft.woostorelink.commands;

import com.nocticraft.woostorelink.WooStoreLink;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class EntregasCommand implements CommandExecutor {

    private final WooStoreLink plugin;

    public EntregasCommand(WooStoreLink plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("woostorelink.admin")) {
                sender.sendMessage("§cNo tienes permiso para usar este comando.");
                return true;
            }

            plugin.reloadConfig();
            plugin.limpiarLogsAntiguos();
            plugin.connectToDatabase();
            sender.sendMessage("§aWooStoreLink recargado con éxito.");
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("check")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cEste comando solo puede ser usado por jugadores.");
                return true;
            }

            Player player = (Player) sender;

            try {
                PreparedStatement stmt = plugin.getConnection().prepareStatement(
                        "SELECT item, cantidad FROM entregas_pendientes WHERE jugador = ? AND entregado = 0");
                stmt.setString(1, player.getName());
                ResultSet rs = stmt.executeQuery();

                boolean found = false;
                while (rs.next()) {
                    String item = rs.getString("item");
                    int cantidad = rs.getInt("cantidad");
                    player.sendMessage("§aTienes pendiente: §e" + item + " x" + cantidad);
                    found = true;
                }

                if (!found) {
                    player.sendMessage("§7No tienes entregas pendientes.");
                }

            } catch (SQLException e) {
                player.sendMessage("§cError al consultar tus entregas.");
                plugin.logEntrega("❌ Error en /entregas check: " + e.getMessage());
            }

            return true;
        }

        sender.sendMessage("§eUso: /entregas <reload|check>");
        return true;
    }
}
