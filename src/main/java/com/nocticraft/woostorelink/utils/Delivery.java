package com.nocticraft.woostorelink.utils;

public class Delivery {

    private int id;
    private int order_id;
    private String item;
    private int product_id;
    private int variation_id;
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

    public int getProductId() {
        return product_id;
    }

    public int getVariationId() {
        return variation_id;
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

    public void setProductId(int product_id) {
        this.product_id = product_id;
    }

    public void setVariationId(int variation_id) {
        this.variation_id = variation_id;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }

    public void setDelivered(boolean delivered) { // ✅ AÑADIDO
        this.delivered = delivered;
    }
}
