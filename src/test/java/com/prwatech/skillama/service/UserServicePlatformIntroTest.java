package com.prwatech.skillama.service;

import com.prwatech.common.configuration.AppContext;
import com.prwatech.common.configuration.PasswordEncode;
import com.prwatech.common.service.impl.EmailServiceImpl;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import com.prwatech.skillama.repository.UserLoginEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * One-time-per-account platform intro flags: the first call stamps the timestamp,
 * later calls are no-ops (no save), and a flag can never be unset — the AI-tutor
 * intro / demo video must not replay once shown or skipped.
 */
@ExtendWith(MockitoExtension.class)
class UserServicePlatformIntroTest {

    @Mock private SkillamaUserRepository userRepository;
    @Mock private UserLoginEventRepository userLoginEventRepository;
    @Mock private EmailServiceImpl emailService;
    @Mock private AppContext appContext;
    @Mock private PasswordEncode passwordEncode;
    @Mock private NotificationSettingsService notificationSettingsService;
    @Mock private UserContactService userContactService;
    @Mock private MongoTemplate skillamaMongoTemplate;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(
                userRepository,
                userLoginEventRepository,
                emailService,
                appContext,
                passwordEncode,
                notificationSettingsService,
                userContactService,
                skillamaMongoTemplate);
    }

    @Test
    void firstCallStampsAiTutorIntroSeen() {
        User user = User.builder().id("u1").build();
        when(userRepository.findById("u1")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = userService.markPlatformIntroSeen("u1", "AI_TUTOR_INTRO");

        assertNotNull(result.getAiTutorIntroSeenAt());
        assertNull(result.getDemoVideoSeenAt());
        verify(userRepository).save(user);
    }

    @Test
    void firstCallStampsDemoVideoSeen() {
        User user = User.builder().id("u1").build();
        when(userRepository.findById("u1")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = userService.markPlatformIntroSeen("u1", "DEMO_VIDEO");

        assertNotNull(result.getDemoVideoSeenAt());
        assertNull(result.getAiTutorIntroSeenAt());
    }

    @Test
    void repeatCallIsIdempotentAndNeverOverwritesTheOriginalStamp() {
        LocalDateTime firstSeen = LocalDateTime.of(2026, 1, 1, 10, 0);
        User user = User.builder().id("u1").aiTutorIntroSeenAt(firstSeen).build();
        when(userRepository.findById("u1")).thenReturn(Optional.of(user));

        User result = userService.markPlatformIntroSeen("u1", "AI_TUTOR_INTRO");

        assertEquals(firstSeen, result.getAiTutorIntroSeenAt());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void rejectsUnknownFlag() {
        when(userRepository.findById("u1")).thenReturn(Optional.of(User.builder().id("u1").build()));
        assertThrows(IllegalArgumentException.class,
                () -> userService.markPlatformIntroSeen("u1", "SOMETHING_ELSE"));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void rejectsUnknownUser() {
        when(userRepository.findById("missing")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class,
                () -> userService.markPlatformIntroSeen("missing", "AI_TUTOR_INTRO"));
    }
}
