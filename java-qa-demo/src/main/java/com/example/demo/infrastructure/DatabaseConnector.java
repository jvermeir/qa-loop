package com.example.demo.infrastructure;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnector {

    // S2068: hardcoded credentials
    private static final String DB_URL = "jdbc:mysql://localhost:3306/bookstore";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "root123";

    // S2885: shared mutable connection — not thread-safe
    private Connection connection;

    public Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            }
        } catch (SQLException e) {
            // S1166: exception swallowed, caller receives null
        }
        return connection;
    }

    public void closeConnection() {
        try {
            connection.close(); // S2259: NPE if getConnection() failed
        } catch (Exception e) {
            // swallowed
        }
    }

    // S2696: public mutable static counter — not thread-safe
    public static int queryCount = 0;
}
