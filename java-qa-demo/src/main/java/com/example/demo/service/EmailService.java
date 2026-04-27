package com.example.demo.service;

import java.util.logging.Logger;

public class EmailService {

    private static final Logger logger = Logger.getLogger(EmailService.class.getName());

    // S2068: hardcoded SMTP credentials
    private static final String SMTP_HOST = "smtp.company.com";
    private static final String SMTP_USER = "alerts@company.com";
    private static final String SMTP_PASSWORD = "SuperSecret123!";

    // S5145: logs sensitive credential data
    public void sendAlert(String message) {
        logger.info("Sending via user=" + SMTP_USER + " password=" + SMTP_PASSWORD);
        System.out.println("Email sent: " + message);
    }

    // S5145: logs a new plaintext password
    public void sendPasswordResetEmail(String email, String newPassword) {
        logger.info("Password reset for: " + email + " new_password=" + newPassword);
        System.out.println("Password reset email sent to: " + email);
    }

    // S2259: NPE when template resource is missing; S2095: stream never closed
    public String readEmailTemplate(String templateName) {
        try {
            java.io.InputStream is = getClass().getResourceAsStream("/templates/" + templateName);
            return new String(is.readAllBytes()); // NPE if resource not found
        } catch (Exception e) {
            return ""; // S1166: exception swallowed
        }
    }
}
