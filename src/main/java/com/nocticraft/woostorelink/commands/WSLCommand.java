package com.nocticraft.woostorelink.commands;

import com.nocticraft.woostorelink.WooStoreLink;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

public class WSLCommand implements CommandExecutor {

    private final WooStoreLink plugin;

    public WSLCommand(WooStoreLink plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                if (!sender.hasPermission("woostorelink.reload")) {
                    sender.sendMessage("§cYou do not have permission to use this command.");
                    return true;
                }
                plugin.reloadConfig();
                plugin.loadLanguage();
                sender.sendMessage("§a✔ Configuration and language reloaded.");
                break;

            case "check":
                if (!sender.hasPermission("woostorelink.check")) {
                    sender.sendMessage("§cYou do not have permission to use this command.");
                    return true;
                }
                if (sender instanceof Player) {
                    plugin.processPendingDeliveries((Player) sender);
                    sender.sendMessage("§a✔ Checked your pending deliveries.");
                } else {
                    sender.sendMessage("§cOnly players can use this command.");
                }
                break;

            case "checkplayer":
                if (!sender.hasPermission("woostorelink.check.others")) {
                    sender.sendMessage("§cYou do not have permission to use this command.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /wsl checkplayer <player>");
                    break;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target != null) {
                    plugin.processPendingDeliveries(target);
                    sender.sendMessage("§a✔ Checked deliveries for " + target.getName());
                } else {
                    sender.sendMessage("§cPlayer not found.");
                }
                break;

            case "status":
                if (!sender.hasPermission("woostorelink.status")) {
                    sender.sendMessage("§cYou do not have permission to use this command.");
                    return true;
                }

                String token = plugin.getConfig().getString("api-token");
                String domain = plugin.getConfig().getString("api-domain");
                boolean configured = token != null && !token.isEmpty() && domain != null && !domain.isEmpty();

                sender.sendMessage("§eREST API: " + (configured ? "§aConfigured ✔" : "§cMissing config ✘"));
                break;

            default:
                sender.sendMessage("§cUnknown subcommand. Type §e/wsl help §cfor help.");
                break;
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6✦ WooStoreLink Commands ✦");

        if (sender.hasPermission("woostorelink.help"))
            sender.sendMessage("§e/wsl help §7- Show this help menu");
        if (sender.hasPermission("woostorelink.reload"))
            sender.sendMessage("§e/wsl reload §7- Reload config and language");
        if (sender.hasPermission("woostorelink.check"))
            sender.sendMessage("§e/wsl check §7- Check your own pending deliveries");
        if (sender.hasPermission("woostorelink.check.others"))
            sender.sendMessage("§e/wsl checkplayer <name> §7- Check deliveries for another player");
        if (sender.hasPermission("woostorelink.status"))
            sender.sendMessage("§e/wsl status §7- Show REST API token & domain config status");
    }
}
