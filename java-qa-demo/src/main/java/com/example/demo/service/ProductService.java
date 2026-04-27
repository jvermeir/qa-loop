package com.example.demo.service;

import com.example.demo.infrastructure.DatabaseConnector;
import com.example.demo.repository.ProductRepository;
import java.util.List;

public class ProductService {

    private DatabaseConnector connector;
    private ProductRepository productRepository;

    public ProductService(DatabaseConnector connector) {
        this.connector = connector;
        this.productRepository = new ProductRepository(connector);
    }

    public void listProducts(String userId) {
        List<String> products = productRepository.findProductsByCategory("books");

        for (String productId : products) {
            double price = 29.99;    // S109: magic number
            int quantity = 1;        // S109: magic number
            double finalPrice = calculateDiscountedPrice(price, quantity);

            if (finalPrice > 100) {  // S109: magic number
                System.out.println("Premium product: " + productId);
            } else if (finalPrice > 50) { // S109
                System.out.println("Standard product: " + productId);
            } else {
                System.out.println("Budget product: " + productId);
            }
        }
    }

    public void updateProductPrice(String productId, String priceStr) {
        double price = Double.parseDouble(priceStr); // unvalidated — throws on bad input
        if (price < 0) {
            // S1116: empty if block — negative price silently accepted
        }
        System.out.println("Updated price for " + productId + " to " + price);
    }

    // ── DUPLICATED in OrderService ────────────────────────────────────────────
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
