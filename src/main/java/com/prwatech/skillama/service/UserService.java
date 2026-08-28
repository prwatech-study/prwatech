package com.prwatech.skillama.service;

import com.prwatech.common.configuration.AppContext;
import com.prwatech.common.configuration.PasswordEncode;
import com.prwatech.common.dto.EmailSendDto;
import com.prwatech.common.service.impl.EmailServiceImpl;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.notification.NotificationEventType;
import com.prwatech.skillama.model.UserLoginEvent;
import com.prwatech.skillama.model.UserSession;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import com.prwatech.skillama.repository.UserLoginEventRepository;
import com.prwatech.skillama.util.IndiaTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserService.class);

    private final SkillamaUserRepository userRepository;
    private final UserLoginEventRepository userLoginEventRepository;
    private final EmailServiceImpl emailService;
    private final AppContext appContext;
    private final PasswordEncode passwordEncode;
    private final NotificationSettingsService notificationSettingsService;
    private final UserContactService userContactService;
    private final MongoTemplate skillamaMongoTemplate;

    public UserService(
            SkillamaUserRepository userRepository,
            UserLoginEventRepository userLoginEventRepository,
            EmailServiceImpl emailService,
            AppContext appContext,
            PasswordEncode passwordEncode,
            NotificationSettingsService notificationSettingsService,
            UserContactService userContactService,
            @Qualifier("skillamaMongoTemplate") MongoTemplate skillamaMongoTemplate) {
        this.userRepository = userRepository;
        this.userLoginEventRepository = userLoginEventRepository;
        this.emailService = emailService;
        this.appContext = appContext;
        this.passwordEncode = passwordEncode;
        this.notificationSettingsService = notificationSettingsService;
        this.userContactService = userContactService;
        this.skillamaMongoTemplate = skillamaMongoTemplate;
    }

    public User register(User user) {
        // Encode password before storing in database
        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            String encodedPassword = passwordEncode.getEncryptedPassword(user.getPassword());
            user.setPassword(encodedPassword);
        }
        user.setActive(false);
        user.setActivationKey(generateActivationKey());
        if (user.getRole() == null) {
            user.setRole(User.UserRole.USER);
        }
        user.setCreatedAt(IndiaTime.now());
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

            notificationSettingsService.sendTeamNotification(
                    NotificationEventType.USER_REGISTRATION, subject, emailMessage);
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

    /**
     * Resolve a user from JWT subject email (normalized + case-insensitive fallback).
     */
    public Optional<User> findByEmailForAuth(String emailFromJwt) {
        if (emailFromJwt == null || emailFromJwt.isBlank()) {
            return Optional.empty();
        }
        String trimmed = emailFromJwt.trim();
        String normalized = userContactService.normalizeEmail(trimmed);
        if (normalized != null) {
            Optional<User> byNormalized = userRepository.findByEmail(normalized);
            if (byNormalized.isPresent()) {
                return byNormalized;
            }
        }
        Optional<User> byTrimmed = userRepository.findByEmail(trimmed);
        if (byTrimmed.isPresent()) {
            return byTrimmed;
        }
        if (normalized != null) {
            return userRepository.findByEmailIgnoreCase(normalized);
        }
        return Optional.empty();
    }

    public Optional<User> findById(String id) {
        return userRepository.findById(id);
    }

    public Page<User> findAll(int page, int size, String sortBy, boolean desc) {
        Pageable pageable = PageRequest.of(page, size, desc ? Sort.Direction.DESC : Sort.Direction.ASC, sortBy);
        return userRepository.findAll(pageable);
    }
    
    /**
     * Stamps a one-time-per-account platform intro (AI-tutor intro / demo video) as seen.
     * Idempotent — the first call sets the timestamp, later calls are no-ops, and a flag
     * is never unset, so an intro can't replay once it has been shown (or skipped).
     */
    public User markPlatformIntroSeen(String userId, String flag) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if ("AI_TUTOR_INTRO".equals(flag)) {
            if (user.getAiTutorIntroSeenAt() != null) return user;
            user.setAiTutorIntroSeenAt(IndiaTime.now());
        } else if ("DEMO_VIDEO".equals(flag)) {
            if (user.getDemoVideoSeenAt() != null) return user;
            user.setDemoVideoSeenAt(IndiaTime.now());
        } else {
            throw new IllegalArgumentException("Unknown platform intro flag: " + flag);
        }
        return userRepository.save(user);
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
        LocalDateTime now = IndiaTime.now();
        user.setLastLoginAt(now);
        int count = user.getLoginCount() != null ? user.getLoginCount() : 0;
        user.setLoginCount(count + 1);
        user.setUpdatedAt(now);
        userRepository.save(user);

        userLoginEventRepository.save(UserLoginEvent.builder()
                .userId(user.getId())
                .loggedInAt(now)
                .build());
    }

    /**
     * Atomically bumps tokenVersion and marks the session active; returns the new version to
     * embed in the freshly minted JWT. findAndModify (rather than read-then-save) avoids two
     * near-simultaneous logins landing on the same version number and both staying valid.
     * Also closes out any still-open UserSession row (a prior login this one is replacing) and
     * opens a fresh one for active-time tracking.
     */
    public int startNewSession(String userId) {
        Query query = Query.query(Criteria.where("id").is(userId));
        Update update = new Update().inc("tokenVersion", 1).set("sessionActive", true);
        User updated = skillamaMongoTemplate.findAndModify(
                query, update, FindAndModifyOptions.options().returnNew(true), User.class);
        int newVersion = updated != null && updated.getTokenVersion() != null ? updated.getTokenVersion() : 0;

        LocalDateTime now = IndiaTime.now();
        closeOpenSessions(userId, "REPLACED", now);
        skillamaMongoTemplate.insert(UserSession.builder()
                .userId(userId)
                .tokenVersion(newVersion)
                .startedAt(now)
                .lastHeartbeatAt(now)
                .build());
        return newVersion;
    }

    /** Bumps tokenVersion and clears sessionActive so the logged-out token stops passing auth checks. */
    public void logout(String userId) {
        Query query = Query.query(Criteria.where("id").is(userId));
        Update update = new Update()
                .inc("tokenVersion", 1)
                .set("sessionActive", false)
                .set("updatedAt", IndiaTime.now());
        skillamaMongoTemplate.findAndModify(query, update, FindAndModifyOptions.options(), User.class);
        closeOpenSessions(userId, "LOGOUT", IndiaTime.now());
    }

    private void closeOpenSessions(String userId, String endReason, LocalDateTime endedAt) {
        Query query = Query.query(Criteria.where("userId").is(userId).and("endedAt").isNull());
        Update update = new Update().set("endedAt", endedAt).set("endReason", endReason);
        skillamaMongoTemplate.updateMulti(query, update, UserSession.class);
    }

    /**
     * Records that the current session's tab was focused and heartbeating just now. No-op
     * (returns false) if this session was already replaced/logged out — a harmless race between
     * a closing tab's last heartbeat and a login elsewhere.
     */
    public boolean recordHeartbeat(String userId, int tokenVersion) {
        Query query = Query.query(Criteria.where("userId").is(userId)
                .and("tokenVersion").is(tokenVersion)
                .and("endedAt").isNull());
        Update update = new Update().set("lastHeartbeatAt", IndiaTime.now());
        return skillamaMongoTemplate.updateFirst(query, update, UserSession.class).getModifiedCount() > 0;
    }

    /**
     * Sums active time (lastHeartbeatAt - startedAt) across sessions started in [from, to).
     * Uses lastHeartbeatAt rather than endedAt so a browser closed without calling /logout
     * doesn't inflate the total past the last moment it was genuinely open and focused.
     * Does not clamp a session's time to the [from, to) boundary — a session that starts just
     * before "to" and continues past it is counted in full, not split across periods.
     */
    public long getTotalActiveSeconds(String userId, LocalDateTime from, LocalDateTime to) {
        Query query = Query.query(Criteria.where("userId").is(userId)
                .and("startedAt").gte(from).lt(to));
        long totalSeconds = 0;
        for (UserSession session : skillamaMongoTemplate.find(query, UserSession.class)) {
            LocalDateTime end = session.getLastHeartbeatAt() != null
                    ? session.getLastHeartbeatAt() : session.getStartedAt();
            long seconds = Duration.between(session.getStartedAt(), end).getSeconds();
            if (seconds > 0) {
                totalSeconds += seconds;
            }
        }
        return totalSeconds;
    }
}
