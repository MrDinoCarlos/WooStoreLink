package com.nocticraft.woostorelink.utils;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DeliveryManager {

    private final MySQLManager db;

    public DeliveryManager(MySQLManager db) {
        this.db = db;
    }

    public List<String> getPendingDeliveries(String playerName) {
        List<String> deliveries = new ArrayList<>();

        try (Connection conn = db.getConnection()) {
            PreparedStatement stmt = conn.prepareStatement(
                    "SELECT id, item, amount, delivered FROM pending_deliveries WHERE player = ?"
            );
            stmt.setString(1, playerName);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                String item = rs.getString("item");
                int amount = rs.getInt("amount");
                boolean delivered = rs.getBoolean("delivered");

                String status = delivered ? "YES" : "NO";
                deliveries.add(item + " x" + amount + " | Delivered: " + status);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return deliveries;
    }

    public void markAsDelivered(int id) {
        try (Connection conn = db.getConnection()) {
            PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE pending_deliveries SET delivered = 1 WHERE id = ?"
            );
            stmt.setInt(1, id);
            stmt.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
