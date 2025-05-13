package com.nocticraft.woostorelink.utils;

public class Delivery {

    private int id;
    private int order_id;
    private String item;
    private int amount;
    private boolean delivered; // ✅ AÑADIDO

    // Getters
    public int getId() {
        return id;
    }

    public int getOrderId() {
        return order_id;
    }

    public String getItem() {
        return item;
    }

    public int getAmount() {
        return amount;
    }

    public boolean isDelivered() { // ✅ AÑADIDO
        return delivered;
    }

    // Setters
    public void setId(int id) {
        this.id = id;
    }

    public void setOrderId(int order_id) {
        this.order_id = order_id;
    }

    public void setItem(String item) {
        this.item = item;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }

    public void setDelivered(boolean delivered) { // ✅ AÑADIDO
        this.delivered = delivered;
    }
}
