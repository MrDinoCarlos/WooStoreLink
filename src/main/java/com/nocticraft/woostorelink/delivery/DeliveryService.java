package com.nocticraft.woostorelink.delivery;

import com.nocticraft.woostorelink.WooStoreLink;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class DeliveryService {
    private final WooStoreLink plugin;
    private final Map<UUID, List<ItemStack>> queue = new HashMap<>();
    private final File store;
    private final ExecutorService ioExecutor;
    private final BukkitTask retryTask;
    private BukkitTask pendingSave;

    public DeliveryService(WooStoreLink plugin) {
        this.plugin = plugin;
        this.store = new File(plugin.getDataFolder(), "pending_deliveries.dat");
        this.ioExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "WooStoreLink-QueueIO");
            thread.setDaemon(true);
            return thread;
        });
        load();
        retryTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tryDeliverAllOnline, 20L * 10, 20L * 20);
    }

    public int countPending(UUID id) {
        return queue.getOrDefault(id, List.of()).size();
    }

    public List<ItemStack> getQueue(UUID id) {
        return new ArrayList<>(queue.getOrDefault(id, List.of()));
    }

    public boolean tryDeliverNow(Player player, ItemStack item) {
        if (hasSpace(player)) {
            player.getInventory().addItem(item);
            return true;
        }
        enqueue(player.getUniqueId(), item);
        var lang = plugin.getLang();
        player.sendMessage(color(lang.getOrDefault("queue-added-1",
                "&eYour inventory is full. &7The item has been added to your delivery queue.")));
        player.sendMessage(color(lang.getOrDefault("queue-added-2",
                "&7Please free up space or open &e/wsl menu &7→ Deliveries to claim it.")));
        return false;
    }

    public boolean claimFromQueue(Player player, int index) {
        List<ItemStack> list = queue.get(player.getUniqueId());
        if (list == null || index < 0 || index >= list.size()) {
            return false;
        }

        ItemStack item = list.get(index);
        if (!hasSpace(player)) {
            player.sendMessage(color(plugin.getLang().getOrDefault("queue-claim-full",
                    "&eYour inventory is still full. &7Free up space, then try again from &e/wsl menu &7→ Deliveries.")));
            return false;
        }

        player.getInventory().addItem(item);
        list.remove(index);
        if (list.isEmpty()) {
            queue.remove(player.getUniqueId());
        }
        scheduleSave();

        String itemName = item.getType().name().toLowerCase().replace("_", " ");
        player.sendMessage(color(plugin.getLang().getOrDefault("queue-claim-success",
                        "&aYou have successfully claimed your queued delivery: &e%item%&a.")
                .replace("%item%", itemName)));
        return true;
    }

    public void tryDeliverPlayer(Player player) {
        List<ItemStack> list = queue.get(player.getUniqueId());
        if (list == null || list.isEmpty()) {
            return;
        }

        boolean changed = false;
        Iterator<ItemStack> iterator = list.iterator();
        while (iterator.hasNext() && hasSpace(player)) {
            player.getInventory().addItem(iterator.next());
            iterator.remove();
            changed = true;
        }
        if (list.isEmpty()) {
            queue.remove(player.getUniqueId());
        }
        if (changed) {
            scheduleSave();
        }
    }

    public void shutdown() {
        retryTask.cancel();
        if (pendingSave != null) {
            pendingSave.cancel();
            pendingSave = null;
        }
        try {
            Map<UUID, List<ItemStack>> finalSnapshot = snapshot();
            Future<?> finalWrite = ioExecutor.submit(() -> writeSnapshot(finalSnapshot));
            finalWrite.get(10, TimeUnit.SECONDS);
        } catch (Exception exception) {
            plugin.getLogger().warning("Failed to flush queued deliveries: " + exception.getMessage());
        } finally {
            ioExecutor.shutdown();
            try {
                if (!ioExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                    ioExecutor.shutdownNow();
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                ioExecutor.shutdownNow();
            }
        }
    }

    private boolean hasSpace(Player player) {
        return player.getInventory().firstEmpty() != -1;
    }

    private void enqueue(UUID id, ItemStack item) {
        queue.computeIfAbsent(id, ignored -> new ArrayList<>()).add(item.clone());
        scheduleSave();
    }

    private void tryDeliverAllOnline() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            tryDeliverPlayer(player);
        }
    }

    private void scheduleSave() {
        if (pendingSave != null) {
            return;
        }
        pendingSave = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pendingSave = null;
            Map<UUID, List<ItemStack>> data = snapshot();
            ioExecutor.execute(() -> writeSnapshot(data));
        }, 20L);
    }

    private Map<UUID, List<ItemStack>> snapshot() {
        Map<UUID, List<ItemStack>> copy = new HashMap<>();
        queue.forEach((uuid, items) -> {
            List<ItemStack> itemCopies = new ArrayList<>(items.size());
            items.forEach(item -> itemCopies.add(item.clone()));
            copy.put(uuid, itemCopies);
        });
        return copy;
    }

    private void writeSnapshot(Map<UUID, List<ItemStack>> data) {
        File parent = store.getParentFile();
        File temporary = new File(parent, store.getName() + ".tmp");
        try {
            if (!parent.exists() && !parent.mkdirs()) {
                throw new IllegalStateException("Could not create " + parent);
            }
            try (ObjectOutputStream output = new BukkitObjectOutputStream(new FileOutputStream(temporary))) {
                output.writeInt(data.size());
                for (Map.Entry<UUID, List<ItemStack>> entry : data.entrySet()) {
                    output.writeObject(entry.getKey());
                    output.writeInt(entry.getValue().size());
                    for (ItemStack item : entry.getValue()) {
                        output.writeObject(item);
                    }
                }
            }
            try {
                Files.move(temporary.toPath(), store.toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception unsupportedAtomicMove) {
                Files.move(temporary.toPath(), store.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception exception) {
            plugin.getLogger().warning("Failed to save queued deliveries: " + exception.getMessage());
        }
    }

    private void load() {
        if (!store.exists()) {
            return;
        }
        try (ObjectInputStream input = new BukkitObjectInputStream(new FileInputStream(store))) {
            int size = input.readInt();
            for (int i = 0; i < size; i++) {
                UUID id = (UUID) input.readObject();
                int itemCount = input.readInt();
                List<ItemStack> items = new ArrayList<>();
                for (int j = 0; j < itemCount; j++) {
                    items.add((ItemStack) input.readObject());
                }
                queue.put(id, items);
            }
        } catch (Exception exception) {
            plugin.getLogger().warning("Failed to load queued deliveries: " + exception.getMessage());
        }
    }

    private String color(String message) {
        return message.replace("&", "§");
    }
}
