package com.nocticraft.woostorelink.commands;

import com.nocticraft.woostorelink.WooStoreLink;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

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
                if (!(sender instanceof Player) || !sender.hasPermission("woostorelink.check")) {
                    sender.sendMessage("§cYou do not have permission or must be a player.");
                    return true;
                }
                plugin.processPendingDeliveries((Player) sender);
                sender.sendMessage("§a✔ Checked your pending deliveries.");
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

                String tokenCfg = plugin.getConfig().getString("api-token");
                String domain = plugin.getConfig().getString("api-domain");
                boolean configured = tokenCfg != null && !tokenCfg.isEmpty() && domain != null && !domain.isEmpty();
                sender.sendMessage("§eREST API: " + (configured ? "§aConfigured ✔" : "§cMissing config ✘"));

                if (sender instanceof Player player) {
                    String name = player.getName();
                    long lastSync = plugin.getLinkManager().getLastSync(name);
                    long nextSync = lastSync + 3600; // 1h después (puedes hacer esto dinámico si usas config)

                    sender.sendMessage("§7🕓 Last Sync: §f" + (lastSync == 0 ? "Never" : formatTime(lastSync)));
                    sender.sendMessage("§7⏰ Next Check: §f" + (lastSync == 0 ? "N/A" : formatTime(nextSync)));

                    if (player.isOp()) {
                        String token = plugin.getConfig().getString("api-token");
                        sender.sendMessage("§7🔐 Token: §f" + (token != null ? token : "Not set"));
                    }
                }

                return true;


            case "wp-link":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§cOnly players can use this command.");
                    return true;
                }
                if (!sender.hasPermission("woostorelink.wp-link")) {
                    sender.sendMessage("§cYou do not have permission to use this command.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /wsl wp-link <your-email>");
                    return true;
                }
                requestLink((Player) sender, args[1]);
                return true;

            case "wp-verify":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§cOnly players can use this command.");
                    return true;
                }
                if (!sender.hasPermission("woostorelink.wp-verify")) {
                    sender.sendMessage("§cYou do not have permission to use this command.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /wsl wp-verify <code>");
                    return true;
                }
                verifyCode((Player) sender, args[1]);
                return true;

            default:
                sender.sendMessage("§cUnknown subcommand. Type §e/wsl help §cfor help.");
                break;
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6✦ WooStoreLink Commands ✦");
        sender.sendMessage("§e/wsl help §7- Show this help menu");
        sender.sendMessage("§e/wsl reload §7- Reload config and language");
        sender.sendMessage("§e/wsl check §7- Check your own pending deliveries");
        sender.sendMessage("§e/wsl checkplayer <name> §7- Check deliveries for another player");
        sender.sendMessage("§e/wsl status §7- Show REST API token & domain config status");
        sender.sendMessage("§e/wsl wp-link <email> §7- Link your WordPress account");
        sender.sendMessage("§e/wsl wp-verify <code> §7- Verify your email to complete the link");
    }

    private void requestLink(Player player, String email) {
        plugin.getLinkManager().setPendingEmail(player.getName(), email); // store pending email

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String apiUrl = plugin.getConfig().getString("api-domain") + "/wp-json/storelinkformc/v1/request-link";
                String token = plugin.getConfig().getString("api-token");
                String payload = "email=" + URLEncoder.encode(email, "UTF-8") +
                        "&player=" + URLEncoder.encode(player.getName(), "UTF-8") +
                        "&token=" + URLEncoder.encode(token, "UTF-8");


                HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload.getBytes(StandardCharsets.UTF_8));
                }

                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    player.sendMessage("§a✔ Verification code sent to your email.");
                } else {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
                    String response = reader.lines().collect(Collectors.joining());
                    player.sendMessage("§c✖ Failed: " + response);
                }

            } catch (Exception e) {
                player.sendMessage("§c✖ Error: " + e.getMessage());
            }
        });
    }

    private void verifyCode(Player player, String code) {
        String email = plugin.getLinkManager().getPendingEmail(player.getName());
        if (email == null) {
            player.sendMessage("§cYou must link an email first using /wsl wp-link <email>");
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String apiUrl = plugin.getConfig().getString("api-domain") + "/wp-json/storelinkformc/v1/verify-link";
                String token = plugin.getConfig().getString("api-token");
                String payload = "email=" + URLEncoder.encode(email, "UTF-8") +
                        "&code=" + URLEncoder.encode(code, "UTF-8") +
                        "&token=" + URLEncoder.encode(token, "UTF-8");


                HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload.getBytes(StandardCharsets.UTF_8));
                }

                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    plugin.getLinkManager().clear(player.getName());
                    player.sendMessage("§a✔ Your account has been linked!");
                } else {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
                    String response = reader.lines().collect(Collectors.joining());
                    player.sendMessage("§c✖ Failed: " + response);
                }

            } catch (Exception e) {
                player.sendMessage("§c✖ Error: " + e.getMessage());
            }
        });
    }
    private String formatTime(long unix) {
        java.time.Instant instant = java.time.Instant.ofEpochSecond(unix);
        java.time.ZonedDateTime zdt = instant.atZone(java.time.ZoneId.systemDefault());
        return java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(zdt);
    }
}

