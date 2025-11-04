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

    @Override public String title() { return "§6Help"; }

    @Override public void draw() {
        inv.clear();
        inv.setItem(0, ItemBuilder.icon(Material.ARROW, "§6Back", List.of("§7Return to Profile")));
        inv.setItem(8, ItemBuilder.icon(Material.BARRIER, "§cExit", List.of("§7Close")));
        inv.setItem(4, ItemBuilder.icon(Material.BOOK, "§eCommands",
                List.of(
                        "§e/wsl menu §7- Open profile",
                        "§e/wsl deliveries §7- Pending deliveries",
                        "§e/wsl achievements §7- Your achievements",
                        "§e/wsl check §7- Check pending from store",
                        "§e/wsl status §7- Status info",
                        "§e/wsl wp-link <email> §7- Link account",
                        "§e/wsl wp-verify <code> §7- Verify link"
                )));
    }

    @Override public void onClick(InventoryClickEvent e) {
        super.onClick(e);
        if (e.getSlot()==0) { new ProfileMenu(plugin, viewer).open(); return; }
        if (e.getSlot()==8) { viewer.closeInventory(); }
    }
}
