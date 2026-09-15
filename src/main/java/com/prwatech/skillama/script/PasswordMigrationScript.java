package com.prwatech.skillama.script;

import com.prwatech.common.configuration.PasswordEncode;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Optional bulk migration of legacy Base64 passwords to bcrypt.
 * Disabled on startup by default; login already upgrades on successful authentication.
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
     * Migrates legacy Base64-stored passwords to bcrypt. Already-bcrypt hashes are skipped.
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
                    String stored = user.getPassword();
                    String migrated = passwordEncode.migrateStoredPassword(stored);
                    if (migrated == null || migrated.equals(stored)) {
                        skippedCount++;
                        continue;
                    }
                    user.setPassword(migrated);
                    userRepository.save(user);
                    encodedCount++;
                    LOGGER.info("Migrated password encoding for user id {}", user.getId());
                    
                } catch (Exception e) {
                    LOGGER.error("Error encoding password for user id {}", user.getId(), e);
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
}
