package com.nocticraft.woostorelink.delivery;

import com.nocticraft.woostorelink.WooStoreLink;
import com.nocticraft.woostorelink.utils.menu.ItemBuilder;
import com.nocticraft.woostorelink.utils.menu.Menu;
import com.nocticraft.woostorelink.utils.menu.ProfileMenu;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class DeliveriesMenu extends Menu {
    private final WooStoreLink plugin;
    private final List<ItemStack> data;
    private int page = 0;

    public DeliveriesMenu(WooStoreLink plugin, Player viewer, List<ItemStack> data) {
        super(viewer);
        this.plugin = plugin;
        this.data = data;
        this.rows = 6;
        refreshData(); // ← sincroniza al abrir
    }

    @Override public String title() {
        return color(plugin.getLang().getOrDefault("menu-deliveries-title", "&6Pending deliveries"));
    }

    @Override public void draw() {
        refreshData();
        inv.clear();
        // barra superior: back (0) y exit (8)
        String backName = color(plugin.getLang().getOrDefault("menu-back", "&6Back"));
        String backLore = color(plugin.getLang().getOrDefault("menu-back-lore", "&7Return to Profile"));
        String exitName = color(plugin.getLang().getOrDefault("menu-exit", "&cExit"));
        String exitLore = color(plugin.getLang().getOrDefault("menu-exit-lore", "&7Close"));

        inv.setItem(0, ItemBuilder.icon(Material.ARROW, backName, List.of(backLore)));
        inv.setItem(8, ItemBuilder.icon(Material.BARRIER, exitName, List.of(exitLore)));

        int start = page * 28;
        for (int i = 0; i < 28 && start + i < data.size(); i++) {
            ItemStack icon = data.get(start + i).clone();
            var meta = icon.getItemMeta();
            String lore = plugin.getLang().getOrDefault("deliveries-click-to-claim", "&e&oClick to claim");
            meta.setLore(java.util.List.of(color(lore)));
            icon.setItemMeta(meta);
            inv.setItem(slotForIndex(i), icon);
        }

        String prev = color(plugin.getLang().getOrDefault("menu-prev", "&6Prev"));
        String next = color(plugin.getLang().getOrDefault("menu-next", "&6Next"));
        inv.setItem(45, ItemBuilder.icon(Material.ARROW, prev, List.of()));
        inv.setItem(53, ItemBuilder.icon(Material.ARROW, next, List.of()));
    }


    private int slotForIndex(int i) { int row = i / 7, col = i % 7; return (row+1)*9 + (col+1); }

    @Override public void onClick(InventoryClickEvent e) {
        e.setCancelled(true);
        if (e.getSlot() == 0) { new ProfileMenu(plugin, viewer).open(); return; }
        if (e.getSlot() == 8) { viewer.closeInventory(); return; }
        if (e.getSlot() == 45 && page>0) { page--; refreshData(); draw(); return; }
        if (e.getSlot() == 53 && (page+1)*28 < data.size()) { page++; refreshData(); draw(); return; }

        int row = e.getSlot()/9 - 1, col = e.getSlot()%9 - 1;
        if (row < 0 || row > 3 || col < 0 || col > 6) return;
        int index = page*28 + (row*7 + col);
        if (index < 0 || index >= data.size()) return;

        if (plugin.getDeliveryService().claimFromQueue(viewer, index)) {
            refreshData();
            draw();
        } else {
            // sigue en cola, mensajes ya enviados desde DeliveryService
        }
    }

    private void refreshData() {
        data.clear();
        data.addAll(plugin.getDeliveryService().getQueue(viewer.getUniqueId()));
        int maxPage = (int) Math.ceil(Math.max(1, data.size()) / 28.0) - 1;
        if (page > maxPage) page = Math.max(0, maxPage);
    }
}
