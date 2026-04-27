package com.example.demo.repository;

import com.example.demo.infrastructure.DatabaseConnector;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class UserRepository {

    private DatabaseConnector connector;

    public UserRepository(DatabaseConnector connector) {
        this.connector = connector;
    }

    // S2077: SQL injection via string concatenation
    public List<String> findUsersByName(String name) {
        List<String> users = new ArrayList<>();
        try {
            Connection conn = connector.getConnection();
            Statement stmt = conn.createStatement(); // S2095: resource leak
            String sql = "SELECT * FROM users WHERE name = '" + name + "'";
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                users.add(rs.getString("name"));
            }
        } catch (SQLException e) {
            // S1166: swallowed
        }
        return users;
    }

    // S2077: SQL injection; S2259: returns null — callers risk NPE
    public String findUserById(String userId) {
        try {
            Connection conn = connector.getConnection();
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * FROM users WHERE id = " + userId);
            if (rs.next()) {
                return rs.getString("username");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void deleteUser(String userId) {
        try {
            Connection conn = connector.getConnection();
            Statement stmt = conn.createStatement();
            stmt.execute("DELETE FROM users WHERE id = " + userId); // S2077
            logAuditEntry("DELETE_USER", userId);
        } catch (SQLException e) {
            // swallowed
        }
    }

    // ── DUPLICATED in OrderRepository and ProductRepository ──────────────────
    private void logAuditEntry(String operation, String entityId) {
        try {
            java.io.FileWriter fw = new java.io.FileWriter("audit.log", true);
            fw.write(new java.util.Date().toString() + " | " + operation + " | " + entityId + "\n");
            fw.write("User: SYSTEM\n");
            fw.write("Status: COMPLETED\n");
            fw.write("Source: " + this.getClass().getSimpleName() + "\n");
            fw.write("Environment: PRODUCTION\n");
            fw.write("----------------------------------------\n");
            fw.close(); // S2095: not in try-with-resources
        } catch (Exception e) {
            // ignore
        }
    }
    // ─────────────────────────────────────────────────────────────────────────
}
