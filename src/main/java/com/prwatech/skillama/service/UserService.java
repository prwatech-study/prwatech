package com.prwatech.skillama.service;

import com.prwatech.common.configuration.AppContext;
import com.prwatech.common.configuration.PasswordEncode;
import com.prwatech.common.dto.EmailSendDto;
import com.prwatech.common.service.impl.EmailServiceImpl;
import com.prwatech.skillama.SkillamaNotificationEmails;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.model.UserLoginEvent;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import com.prwatech.skillama.repository.UserLoginEventRepository;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@AllArgsConstructor
public class UserService {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserService.class);
    
    private final SkillamaUserRepository userRepository;
    private final UserLoginEventRepository userLoginEventRepository;
    private final UserCourseAccessService userCourseAccessService;
    private final EmailServiceImpl emailService;
    private final AppContext appContext;
    private final PasswordEncode passwordEncode;

    public User register(User user) {
        // Encode password before storing in database
        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            String encodedPassword = passwordEncode.getEncryptedPassword(user.getPassword());
            user.setPassword(encodedPassword);
        }
        user.setActive(false);
        user.setActivationKey(generateActivationKey());
        user.setCreatedAt(LocalDateTime.now());
        User savedUser = userRepository.save(user);
        
        // Send notification emails to admins
        sendRegistrationNotificationEmail(savedUser);
        
        return savedUser;
    }
    
    private void sendRegistrationNotificationEmail(User user) {
        try {
            String baseUrl = appContext.getSkillamaBaseUrl();
            String activationUrl = baseUrl + "/skillama/users/admin/activate?email=" 
                    + URLEncoder.encode(user.getEmail(), StandardCharsets.UTF_8);
            
            String emailMessage = "A new user has registered on Skillama.\n\n"
                    + "User Details:\n"
                    + "Name: " + (user.getName() != null ? user.getName() : "N/A") + "\n"
                    + "Email: " + user.getEmail() + "\n"
                    + "Gender: " + (user.getGender() != null ? user.getGender() : "N/A") + "\n\n"
                    + "Please activate the user using the following URL:\n"
                    + activationUrl;
            
            String subject = "New User Registration - Activation Required";

            for (String teamEmail : SkillamaNotificationEmails.TEAM_INBOXES) {
                emailService.sendEmail(new EmailSendDto(teamEmail, subject, emailMessage));
            }
            
            LOGGER.info("Registration notification emails sent for user: {}", user.getEmail());
        } catch (Exception e) {
            LOGGER.error("Failed to send registration notification email for user: {}", user.getEmail(), e);
            // Don't throw exception - registration should succeed even if email fails
        }
    }
    
    private String generateActivationKey() {
        return UUID.randomUUID().toString();
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public Optional<User> findById(String id) {
        return userRepository.findById(id);
    }

    public Page<User> findAll(int page, int size, String sortBy, boolean desc) {
        Pageable pageable = PageRequest.of(page, size, desc ? Sort.Direction.DESC : Sort.Direction.ASC, sortBy);
        return userRepository.findAll(pageable);
    }
    
    public User activateUser(String email) {
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            user.setActive(true);
            user.setActivationKey(null); // Clear activation key once activated
            return userRepository.save(user);
        }
        return null;
    }
    
    public User deactivateUser(String email) {
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            user.setActive(false);
            user.setActivationKey(generateActivationKey()); // Generate new activation key
            return userRepository.save(user);
        }
        return null;
    }
    
    /**
     * Validates user password using encoded password comparison
     */
    public boolean validatePassword(String plainPassword, String encodedPassword) {
        return passwordEncode.compare(plainPassword, encodedPassword);
    }
    
    /**
     * Encodes password for a user and saves it
     * Used for migration script to encode existing passwords
     */
    public User encodeAndUpdatePassword(User user, String plainPassword) {
        String encodedPassword = passwordEncode.getEncryptedPassword(plainPassword);
        user.setPassword(encodedPassword);
        return userRepository.save(user);
    }
    
    /**
     * Migrates all existing user passwords to encoded format
     * This method checks if a password is already encoded, and if not, encodes it
     * @return Map with migration statistics
     */
    public Map<String, Object> migrateAllPasswords() {
        Map<String, Object> result = new HashMap<>();
        int totalUsers = 0;
        int encodedCount = 0;
        int skippedCount = 0;
        int errorCount = 0;
        
        try {
            List<User> allUsers = userRepository.findAll();
            totalUsers = allUsers.size();
            
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
                            continue;
                        } catch (IllegalArgumentException e) {
                            // Not valid Base64, encode it
                        }
                    }
                    
                    // Password is plain text, encode it
                    String encodedPassword = passwordEncode.getEncryptedPassword(user.getPassword());
                    user.setPassword(encodedPassword);
                    userRepository.save(user);
                    encodedCount++;
                    
                } catch (Exception e) {
                    LOGGER.error("Error encoding password for user: {}", user.getEmail(), e);
                    errorCount++;
                }
            }
            
            result.put("status", "success");
            result.put("totalUsers", totalUsers);
            result.put("passwordsEncoded", encodedCount);
            result.put("alreadyEncoded", skippedCount);
            result.put("errors", errorCount);
            result.put("message", String.format(
                "Migration completed: %d encoded, %d already encoded, %d errors",
                encodedCount, skippedCount, errorCount));
            
        } catch (Exception e) {
            LOGGER.error("Error during password migration", e);
            result.put("status", "error");
            result.put("message", "Migration failed: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Checks if a string is Base64 encoded
     */
    private boolean isBase64Encoded(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        // Base64 strings typically don't contain spaces and have specific character set
        // A simple heuristic: check if it contains only Base64 characters
        return str.matches("^[A-Za-z0-9+/=]+$") && str.length() % 4 == 0;
    }
    
    /**
     * Saves a user entity
     */
    public User save(User user) {
        return userRepository.save(user);
    }

    public void recordLogin(User user) {
        LocalDateTime now = LocalDateTime.now();
        user.setLastLoginAt(now);
        int count = user.getLoginCount() != null ? user.getLoginCount() : 0;
        user.setLoginCount(count + 1);
        user.setUpdatedAt(now);
        userRepository.save(user);

        userLoginEventRepository.save(UserLoginEvent.builder()
                .userId(user.getId())
                .loggedInAt(now)
                .build());

        if (user.getRole() == null || user.getRole() == User.UserRole.USER) {
            userCourseAccessService.ensureDefaultFreemiumEnrollment(user.getId());
        }
    }
}
