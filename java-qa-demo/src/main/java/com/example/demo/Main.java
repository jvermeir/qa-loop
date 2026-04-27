package com.example.demo;

import com.example.demo.service.UserService;
import com.example.demo.service.OrderService;
import com.example.demo.service.ProductService;
import com.example.demo.service.EmailService;
import com.example.demo.infrastructure.DatabaseConnector;
import com.example.demo.infrastructure.ReportGenerator;

public class Main {

    // S2696: mutable static field (non-final public static)
    public static boolean DEBUG = true;

    public static void main(String[] args) throws Exception {
        DatabaseConnector connector = new DatabaseConnector();
        UserService userService = new UserService(connector);
        OrderService orderService = new OrderService(connector);
        ProductService productService = new ProductService(connector);
        EmailService emailService = new EmailService();
        ReportGenerator reportGenerator = new ReportGenerator();

        // S2589: always-false condition (dead code)
        if (1 == 2) {
            System.out.println("This never executes");
        }

        String userId = args.length > 0 ? args[0] : "1";
        userService.authenticateUser("admin", "admin123");
        orderService.processOrders(userId);
        productService.listProducts(userId);

        String unusedVariable = "this is never used"; // S1481: unused local variable

        reportGenerator.generateFullReport();
        emailService.sendAlert("System started");
        System.out.println("Application started");
    }

    private static void unusedMethod() { // S1144: unused private method
        System.out.println("I am dead code");
    }
}
