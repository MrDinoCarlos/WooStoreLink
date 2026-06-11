package com.nocticraft.woostorelink.commands;

import com.nocticraft.woostorelink.WooStoreLink;
import com.nocticraft.woostorelink.utils.Achievement;
import com.nocticraft.woostorelink.utils.AchievementManager;
import com.nocticraft.woostorelink.utils.menu.ProfileMenu;
import com.nocticraft.woostorelink.delivery.DeliveriesMenu;
import com.nocticraft.woostorelink.utils.menu.HelpMenu;
import com.nocticraft.woostorelink.utils.menu.AchievementsMenu;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
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

    public WSLCommand(WooStoreLink plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length == 0 || args[0].equalsIgnoreCase("menu")) {
            if (!(sender instanceof Player p)) { send(sender, "only-players", "&cOnly players can use this command."); return true; }
            new ProfileMenu(plugin, p).open();
            return true;
        }
        if (args[0].equalsIgnoreCase("help")) {
            if (!(sender instanceof Player p)) { sendHelp(sender); return true; } // consola ve listado textual
            new HelpMenu(plugin, p).open();
            return true;
        }
        if (args[0].equalsIgnoreCase("deliveries")) {
            if (!(sender instanceof Player p)) { send(sender, "only-players", "&cOnly players can use this command."); return true; }
            new DeliveriesMenu(plugin, p, plugin.getDeliveryService().getQueue(p.getUniqueId())).open();
            return true;
        }
        if (args[0].equalsIgnoreCase("achievements")) {
            if (sender instanceof Player p) { new AchievementsMenu(plugin, p).open(); return true; }
            // consola: mantiene la versión textual que ya tenías
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                if (!sender.hasPermission("woostorelink.reload")) {
                    send(sender, "no-permission", "&cYou do not have permission to use this command.");
                    return true;
                }
                plugin.reloadConfig();
                plugin.loadLanguage();
                send(sender, "reloaded", "&aConfiguration and language reloaded.");
                break;

            case "check":
                if (!(sender instanceof Player) || !sender.hasPermission("woostorelink.check")) {
                    send(sender, "permission-player", "&cYou do not have permission or must be a player.");
                    return true;
                }
                plugin.processPendingDeliveries((Player) sender);
                send(sender, "checked-self", "&aChecked your pending deliveries.");
                break;

            case "checkplayer":
                if (!sender.hasPermission("woostorelink.check.others")) {
                    send(sender, "no-permission", "&cYou do not have permission to use this command.");
                    return true;
                }
                if (args.length < 2) {
                    send(sender, "usage-checkplayer", "&cUsage: /wsl checkplayer <player>");
                    break;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target != null) {
                    plugin.processPendingDeliveries(target);
                    sender.sendMessage(color(msg("checked-other", "&aChecked deliveries for %player%.")
                            .replace("%player%", target.getName())));
                } else {
                    send(sender, "player-not-found", "&cPlayer not found.");
                }
                break;

            case "status":
                if (!sender.hasPermission("woostorelink.status")) {
                    send(sender, "no-permission", "&cYou do not have permission to use this command.");
                    return true;
                }
                String tokenCfg = plugin.getConfig().getString("api-token");
                String domain = plugin.getConfig().getString("api-domain");
                boolean configured = tokenCfg != null && !tokenCfg.isEmpty() && domain != null && !domain.isEmpty();
                sender.sendMessage(color(msg("status-rest-api", "&eREST API: %status%")
                        .replace("%status%", configured
                                ? msg("status-configured", "&aConfigured")
                                : msg("status-missing-config", "&cMissing config"))));

                if (sender instanceof Player player) {
                    String name = player.getName();
                    long lastSync = plugin.getLinkManager().getLastSync(name);
                    long nextSync = lastSync + 3600;
                    sender.sendMessage(color(msg("status-last-sync", "&7Last Sync: &f%time%")
                            .replace("%time%", lastSync == 0 ? msg("status-never", "Never") : formatTime(lastSync))));
                    sender.sendMessage(color(msg("status-next-check", "&7Next Check: &f%time%")
                            .replace("%time%", lastSync == 0 ? msg("status-na", "N/A") : formatTime(nextSync))));
                    if (player.isOp()) {
                        String token = plugin.getConfig().getString("api-token");
                        sender.sendMessage(color(msg("status-token", "&7Token: &f%token%")
                                .replace("%token%", token != null ? token : msg("status-not-set", "Not set"))));
                    }
                }
                return true;

            case "wp-link":
                if (!(sender instanceof Player)) {
                    send(sender, "only-players", "&cOnly players can use this command.");
                    return true;
                }
                if (!sender.hasPermission("woostorelink.wp-link")) {
                    send(sender, "no-permission", "&cYou do not have permission to use this command.");
                    return true;
                }
                if (args.length < 2) {
                    send(sender, "usage-wp-link", "&cUsage: /wsl wp-link <your-email>");
                    return true;
                }
                requestLink((Player) sender, args[1]);
                return true;

            case "wp-verify":
                if (!(sender instanceof Player)) {
                    send(sender, "only-players", "&cOnly players can use this command.");
                    return true;
                }
                if (!sender.hasPermission("woostorelink.wp-verify")) {
                    send(sender, "no-permission", "&cYou do not have permission to use this command.");
                    return true;
                }
                if (args.length < 2) {
                    send(sender, "usage-wp-verify", "&cUsage: /wsl wp-verify <code>");
                    return true;
                }
                verifyCode((Player) sender, args[1]);
                return true;

            case "achievements":
            case "achievement":
            case "ach":
                handleAchievements(sender, args);
                return true;

            default:
                send(sender, "unknown-subcommand", "&cUnknown subcommand. Type &e/wsl help &cfor help.");
                break;
        }

        return true;
    }

    private void sendHelp(CommandSender sender) { /* igual que lo tenías */ }

    private String msg(String key, String fallback) {
        return plugin.getLang().getOrDefault(key, fallback);
    }

    private void send(CommandSender sender, String key, String fallback) {
        sender.sendMessage(color(msg(key, fallback)));
    }

    // Devuelve un texto de error legible a partir del body del API.
// Soporta {"error": "..."} o {"message": "..."} y fallback a body tal cual.
    private String extractApiErrorReason(String body) {
        if (body == null || body.isBlank()) return "Unknown error";
        try {
            com.google.gson.JsonElement el = com.google.gson.JsonParser.parseString(body);
            if (el.isJsonObject()) {
                var obj = el.getAsJsonObject();
                if (obj.has("error") && !obj.get("error").isJsonNull()) {
                    return obj.get("error").getAsString();
                }
                if (obj.has("message") && !obj.get("message").isJsonNull()) {
                    return obj.get("message").getAsString();
                }
            }
        } catch (Throwable ignored) { /* body no era JSON, usamos texto tal cual */ }
        return body;
    }


    private void requestLink(Player player, String rawEmail) {
        var lang = plugin.getLang();

        // Normalizamos el email por si hay espacios o mayúsculas
        String email = rawEmail == null ? "" : rawEmail.trim().toLowerCase();

        String domain = plugin.getConfig().getString("api-domain");
        String token  = plugin.getConfig().getString("api-token");

        if (domain == null || domain.isEmpty() || token == null || token.isEmpty()) {
            player.sendMessage(color(lang.getOrDefault("link-error", "&c✖ Failed: %reason%")
                    .replace("%reason%", "Missing api-domain/api-token in config.yml")));
            plugin.getLogger().warning("[wp-link] Missing api-domain/api-token in config.yml");
            return;
        }

        plugin.getLinkManager().setPendingEmail(player.getName(), email);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            HttpURLConnection conn = null;
            try {
                String apiUrl = domain + "/wp-json/storelinkformc/v1/request-link";
                String payload = "email=" + URLEncoder.encode(email, StandardCharsets.UTF_8.name()) +
                        "&player=" + URLEncoder.encode(player.getName(), StandardCharsets.UTF_8.name()) +
                        "&token="  + URLEncoder.encode(token, StandardCharsets.UTF_8.name());

                // Logs de depuración completos
                plugin.getLogger().info("[wp-link] POST " + apiUrl);
                plugin.getLogger().info("[wp-link] Payload: " + payload);

                conn = (HttpURLConnection) new URL(apiUrl).openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(15000);

                // Cabeceras extra "por si acaso"
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("User-Agent", "WooStoreLink/0.8.0 (Minecraft)");
                // Token también por header (además de en el body)
                conn.setRequestProperty("X-StoreLink-Token", token);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload.getBytes(StandardCharsets.UTF_8));
                }

                int responseCode = conn.getResponseCode();
                String responseBody;
                try (InputStream is = (responseCode >= 200 && responseCode < 300)
                        ? conn.getInputStream()
                        : conn.getErrorStream()) {
                    responseBody = (is == null) ? "" :
                            new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                                    .lines().collect(Collectors.joining("\n"));
                }

                plugin.getLogger().info("[wp-link] HTTP " + responseCode + ": " + responseBody);

                // Mostramos la respuesta real en el chat SIEMPRE en modo debug (puedes quitarlo luego)
                // player.sendMessage("§7[Debug] " + responseBody);

                if (responseCode != 200) {
                    String reason = extractApiErrorReason(responseBody);
                    player.sendMessage(color(lang.getOrDefault("link-error",
                            "&c✖ Failed: %reason%").replace("%reason%", reason)));
                    return;
                }

                // Si es 200, comprobamos el JSON por si viene success=false
                boolean success = true;
                String message  = "Verification code sent.";
                try {
                    var el = com.google.gson.JsonParser.parseString(responseBody);
                    if (el.isJsonObject()) {
                        var obj = el.getAsJsonObject();
                        if (obj.has("success")) success = obj.get("success").getAsBoolean();
                        if (obj.has("message") && !obj.get("message").isJsonNull())
                            message = obj.get("message").getAsString();
                    }
                } catch (Throwable ignored) {}

                if (!success) {
                    // 200 pero success=false -> mostramos causa si viene
                    String reason = extractApiErrorReason(responseBody);
                    player.sendMessage(color(lang.getOrDefault("link-error",
                            "&c✖ Failed: %reason%").replace("%reason%", reason)));
                    return;
                }

                // OK real
                player.sendMessage(color(lang.getOrDefault("link-started",
                        "&a✔ Verification code sent to your email.")));

                // (Opcional) mostrar el código si el backend lo adjunta y tienes debug activo
                boolean debugShow = plugin.getConfig().getBoolean("linking.debug-show-code", false);
                if (debugShow && player.isOp()) {
                    try {
                        var el = com.google.gson.JsonParser.parseString(responseBody);
                        if (el.isJsonObject() && el.getAsJsonObject().has("code")) {
                            String code = el.getAsJsonObject().get("code").getAsString();
                            player.sendMessage(color("&7[debug] Verification code: &e" + code));
                        }
                    } catch (Throwable ignored) {}
                }

            } catch (Exception e) {
                plugin.getLogger().warning("[wp-link] Exception: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                e.printStackTrace();
                player.sendMessage(color(lang.getOrDefault("link-error",
                        "&c✖ Failed: %reason%").replace("%reason%", e.getMessage() != null ? e.getMessage() : "unknown error")));
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private void verifyCode(Player player, String code) {
        var lang = plugin.getLang();
        String email = plugin.getLinkManager().getPendingEmail(player.getName());
        if (email == null) {
            player.sendMessage(color(lang.getOrDefault("link-error", "&c✖ Failed: %reason%")
                    .replace("%reason%", "You must link an email first using /wsl wp-link <email>")));
            return;
        }

        String domain = plugin.getConfig().getString("api-domain");
        String token  = plugin.getConfig().getString("api-token");

        if (domain == null || domain.isEmpty() || token == null || token.isEmpty()) {
            player.sendMessage(color(lang.getOrDefault("verify-error", "&c✖ Failed: %reason%")
                    .replace("%reason%", "Missing api-domain/api-token in config.yml")));
            plugin.getLogger().warning("[wp-verify] Missing api-domain/api-token in config.yml");
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            HttpURLConnection conn = null;
            try {
                String apiUrl = domain + "/wp-json/storelinkformc/v1/verify-link";
                String payload = "email=" + URLEncoder.encode(email, "UTF-8") +
                        "&code=" + URLEncoder.encode(code, "UTF-8") +
                        "&token=" + URLEncoder.encode(token, "UTF-8");

                plugin.getLogger().info("[wp-verify] POST " + apiUrl + " email=" + email + " player=" + player.getName());

                conn = (HttpURLConnection) new URL(apiUrl).openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(15000);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload.getBytes(StandardCharsets.UTF_8));
                }

                int responseCode = conn.getResponseCode();

                String responseBody;
                try (InputStream is = (responseCode >= 200 && responseCode < 300)
                        ? conn.getInputStream()
                        : conn.getErrorStream()) {
                    if (is != null) {
                        responseBody = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                                .lines().collect(Collectors.joining("\n"));
                    } else {
                        responseBody = "";
                    }
                }

                if (responseCode == 200) {
                    plugin.getLinkManager().clear(player.getName());
                    plugin.getLinkManager().setLinked(player.getName(), true);
                    player.sendMessage(color(lang.getOrDefault("verify-success",
                            "&a✔ Your account has been linked!")));
                    plugin.getLogger().info("[wp-verify] 200 OK: " + responseBody);
                } else {
                    String reason = extractApiErrorReason(responseBody);
                    player.sendMessage(color(lang.getOrDefault("verify-error",
                            "&c✖ Failed: %reason%").replace("%reason%", reason)));
                    plugin.getLogger().warning("[wp-verify] HTTP " + responseCode + ": " + responseBody);
                }


            } catch (Exception e) {
                plugin.getLogger().warning("[wp-verify] Exception: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                e.printStackTrace();
                player.sendMessage(color(lang.getOrDefault("verify-error",
                        "&c✖ Failed: %reason%").replace("%reason%", e.getMessage() != null ? e.getMessage() : "unknown error")));
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }


    private String formatTime(long unix) {
        java.time.Instant instant = java.time.Instant.ofEpochSecond(unix);
        java.time.ZonedDateTime zdt = instant.atZone(java.time.ZoneId.systemDefault());
        return java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(zdt);
    }

    private void handleAchievements(CommandSender sender, String[] args) { /* igual que lo tenías */ }

    private String color(String s) { return s.replace("&", "§"); }
}
