package com.nocticraft.woostorelink.utils.menu;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MenuRegistry implements Listener {
    private static final Map<UUID, Menu> openMenus = new HashMap<>();
    public static void bind(UUID id, Menu menu) { openMenus.put(id, menu); }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Menu m = openMenus.get(p.getUniqueId());
        if (m == null || e.getClickedInventory() == null) return;
        if (!e.getView().getTopInventory().equals(m.inv)) return;
        m.onClick(e);
    }
}
