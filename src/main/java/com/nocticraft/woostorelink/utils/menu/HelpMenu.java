package com.nocticraft.woostorelink.utils.menu;

import com.nocticraft.woostorelink.WooStoreLink;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.List;

public class HelpMenu extends Menu {
    private final WooStoreLink plugin;

    public HelpMenu(WooStoreLink plugin, Player viewer) {
        super(viewer);
        this.plugin = plugin;
        this.rows = 1;
    }

    @Override public String title() {
        return color(plugin.getLang().getOrDefault("menu-help-title", "&6Help"));
    }

    @Override public void draw() {
        inv.clear();
        inv.setItem(0, ItemBuilder.icon(Material.ARROW,
                color(plugin.getLang().getOrDefault("menu-back", "&6Back")),
                List.of(color(plugin.getLang().getOrDefault("menu-back-lore", "&7Return to Profile")))));
        inv.setItem(8, ItemBuilder.icon(Material.BARRIER,
                color(plugin.getLang().getOrDefault("menu-exit", "&cExit")),
                List.of(color(plugin.getLang().getOrDefault("menu-exit-lore", "&7Close")))));
        inv.setItem(4, ItemBuilder.icon(Material.BOOK,
                color(plugin.getLang().getOrDefault("menu-help-commands", "&eCommands")),
                List.of(
                        color(plugin.getLang().getOrDefault("menu-help-line-menu", "&e/wsl menu &7- Open profile")),
                        color(plugin.getLang().getOrDefault("menu-help-line-deliveries", "&e/wsl deliveries &7- Pending deliveries")),
                        color(plugin.getLang().getOrDefault("menu-help-line-achievements", "&e/wsl achievements &7- Your achievements")),
                        color(plugin.getLang().getOrDefault("menu-help-line-check", "&e/wsl check &7- Check pending from store")),
                        color(plugin.getLang().getOrDefault("menu-help-line-status", "&e/wsl status &7- Status info")),
                        color(plugin.getLang().getOrDefault("menu-help-line-link", "&e/wsl wp-link <email> &7- Link account")),
                        color(plugin.getLang().getOrDefault("menu-help-line-verify", "&e/wsl wp-verify <code> &7- Verify link"))
                )));
    }

    @Override public void onClick(InventoryClickEvent e) {
        super.onClick(e);
        if (e.getSlot()==0) { new ProfileMenu(plugin, viewer).open(); return; }
        if (e.getSlot()==8) { viewer.closeInventory(); }
    }

}
