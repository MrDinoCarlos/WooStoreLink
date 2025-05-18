package com.nocticraft.woostorelink.utils;

import java.util.HashMap;
import java.util.Map;

public class LinkManager {

    // Guarda temporalmente el email por nombre de jugador
    private final Map<String, String> pendingLinks = new HashMap<>();

    /**
     * Almacena un intento de vinculación.
     */
    public void setPendingEmail(String playerName, String email) {
        pendingLinks.put(playerName.toLowerCase(), email.toLowerCase());
    }

    /**
     * Recupera el email pendiente de verificación.
     */
    public String getPendingEmail(String playerName) {
        return pendingLinks.get(playerName.toLowerCase());
    }

    /**
     * Elimina el vínculo después de verificar.
     */
    public void clear(String playerName) {
        pendingLinks.remove(playerName.toLowerCase());
    }

    /**
     * Verifica si un jugador tiene un email pendiente.
     */
    public boolean hasPending(String playerName) {
        return pendingLinks.containsKey(playerName.toLowerCase());
    }

    //Guarda Ultimo Sync
    private final Map<String, Long> lastSync = new HashMap<>();

    public void setLastSync(String playerName, long timestamp) {
        lastSync.put(playerName.toLowerCase(), timestamp);
    }

    public long getLastSync(String playerName) {
        return lastSync.getOrDefault(playerName.toLowerCase(), 0L);
    }

}
