package com.nocticraft.woostorelink.utils;

public class Delivery {

    // Coinciden EXACTO con la API
    private String id;
    private String item;
    private String amount;

    // Campos opcionales (la API NO los manda)
    private Integer order_id;
    private Boolean delivered;

    /* ===== GETTERS ===== */

    public int getId() {
        try {
            return Integer.parseInt(id);
        } catch (Exception e) {
            return 0;
        }
    }

    public String getItem() {
        return item == null ? "" : item;
    }

    public int getAmount() {
        try {
            return Integer.parseInt(amount);
        } catch (Exception e) {
            return 1;
        }
    }

    // Compatibilidad con código existente
    public int getOrderId() {
        return order_id == null ? 0 : order_id;
    }

    public boolean isDelivered() {
        return delivered != null && delivered;
    }

    /* ===== SETTERS (opcionales, Gson no los necesita) ===== */

    public void setId(String id) {
        this.id = id;
    }

    public void setItem(String item) {
        this.item = item;
    }

    public void setAmount(String amount) {
        this.amount = amount;
    }

    public void setOrderId(Integer order_id) {
        this.order_id = order_id;
    }

    public void setDelivered(Boolean delivered) {
        this.delivered = delivered;
    }
}
