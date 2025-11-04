package com.nocticraft.woostorelink.utils.menu;

import com.nocticraft.woostorelink.WooStoreLink;
import com.nocticraft.woostorelink.utils.Achievement;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class AchievementsMenu extends Menu {
    private final WooStoreLink plugin;
    private int page = 0;
    private List<Achievement> all;

    public AchievementsMenu(WooStoreLink plugin, Player viewer) {
        super(viewer);
        this.plugin = plugin;
        this.rows = 6;
        this.all = plugin.getAchievementManager() != null ? plugin.getAchievementManager().getAll() : java.util.List.of();
    }

    @Override public String title() { return "§6Achievements"; }

    @Override public void draw() {
        inv.clear();
        inv.setItem(0, ItemBuilder.icon(Material.ARROW, "§6Back", List.of("§7Return to Profile")));
        inv.setItem(8, ItemBuilder.icon(Material.BARRIER, "§cExit", List.of("§7Close")));

        int start = page * 28;
        int purchases = 0;
        try { purchases = plugin.getAchievementManager().getPurchases(viewer.getUniqueId()); } catch (Throwable ignored) {}

        for (int i=0; i<28 && start+i < all.size(); i++) {
            Achievement a = all.get(start+i);
            boolean unlocked = plugin.getAchievementManager().isUnlocked(viewer.getUniqueId(), a.getId());
            int need = Math.max(0, a.getCount() - purchases);
            String bar = progressBar(Math.min(purchases, a.getCount()), a.getCount());

            ItemStack icon = new ItemStack(unlocked ? Material.EMERALD : Material.REDSTONE);
            inv.setItem(slotForIndex(i), ItemBuilder.icon(
                    icon.getType(),
                    (unlocked ? "§a" : "§c") + a.getTitle(),
                    List.of(
                            "§7Requirement: §f" + a.getCount() + " " + plugin.getAchievementManager().getCounterName(),
                            "§7Progress: §f" + purchases + "/" + a.getCount() + " " + bar,
                            unlocked ? "§aUnlocked!" : "§cMissing: §f" + need
                    )));
        }

        inv.setItem(45, ItemBuilder.icon(Material.ARROW, "§6Prev", List.of()));
        inv.setItem(53, ItemBuilder.icon(Material.ARROW, "§6Next", List.of()));
    }

    private String progressBar(int done, int all) {
        int width = 12, fill = all == 0 ? 0 : (int)Math.round((done*1.0/all)*width);
        StringBuilder sb = new StringBuilder("§7[");
        for (int i=0;i<width;i++) sb.append(i<fill? "§a▮":"§8▯");
        sb.append("§7]");
        return sb.toString();
    }

    private int slotForIndex(int i) { int row=i/7, col=i%7; return (row+1)*9 + (col+1); }

    @Override public void onClick(InventoryClickEvent e) {
        super.onClick(e);
        if (e.getSlot()==0) { new ProfileMenu(plugin, viewer).open(); return; }
        if (e.getSlot()==8) { viewer.closeInventory(); return; }
        if (e.getSlot()==45 && page>0) { page--; draw(); return; }
        if (e.getSlot()==53 && (page+1)*28 < all.size()) { page++; draw(); return; }
    }
}
