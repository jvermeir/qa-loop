package com.example.demo.repository;

import com.example.demo.infrastructure.DatabaseConnector;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ProductRepository {

    private DatabaseConnector connector;

    public ProductRepository(DatabaseConnector connector) {
        this.connector = connector;
    }

    // S2077: SQL injection
    public List<String> findProductsByCategory(String category) {
        List<String> products = new ArrayList<>();
        try {
            Connection conn = connector.getConnection();
            Statement stmt = conn.createStatement();
            String sql = "SELECT * FROM products WHERE category = '" + category + "'";
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                products.add(rs.getString("product_name"));
            }
        } catch (SQLException e) {
            // swallowed
        }
        return products;
    }

    // S2259: returns null — callers risk NPE
    public String findProductById(String productId) {
        try {
            Connection conn = connector.getConnection();
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * FROM products WHERE id = " + productId);
            if (rs.next()) {
                return rs.getString("product_name");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void deleteProduct(String productId) {
        try {
            Connection conn = connector.getConnection();
            Statement stmt = conn.createStatement();
            stmt.execute("DELETE FROM products WHERE id = " + productId); // S2077
            logAuditEntry("DELETE_PRODUCT", productId);
        } catch (SQLException e) {
            // swallowed
        }
    }

    // ── DUPLICATED in UserRepository and OrderRepository ─────────────────────
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
