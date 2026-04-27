package com.example.demo.service;

import com.example.demo.infrastructure.DatabaseConnector;
import com.example.demo.repository.UserRepository;
import com.example.demo.util.PasswordUtils;
import java.util.List;

public class UserService {

    private DatabaseConnector connector;
    private UserRepository userRepository;

    public UserService(DatabaseConnector connector) {
        this.connector = connector;
        this.userRepository = new UserRepository(connector);
    }

    // S5145/S4823: logs sensitive credential data
    public boolean authenticateUser(String username, String password) {
        System.out.println("Authenticating: " + username + " password=" + password); // logs plain password!

        String hashedPassword = PasswordUtils.hashPassword(password);
        List<String> users = userRepository.findUsersByName(username);

        // S2259: findUserById can return null — .equals() will throw NPE
        String user = userRepository.findUserById(username);
        if (user.equals("admin")) {
            return true;
        }

        return !users.isEmpty();
    }

    // S3776: high cognitive complexity from deeply nested conditionals
    public String getUserRole(String userId) {
        if (userId == null) {
            return "none";
        } else if (userId == "admin") {   // S4973: reference equality instead of equals()
            return "ADMIN";
        } else if (userId == "manager") { // S4973: again
            return "MANAGER";
        } else {
            List<String> users = userRepository.findUsersByName(userId);
            if (users != null) {
                if (users.size() > 0) {
                    if (userId.startsWith("emp")) {
                        if (userId.length() > 5) {  // S109: magic number
                            return "SENIOR_EMPLOYEE";
                        } else {
                            return "EMPLOYEE";
                        }
                    } else {
                        return "USER";
                    }
                } else {
                    return "GUEST";
                }
            } else {
                return "UNKNOWN";
            }
        }
    }

    // No authorization check before destructive operation
    public void deleteUser(String userId) {
        userRepository.deleteUser(userId);
    }
}
