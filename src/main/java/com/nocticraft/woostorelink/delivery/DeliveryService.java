package com.nocticraft.woostorelink.delivery;

import com.nocticraft.woostorelink.WooStoreLink;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.*;
import java.util.*;

public class DeliveryService {
    private final WooStoreLink plugin;
    private final Map<UUID, List<ItemStack>> queue = new HashMap<>();
    private final File store;

    public DeliveryService(WooStoreLink plugin) {
        this.plugin = plugin;
        this.store = new File(plugin.getDataFolder(), "pending_deliveries.dat");
        load();
        Bukkit.getScheduler().runTaskTimer(plugin, this::tryDeliverAllOnline, 20L*10, 20L*20);
    }

    public int countPending(UUID id) { return queue.getOrDefault(id, List.of()).size(); }
    public List<ItemStack> getQueue(UUID id) { return new ArrayList<>(queue.getOrDefault(id, List.of())); }

    public boolean tryDeliverNow(Player p, ItemStack item) {
        if (hasSpace(p)) {
            p.getInventory().addItem(item);
            return true;
        }
        // inventario lleno → cola + mensajes desde lang
        enqueue(p.getUniqueId(), item);
        var lang = plugin.getLang();
        p.sendMessage(color(lang.getOrDefault("queue-added-1",
                "&eYour inventory is full. &7The item has been added to your delivery queue.")));
        p.sendMessage(color(lang.getOrDefault("queue-added-2",
                "&7Please free up space or open &e/wsl menu &7→ Deliveries to claim it.")));
        return false;
    }


    // Reclamar desde la cola SIN re-encolar
    public boolean claimFromQueue(Player p, int index) {
        List<ItemStack> list = queue.get(p.getUniqueId());
        if (list == null || index < 0 || index >= list.size()) return false;

        ItemStack is = list.get(index);
        if (hasSpace(p)) {
            p.getInventory().addItem(is);
            list.remove(index);
            if (list.isEmpty()) queue.remove(p.getUniqueId());
            save();

            // Mensaje de confirmación desde lang
            String itemName = is.getType().name().toLowerCase().replace("_", " ");
            var lang = plugin.getLang();
            p.sendMessage(color(lang.getOrDefault("queue-claim-success",
                            "&a✅ You have successfully claimed your queued delivery: &e%item%&a.")
                    .replace("%item%", itemName)));

            return true;
        } else {
            var lang = plugin.getLang();
            p.sendMessage(color(lang.getOrDefault("queue-claim-full",
                    "&eYour inventory is still full. &7Free up space, then try again from &e/wsl menu &7→ Deliveries.")));
            return false;
        }
    }

    public void tryDeliverPlayer(Player p) {
        List<ItemStack> list = queue.get(p.getUniqueId());
        if (list == null || list.isEmpty()) return;
        Iterator<ItemStack> it = list.iterator();
        while (it.hasNext()) {
            ItemStack is = it.next();
            if (hasSpace(p)) {
                p.getInventory().addItem(is);
                it.remove();
            } else break;
        }
        if (list.isEmpty()) queue.remove(p.getUniqueId());
        save();
    }

    private boolean hasSpace(Player p) { return p.getInventory().firstEmpty() != -1; }

    private void enqueue(UUID id, ItemStack is) {
        queue.computeIfAbsent(id, k -> new ArrayList<>()).add(is);
        save();
    }

    private void tryDeliverAllOnline() { for (Player p : Bukkit.getOnlinePlayers()) tryDeliverPlayer(p); }

    private String color(String s) { return s.replace("&", "§"); }


    private void save() {
        try {
            store.getParentFile().mkdirs();
            try (ObjectOutputStream oos = new BukkitObjectOutputStream(new FileOutputStream(store))) {
                oos.writeInt(queue.size());
                for (var e : queue.entrySet()) {
                    oos.writeObject(e.getKey());
                    oos.writeInt(e.getValue().size());
                    for (ItemStack is : e.getValue()) oos.writeObject(is);
                }
            }
        } catch (IOException ex) { plugin.getLogger().warning("Failed to save deliveries: " + ex.getMessage()); }
    }

    private void load() {
        if (!store.exists()) return;
        try (ObjectInputStream ois = new BukkitObjectInputStream(new FileInputStream(store))) {
            int size = ois.readInt();
            for (int i=0;i<size;i++) {
                UUID id = (UUID) ois.readObject();
                int n = ois.readInt();
                List<ItemStack> list = new ArrayList<>();
                for (int j=0;j<n;j++) list.add((ItemStack) ois.readObject());
                queue.put(id, list);
            }
        } catch (Exception ex) { plugin.getLogger().warning("Failed to load deliveries: " + ex.getMessage()); }
    }
}
