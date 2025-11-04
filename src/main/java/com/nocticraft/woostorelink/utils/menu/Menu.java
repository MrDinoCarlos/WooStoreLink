package com.nocticraft.woostorelink.utils.menu;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

public abstract class Menu {
    protected final Player viewer;
    public Inventory inv;
    protected int rows = 6;

    public Menu(Player viewer) { this.viewer = viewer; }

    public abstract String title();   // usa § colores
    public abstract void draw();      // colocar ítems

    public void open() {
        inv = Bukkit.createInventory(null, rows * 9, title());
        draw();
        viewer.openInventory(inv);
        MenuRegistry.bind(viewer.getUniqueId(), this);
    }

    public void onClick(InventoryClickEvent e) { e.setCancelled(true); }

    protected String color(String s) {
        return s == null ? "" : s.replace("&", "§");
    }
}
