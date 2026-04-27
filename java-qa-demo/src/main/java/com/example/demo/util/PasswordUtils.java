package com.example.demo.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

public class PasswordUtils {

    // S2068: hardcoded fixed salt
    private static final String SALT = "fixed_salt_12345";

    // S4790: MD5 is cryptographically broken — must not be used for passwords
    public static String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest((SALT + password).getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return password; // S1166: returns plaintext password on failure!
        }
    }

    // S2245: java.util.Random is not cryptographically secure
    public static String generateToken() {
        Random random = new Random(); // should be SecureRandom
        return String.valueOf(random.nextLong());
    }

    // S2647: credentials encoded without Base64 — trivially reversible
    public static String encodeBasicAuth(String user, String pass) {
        return user + ":" + pass;
    }

    // S2070: SHA-1 is also broken for security use
    public static String legacyHash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] hash = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return input;
        }
    }
}
