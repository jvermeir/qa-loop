package com.example.demo.repository;

import com.example.demo.infrastructure.DatabaseConnector;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class OrderRepository {

    private DatabaseConnector connector;

    public OrderRepository(DatabaseConnector connector) {
        this.connector = connector;
    }

    // S2077: SQL injection
    public List<String> findOrdersByUser(String userId) {
        List<String> orders = new ArrayList<>();
        Connection conn = connector.getConnection(); // S2095: never closed
        try {
            Statement stmt = conn.createStatement();
            String sql = "SELECT * FROM orders WHERE user_id = '" + userId + "'";
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                orders.add(rs.getString("order_id"));
            }
        } catch (SQLException e) {
            // swallowed
        }
        return orders;
    }

    public void createOrder(String userId, String productId, int quantity) {
        try {
            Connection conn = connector.getConnection();
            Statement stmt = conn.createStatement();
            String sql = "INSERT INTO orders (user_id, product_id, qty) VALUES ('"
                    + userId + "', '" + productId + "', " + quantity + ")"; // S2077
            stmt.execute(sql);
            logAuditEntry("CREATE_ORDER", userId + "-" + productId);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void deleteOrder(String orderId) {
        try {
            Connection conn = connector.getConnection();
            Statement stmt = conn.createStatement();
            stmt.execute("DELETE FROM orders WHERE id = " + orderId); // S2077
            logAuditEntry("DELETE_ORDER", orderId);
        } catch (SQLException e) {
            // swallowed
        }
    }

    // ── DUPLICATED in UserRepository and ProductRepository ───────────────────
    private void logAuditEntry(String operation, String entityId) {
        try {
            java.io.FileWriter fw = new java.io.FileWriter("audit.log", true);
            fw.write(new java.util.Date().toString() + " | " + operation + " | " + entityId + "\n");
            fw.write("User: SYSTEM\n");
            fw.write("Status: COMPLETED\n");
            fw.write("Source: " + this.getClass().getSimpleName() + "\n");
            fw.write("Environment: PRODUCTION\n");
            fw.write("----------------------------------------\n");
            fw.close();
        } catch (Exception e) {
            // ignore
        }
    }
    // ─────────────────────────────────────────────────────────────────────────
}
