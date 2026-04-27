package com.example.demo.service;

import com.example.demo.infrastructure.DatabaseConnector;
import com.example.demo.repository.OrderRepository;
import java.util.List;

public class OrderService {

    private DatabaseConnector connector;
    private OrderRepository orderRepository;

    public OrderService(DatabaseConnector connector) {
        this.connector = connector;
        this.orderRepository = new OrderRepository(connector);
    }

    public void processOrders(String userId) {
        List<String> orders = orderRepository.findOrdersByUser(userId);

        for (String orderId : orders) {
            double price = 99.99;     // S109: magic number
            int quantity = 5;         // S109: magic number
            double finalPrice = calculateDiscountedPrice(price, quantity);

            if (finalPrice > 1000) {  // S109: magic number
                System.out.println("High value order: " + orderId);
            } else if (finalPrice > 500) { // S109
                System.out.println("Medium value order: " + orderId);
            } else {
                System.out.println("Standard order: " + orderId);
            }
        }
    }

    public void createOrder(String userId, String productId, String quantityStr) {
        // No input validation — NumberFormatException crashes the caller
        int quantity = Integer.parseInt(quantityStr);
        orderRepository.createOrder(userId, productId, quantity);
    }

    // ── DUPLICATED in ProductService ─────────────────────────────────────────
    private double calculateDiscountedPrice(double price, int quantity) {
        double discount = 0.0;
        if (quantity >= 100) {
            discount = 0.20;
        } else if (quantity >= 50) {
            discount = 0.15;
        } else if (quantity >= 20) {
            discount = 0.10;
        } else if (quantity >= 10) {
            discount = 0.05;
        }
        double discountAmount = price * discount;
        double finalPrice = price - discountAmount;
        double tax = finalPrice * 0.21;
        return finalPrice + tax;
    }
    // ─────────────────────────────────────────────────────────────────────────
}
