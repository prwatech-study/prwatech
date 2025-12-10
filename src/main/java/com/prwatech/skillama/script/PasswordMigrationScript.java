package com.prwatech.skillama.script;

import com.prwatech.common.configuration.PasswordEncode;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.List;

/**
 * Migration script to encode existing user passwords
 * This can be run as a CommandLineRunner on application startup
 * Or can be disabled by commenting out @Component annotation
 * 
 * To run manually, call the /skillama/users/admin/migrate-passwords endpoint
 */
@Component
@AllArgsConstructor
public class PasswordMigrationScript implements CommandLineRunner {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(PasswordMigrationScript.class);
    
    private final SkillamaUserRepository userRepository;
    private final PasswordEncode passwordEncode;
    
    // Set to false to disable automatic migration on startup
    private static final boolean AUTO_RUN_MIGRATION = false;
    
    @Override
    public void run(String... args) throws Exception {
        if (!AUTO_RUN_MIGRATION) {
            LOGGER.info("Password migration script is disabled. Use /skillama/users/admin/migrate-passwords endpoint to run migration.");
            return;
        }
        
        LOGGER.info("Starting password migration for existing users...");
        migratePasswords();
        LOGGER.info("Password migration completed.");
    }
    
    /**
     * Migrates all existing user passwords to encoded format
     */
    public void migratePasswords() {
        try {
            List<User> allUsers = userRepository.findAll();
            int totalUsers = allUsers.size();
            int encodedCount = 0;
            int skippedCount = 0;
            int errorCount = 0;
            
            LOGGER.info("Found {} users to process", totalUsers);
            
            for (User user : allUsers) {
                if (user.getPassword() == null || user.getPassword().isEmpty()) {
                    skippedCount++;
                    continue;
                }
                
                try {
                    // Check if password is already Base64 encoded
                    if (isBase64Encoded(user.getPassword())) {
                        // Try to decode to verify it's valid Base64
                        try {
                            Base64.getDecoder().decode(user.getPassword());
                            skippedCount++; // Already encoded, skip
                            LOGGER.debug("User {} already has encoded password, skipping", user.getEmail());
                            continue;
                        } catch (IllegalArgumentException e) {
                            // Not valid Base64, encode it
                        }
                    }
                    
                    // Password is plain text, encode it
                    String originalPassword = user.getPassword();
                    String encodedPassword = passwordEncode.getEncryptedPassword(originalPassword);
                    user.setPassword(encodedPassword);
                    userRepository.save(user);
                    encodedCount++;
                    
                    LOGGER.info("Encoded password for user: {}", user.getEmail());
                    
                } catch (Exception e) {
                    LOGGER.error("Error encoding password for user: {}", user.getEmail(), e);
                    errorCount++;
                }
            }
            
            LOGGER.info("Migration completed - Total: {}, Encoded: {}, Already encoded: {}, Errors: {}", 
                    totalUsers, encodedCount, skippedCount, errorCount);
            
        } catch (Exception e) {
            LOGGER.error("Error during password migration", e);
            throw new RuntimeException("Password migration failed", e);
        }
    }
    
    /**
     * Checks if a string is Base64 encoded
     */
    private boolean isBase64Encoded(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        // Base64 strings typically don't contain spaces and have specific character set
        // A simple heuristic: check if it contains only Base64 characters and length is multiple of 4
        return str.matches("^[A-Za-z0-9+/=]+$") && str.length() % 4 == 0;
    }
}

