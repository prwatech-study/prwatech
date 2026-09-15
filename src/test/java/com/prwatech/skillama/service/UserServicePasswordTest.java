package com.prwatech.skillama.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prwatech.common.configuration.AppContext;
import com.prwatech.common.configuration.PasswordEncode;
import com.prwatech.common.service.impl.EmailServiceImpl;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import com.prwatech.skillama.repository.UserLoginEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServicePasswordTest {

    private static final String PLAIN = "correct-horse-battery";

    @Mock private SkillamaUserRepository userRepository;
    @Mock private UserLoginEventRepository userLoginEventRepository;
    @Mock private EmailServiceImpl emailService;
    @Mock private AppContext appContext;
    @Mock private NotificationSettingsService notificationSettingsService;
    @Mock private UserContactService userContactService;
    @Mock private MongoTemplate skillamaMongoTemplate;

    private PasswordEncode passwordEncode;
    private UserService userService;

    @BeforeEach
    void setUp() {
        AppContext saltContext = org.mockito.Mockito.mock(AppContext.class);
        when(saltContext.getSalt()).thenReturn("$2a$04$HYunSfuYwLxf8CrqhW7QHO");
        passwordEncode = new PasswordEncode(saltContext);
        userService = new UserService(
                userRepository,
                userLoginEventRepository,
                emailService,
                appContext,
                passwordEncode,
                notificationSettingsService,
                userContactService,
                skillamaMongoTemplate);
        org.mockito.Mockito.lenient().when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void register_storesBcryptAndNeverBase64() {
        User incoming = User.builder()
                .email("new@skillama.co.in")
                .name("New Learner")
                .password(PLAIN)
                .build();

        User saved = userService.register(incoming);

        assertTrue(passwordEncode.isBcryptHash(saved.getPassword()));
        assertFalse(passwordEncode.isLegacyEncoded(saved.getPassword()));
        String base64 = Base64.getEncoder().encodeToString(PLAIN.getBytes(StandardCharsets.UTF_8));
        assertFalse(saved.getPassword().equals(base64));
        assertFalse(saved.getPassword().equals(PLAIN));
        assertTrue(userService.validatePassword(PLAIN, saved.getPassword()));
    }

    @Test
    void validatePassword_acceptsCorrectBcrypt() {
        String hash = passwordEncode.getEncryptedPassword(PLAIN);
        assertTrue(userService.validatePassword(PLAIN, hash));
    }

    @Test
    void validatePassword_rejectsIncorrectPassword() {
        String hash = passwordEncode.getEncryptedPassword(PLAIN);
        assertFalse(userService.validatePassword("wrong-password", hash));
    }

    @Test
    void validatePassword_acceptsLegacyBase64() {
        String legacy = Base64.getEncoder().encodeToString(PLAIN.getBytes(StandardCharsets.UTF_8));
        assertTrue(userService.validatePassword(PLAIN, legacy));
        assertFalse(userService.validatePassword("wrong-password", legacy));
    }

    @Test
    void upgradeLegacyPasswordIfNeeded_replacesBase64WithBcryptOnSuccessfulMatch() {
        String legacy = Base64.getEncoder().encodeToString(PLAIN.getBytes(StandardCharsets.UTF_8));
        User user = User.builder().id("u-legacy").email("legacy@skillama.co.in").password(legacy).build();

        assertTrue(userService.validatePassword(PLAIN, user.getPassword()));
        userService.upgradeLegacyPasswordIfNeeded(user, PLAIN);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        String stored = captor.getValue().getPassword();
        assertTrue(passwordEncode.isBcryptHash(stored));
        assertFalse(passwordEncode.isLegacyEncoded(stored));
        assertFalse(stored.equals(legacy));
        assertTrue(userService.validatePassword(PLAIN, stored));
    }

    @Test
    void upgradedUser_canLogInAgainWithBcryptHash() {
        String legacy = Base64.getEncoder().encodeToString(PLAIN.getBytes(StandardCharsets.UTF_8));
        User user = User.builder().id("u-legacy").password(legacy).build();

        userService.upgradeLegacyPasswordIfNeeded(user, PLAIN);

        assertTrue(passwordEncode.isBcryptHash(user.getPassword()));
        assertTrue(userService.validatePassword(PLAIN, user.getPassword()));
        userService.upgradeLegacyPasswordIfNeeded(user, PLAIN);
        verify(userRepository, org.mockito.Mockito.times(1)).save(any(User.class));
    }

    @Test
    void upgradeLegacyPasswordIfNeeded_doesNotRewriteOnWrongPassword() {
        String legacy = Base64.getEncoder().encodeToString(PLAIN.getBytes(StandardCharsets.UTF_8));
        User user = User.builder().id("u-legacy").password(legacy).build();

        userService.upgradeLegacyPasswordIfNeeded(user, "wrong-password");

        assertEquals(legacy, user.getPassword());
        verify(userRepository, never()).save(any());
    }

    @Test
    void migrateAllPasswords_upgradesLegacyAndSkipsBcrypt() {
        String legacy = Base64.getEncoder().encodeToString(PLAIN.getBytes(StandardCharsets.UTF_8));
        String bcrypt = passwordEncode.getEncryptedPassword("other-secret");
        User legacyUser = User.builder().id("u1").password(legacy).build();
        User bcryptUser = User.builder().id("u2").password(bcrypt).build();
        when(userRepository.findAll()).thenReturn(List.of(legacyUser, bcryptUser));

        Map<String, Object> result = userService.migrateAllPasswords();

        assertEquals("success", result.get("status"));
        assertEquals(1, result.get("passwordsEncoded"));
        assertEquals(1, result.get("alreadyEncoded"));
        assertTrue(passwordEncode.isBcryptHash(legacyUser.getPassword()));
        assertEquals(bcrypt, bcryptUser.getPassword());
        assertFalse(String.valueOf(result).contains(PLAIN));
        assertFalse(String.valueOf(result).contains(legacy));
    }

    @Test
    void registerAndUpgrade_doNotLogPlaintextOrDecodedPassword() {
        Logger logger = (Logger) LoggerFactory.getLogger(UserService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            userService.register(User.builder().email("n@x.com").password(PLAIN).build());
            String legacy = Base64.getEncoder().encodeToString(PLAIN.getBytes(StandardCharsets.UTF_8));
            userService.upgradeLegacyPasswordIfNeeded(
                    User.builder().id("u1").email("n@x.com").password(legacy).build(), PLAIN);

            for (ILoggingEvent event : appender.list) {
                String message = event.getFormattedMessage();
                assertFalse(message.contains(PLAIN), "must not log plaintext password");
                assertFalse(message.contains(legacy), "must not log decoded/encoded password");
            }
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void userJacksonSerialization_omitsPassword() throws Exception {
        User user = User.builder()
                .id("u1")
                .email("learner@skillama.co.in")
                .password(passwordEncode.getEncryptedPassword(PLAIN))
                .build();
        String json = new ObjectMapper().writeValueAsString(user);
        assertFalse(json.contains("password"));
        assertFalse(json.contains(user.getPassword()));
        assertFalse(json.contains(PLAIN));
    }
}
