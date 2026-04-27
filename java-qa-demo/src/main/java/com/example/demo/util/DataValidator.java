package com.example.demo.util;

public class DataValidator {

    // S3776: cognitive complexity > 15 due to deeply nested if-else chain
    public boolean validateOrder(String userId, String productId, int quantity, double price,
                                 String currency, String paymentMethod, String deliveryAddress) {
        if (userId == null || userId.isEmpty()) {
            return false;
        } else {
            if (productId == null || productId.isEmpty()) {
                return false;
            } else {
                if (quantity <= 0) {
                    return false;
                } else {
                    if (quantity > 9999) {       // S109: magic number
                        return false;
                    } else {
                        if (price < 0) {
                            return false;
                        } else {
                            if (price > 99999.99) { // S109: magic number
                                return false;
                            } else {
                                if (currency == null) {
                                    return false;
                                } else if (!currency.equals("USD") && !currency.equals("EUR")
                                        && !currency.equals("GBP") && !currency.equals("JPY")) {
                                    return false;
                                } else {
                                    if (paymentMethod == null) {
                                        return false;
                                    } else if (paymentMethod.equals("CASH")
                                            || paymentMethod.equals("CARD")
                                            || paymentMethod.equals("PAYPAL")
                                            || paymentMethod.equals("CRYPTO")) {
                                        if (deliveryAddress == null || deliveryAddress.isEmpty()) {
                                            return false;
                                        } else if (deliveryAddress.length() < 10) { // S109
                                            return false;
                                        } else {
                                            return true;
                                        }
                                    } else {
                                        return false;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // S1541: method has too many return statements (cognitive complexity)
    public String validateUser(String username, String email, String phone) {
        if (username == null)               return "username_null";
        if (username.isEmpty())             return "username_empty";
        if (username.length() < 3)          return "username_too_short";  // S109
        if (username.length() > 50)         return "username_too_long";   // S109
        if (!username.matches("[a-zA-Z0-9_]+")) return "username_invalid_chars";
        if (email == null)                  return "email_null";
        if (email.isEmpty())                return "email_empty";
        if (!email.contains("@"))           return "email_no_at";
        if (!email.contains("."))           return "email_no_dot";
        if (email.length() > 100)           return "email_too_long";      // S109
        if (phone == null)                  return "phone_null";
        if (phone.length() < 7)             return "phone_too_short";     // S109
        if (phone.length() > 15)            return "phone_too_long";      // S109
        if (!phone.matches("[0-9+\\-() ]+")) return "phone_invalid_chars";
        return "valid";
    }
}
