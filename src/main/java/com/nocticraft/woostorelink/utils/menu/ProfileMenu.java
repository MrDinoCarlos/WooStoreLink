package com.nocticraft.woostorelink.utils.menu;

import com.nocticraft.woostorelink.WooStoreLink;
import com.nocticraft.woostorelink.utils.Achievement;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.bukkit.Bukkit.getOfflinePlayer;

public class ProfileMenu extends Menu {
    private final WooStoreLink plugin;

    public ProfileMenu(WooStoreLink plugin, Player viewer) {
        super(viewer);
        this.plugin = plugin;
        this.rows = 1; // 1 línea (9 slots: 0..8)
    }

    @Override
    public String title() {
        return color(plugin.getLang().getOrDefault("menu-title-profile", "&6&lProfile &7- &f%player%")
                .replace("%player%", viewer.getName()));
    }

    @Override
    public void draw() {
        inv.clear();

        // 0: Cabeza
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta sm = (SkullMeta) head.getItemMeta();
        sm.setOwningPlayer(getOfflinePlayer(viewer.getUniqueId()));
        sm.setDisplayName("§e§l" + viewer.getName());
        sm.setLore(List.of("§7UUID: §f" + viewer.getUniqueId()));
        head.setItemMeta(sm);
        inv.setItem(0, head);

        // 2: Vinculación (aproximación con lastSync>0)
        boolean linkingEnabled = plugin.getConfig().getBoolean("linking.enabled", true);
        boolean isLinked = plugin.getLinkManager() != null && plugin.getLinkManager().isLinked(viewer.getName());
        long lastSync = plugin.getLinkManager() != null ? plugin.getLinkManager().getLastSync(viewer.getName()) : 0L;
        boolean isLinkedApprox = lastSync > 0;
        inv.setItem(2, ItemBuilder.icon(
                isLinked ? Material.LIME_DYE : Material.RED_DYE,
                color(plugin.getLang().getOrDefault("menu-profile-linking-name", "&6Linking")),
                List.of(
                        color(plugin.getLang().getOrDefault("menu-profile-linking-lore-1", "&7Server: %server%")
                                .replace("%server%", plugin.getConfig().getBoolean("linking.enabled", true)
                                        ? plugin.getLang().getOrDefault("menu-profile-linking-lore-server-enabled", "&aenabled")
                                        : plugin.getLang().getOrDefault("menu-profile-linking-lore-server-disabled", "&cdisabled"))),
                        color(plugin.getLang().getOrDefault("menu-profile-linking-lore-2", "&7Your status: %status%")
                                .replace("%status%", isLinked
                                        ? plugin.getLang().getOrDefault("menu-profile-linking-status-linked", "&alinked")
                                        : plugin.getLang().getOrDefault("menu-profile-linking-status-notlinked", "&cnot linked"))),
                        color(plugin.getLang().getOrDefault("menu-profile-linking-lore-3", "&e&oClick for instructions"))
                )));


        // 4: Entregas
        int pending = plugin.getDeliveryService() != null
                ? plugin.getDeliveryService().countPending(viewer.getUniqueId()) : 0;
        inv.setItem(4, ItemBuilder.icon(
                pending > 0 ? Material.CHEST_MINECART : Material.CHEST,
                color(plugin.getLang().getOrDefault("menu-profile-deliveries-name", "&6Deliveries")),
                List.of(
                        color(plugin.getLang().getOrDefault("menu-profile-deliveries-lore-1", "&7Pending: &f%count%")
                                .replace("%count%", String.valueOf(pending))),
                        color(plugin.getLang().getOrDefault("menu-profile-deliveries-lore-2", "&e&oClick to open"))
                )));

        // 6: Logros (cálculo con getAll + isUnlocked)
        int unlocked = 0, total = 0;
        try {
            if (plugin.getAchievementManager() != null) {
                var am = plugin.getAchievementManager();
                List<Achievement> all = am.getAll(); total = all.size();
                UUID id = viewer.getUniqueId();
                for (Achievement a : all) if (am.isUnlocked(id, a.getId())) unlocked++;
            }
        } catch (Throwable ignored) {}
        inv.setItem(6, ItemBuilder.icon(Material.NETHER_STAR,
                color(plugin.getLang().getOrDefault("menu-profile-achievements-name", "&6Achievements")),
                List.of(
                        color(plugin.getLang().getOrDefault("menu-profile-achievements-lore-1", "&7Unlocked: &f%unlocked%/%total%")
                                .replace("%unlocked%", String.valueOf(unlocked))
                                .replace("%total%", String.valueOf(total))),
                        color(plugin.getLang().getOrDefault("menu-profile-achievements-lore-2", "&e&oClick to open"))
                )));

        // 7: Help
        inv.setItem(7, ItemBuilder.icon(Material.BOOK,
                color(plugin.getLang().getOrDefault("menu-profile-help-name", "&6Help")),
                List.of(color(plugin.getLang().getOrDefault("menu-profile-help-lore", "&7View all commands and info")))));

        // 8: Exit
        inv.setItem(8, ItemBuilder.icon(Material.BARRIER,
                color(plugin.getLang().getOrDefault("menu-exit", "&cExit")),
                List.of(color(plugin.getLang().getOrDefault("menu-exit-lore", "&7Close")))));
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        super.onClick(e);
        switch (e.getSlot()) {
            case 2 -> viewer.performCommand("wsl wp-link");        // ajusta si tienes otro flujo
            case 4 -> viewer.performCommand("wsl deliveries");
            case 6 -> viewer.performCommand("wsl achievements");   // abre el AchievementsMenu (si ya lo añadiste)
            case 7 -> new HelpMenu(plugin, viewer).open();         // Help
            case 8 -> viewer.closeInventory();                     // Exit
            default -> { }
        }
    }
}
