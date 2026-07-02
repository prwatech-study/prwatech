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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceAuthLookupTest {

    @Mock private SkillamaUserRepository userRepository;
    @Mock private UserLoginEventRepository userLoginEventRepository;
    @Mock private EmailServiceImpl emailService;
    @Mock private AppContext appContext;
    @Mock private PasswordEncode passwordEncode;
    @Mock private NotificationSettingsService notificationSettingsService;
    @Mock private UserContactService userContactService;

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
                userContactService);
        lenient().when(userContactService.normalizeEmail(anyString()))
                .thenAnswer(inv -> {
                    String email = inv.getArgument(0);
                    return email == null ? null : email.trim().toLowerCase();
                });
    }

    @Test
    void findByEmailForAuth_matchesNormalizedEmail() {
        User user = User.builder().id("u1").email("learner@example.com").build();
        when(userRepository.findByEmail("learner@example.com")).thenReturn(Optional.of(user));

        Optional<User> found = userService.findByEmailForAuth("Learner@Example.com");

        assertTrue(found.isPresent());
        assertEquals("u1", found.get().getId());
    }

    @Test
    void findByEmailForAuth_fallsBackToIgnoreCase() {
        User user = User.builder().id("u2").email("Legacy@Example.com").build();
        lenient().when(userRepository.findByEmail("legacy@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("legacy@example.com")).thenReturn(Optional.of(user));

        Optional<User> found = userService.findByEmailForAuth("legacy@example.com");

        assertTrue(found.isPresent());
        assertEquals("u2", found.get().getId());
    }

    @Test
    void findByEmailForAuth_returnsEmptyForBlank() {
        assertTrue(userService.findByEmailForAuth(null).isEmpty());
        assertTrue(userService.findByEmailForAuth("  ").isEmpty());
    }
}
