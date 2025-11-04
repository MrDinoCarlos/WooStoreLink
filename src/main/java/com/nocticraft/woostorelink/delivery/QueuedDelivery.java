package com.nocticraft.woostorelink.delivery;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.*;
import java.util.Base64;

public record QueuedDelivery(String itemB64, String reason) {
    public static QueuedDelivery fromItem(ItemStack item, String reason) {
        return new QueuedDelivery(serialize(item), reason);
    }
    public ItemStack toItem() { return deserialize(itemB64); }

    private static String serialize(ItemStack item) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             BukkitObjectOutputStream oos = new BukkitObjectOutputStream(baos)) {
            oos.writeObject(item);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (IOException e) { throw new RuntimeException(e); }
    }
    private static ItemStack deserialize(String b64) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(Base64.getDecoder().decode(b64));
             BukkitObjectInputStream ois = new BukkitObjectInputStream(bais)) {
            return (ItemStack) ois.readObject();
        } catch (IOException | ClassNotFoundException e) { throw new RuntimeException(e); }
    }
}
