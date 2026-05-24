package com.prwatech.skillama.script;

import com.prwatech.common.configuration.PasswordEncode;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import com.prwatech.skillama.util.IndiaTime;
import java.util.Optional;

/**
 * Script to create the first OWNER user if none exists.
 * 
 * This script runs on application startup and checks if any OWNER users exist.
 * If no OWNER exists, it creates a default OWNER user.
 * 
 * Configuration:
 * - Set AUTO_CREATE_FIRST_OWNER to true to enable automatic creation
 * - Set FIRST_OWNER_EMAIL to the email address for the first owner
 * - Set FIRST_OWNER_PASSWORD to the password for the first owner
 * 
 * Security Note: After creating the first owner, change the password immediately!
 */
@Component
@AllArgsConstructor
public class FirstOwnerSetupScript implements CommandLineRunner {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(FirstOwnerSetupScript.class);
    
    private final SkillamaUserRepository userRepository;
    private final PasswordEncode passwordEncode;
    
    // Set to true to enable automatic first owner creation
    private static final boolean AUTO_CREATE_FIRST_OWNER = false;
    
    // First owner configuration
    private static final String FIRST_OWNER_EMAIL = "owner@prwatech.com";
    private static final String FIRST_OWNER_PASSWORD = "ChangeThisPassword123!";
    private static final String FIRST_OWNER_NAME = "System Owner";
    
    @Override
    public void run(String... args) throws Exception {
        if (!AUTO_CREATE_FIRST_OWNER) {
            LOGGER.info("First owner setup script is disabled. Use MongoDB or API to create first owner.");
            return;
        }
        
        LOGGER.info("Checking for existing OWNER users...");
        createFirstOwnerIfNeeded();
    }
    
    /**
     * Creates the first OWNER user if no OWNER users exist in the system.
     */
    public void createFirstOwnerIfNeeded() {
        try {
            // Check if any OWNER exists
            boolean ownerExists = userRepository.findAll().stream()
                .anyMatch(user -> user.getRole() == User.UserRole.OWNER);
            
            if (ownerExists) {
                LOGGER.info("OWNER user(s) already exist. Skipping first owner creation.");
                return;
            }
            
            // Check if user with email already exists
            Optional<User> existingUser = userRepository.findByEmail(FIRST_OWNER_EMAIL);
            if (existingUser.isPresent()) {
                User user = existingUser.get();
                if (user.getRole() != User.UserRole.OWNER) {
                    // Promote existing user to OWNER
                    LOGGER.info("Promoting existing user {} to OWNER role", FIRST_OWNER_EMAIL);
                    user.setRole(User.UserRole.OWNER);
                    user.setUpdatedAt(IndiaTime.now());
                    userRepository.save(user);
                    LOGGER.warn("================================================");
                    LOGGER.warn("FIRST OWNER CREATED: {}", FIRST_OWNER_EMAIL);
                    LOGGER.warn("PASSWORD: {}", FIRST_OWNER_PASSWORD);
                    LOGGER.warn("IMPORTANT: Change this password immediately!");
                    LOGGER.warn("================================================");
                    return;
                } else {
                    LOGGER.info("OWNER user already exists with email: {}", FIRST_OWNER_EMAIL);
                    return;
                }
            }
            
            // Create new OWNER user
            LOGGER.info("No OWNER users found. Creating first OWNER user...");
            
            User firstOwner = new User();
            firstOwner.setName(FIRST_OWNER_NAME);
            firstOwner.setEmail(FIRST_OWNER_EMAIL);
            firstOwner.setPassword(passwordEncode.getEncryptedPassword(FIRST_OWNER_PASSWORD));
            firstOwner.setRole(User.UserRole.OWNER);
            firstOwner.setActive(true);
            firstOwner.setCreatedAt(IndiaTime.now());
            firstOwner.setUpdatedAt(IndiaTime.now());
            firstOwner.setCreatedBy("SYSTEM");
            firstOwner.setUpdatedBy("SYSTEM");
            
            userRepository.save(firstOwner);
            
            LOGGER.warn("================================================");
            LOGGER.warn("FIRST OWNER CREATED SUCCESSFULLY!");
            LOGGER.warn("Email: {}", FIRST_OWNER_EMAIL);
            LOGGER.warn("Password: {}", FIRST_OWNER_PASSWORD);
            LOGGER.warn("IMPORTANT: Change this password immediately!");
            LOGGER.warn("================================================");
            
        } catch (Exception e) {
            LOGGER.error("Error creating first owner: {}", e.getMessage(), e);
        }
    }
}

