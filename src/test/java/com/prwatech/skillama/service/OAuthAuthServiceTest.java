package com.prwatech.skillama.service;

import com.prwatech.common.configuration.PasswordEncode;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OAuthAuthServiceTest {

    private static final String EMAIL = "learner@example.com";

    @Mock private SkillamaUserRepository userRepository;
    @Mock private UserService userService;
    @Mock private UserContactService userContactService;
    @Mock private FreemiumService freemiumService;
    @Mock private OnboardingService onboardingService;
    @Mock private PasswordEncode passwordEncode;

    @InjectMocks private OAuthAuthService oAuthAuthService;

    @BeforeEach
    void setUp() {
        lenient().when(userContactService.normalizeEmail(EMAIL)).thenReturn(EMAIL);
        lenient().when(userRepository.findByGoogleSub(any())).thenReturn(Optional.empty());
        lenient().when(userRepository.findByAppleSub(any())).thenReturn(Optional.empty());
        lenient()
                .when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void resolveOAuthUser_linksGoogleToLegacyPasswordAccount() {
        User legacy = User.builder()
                .id("u1")
                .email(EMAIL)
                .password("encoded")
                .active(true)
                .build();
        when(userService.findByEmail(EMAIL)).thenReturn(Optional.of(legacy));

        User result = invokeResolve(
                EMAIL, "Legacy User", "pic.jpg", User.AuthProvider.GOOGLE, "google-sub-1", null);

        assertEquals("google-sub-1", result.getGoogleSub());
        assertEquals(User.AuthProvider.EMAIL, result.getAuthProvider());
        verify(userRepository).save(legacy);
    }

    @Test
    void resolveOAuthUser_linksGoogleToEmailProviderAccount() {
        User emailUser = User.builder()
                .id("u2")
                .email(EMAIL)
                .password("encoded")
                .authProvider(User.AuthProvider.EMAIL)
                .active(true)
                .build();
        when(userService.findByEmail(EMAIL)).thenReturn(Optional.of(emailUser));

        User result = invokeResolve(
                EMAIL, "Email User", null, User.AuthProvider.GOOGLE, "google-sub-2", null);

        assertEquals("google-sub-2", result.getGoogleSub());
        assertEquals(User.AuthProvider.EMAIL, result.getAuthProvider());
    }

    @Test
    void resolveOAuthUser_linksAppleToExistingGoogleAccount() {
        User googleUser = User.builder()
                .id("u3")
                .email(EMAIL)
                .authProvider(User.AuthProvider.GOOGLE)
                .googleSub("google-sub-3")
                .active(true)
                .build();
        when(userService.findByEmail(EMAIL)).thenReturn(Optional.of(googleUser));

        User result = invokeResolve(
                EMAIL, "Google User", null, User.AuthProvider.APPLE, null, "apple-sub-1");

        assertEquals("google-sub-3", result.getGoogleSub());
        assertEquals("apple-sub-1", result.getAppleSub());
    }

    @Test
    void resolveOAuthUser_rejectsConflictingGoogleSub() {
        User linked = User.builder()
                .id("u4")
                .email(EMAIL)
                .googleSub("google-sub-existing")
                .active(true)
                .build();
        when(userService.findByEmail(EMAIL)).thenReturn(Optional.of(linked));

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> invokeResolve(
                        EMAIL, "Other", null, User.AuthProvider.GOOGLE, "google-sub-other", null));

        assertTrue(ex.getMessage().toLowerCase().contains("different google"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void resolveOAuthUser_createsNewUserWhenEmailUnknown() {
        when(userService.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.empty());

        User result = invokeResolve(
                EMAIL, "New Learner", "avatar.jpg", User.AuthProvider.GOOGLE, "google-sub-new", null);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertEquals(EMAIL, saved.getEmail());
        assertEquals("google-sub-new", saved.getGoogleSub());
        assertEquals(User.AuthProvider.GOOGLE, saved.getAuthProvider());
        assertFalse(Boolean.TRUE.equals(saved.getOnboardingCompleted()));
        assertNotNull(result);
    }

    private User invokeResolve(
            String email,
            String name,
            String picture,
            User.AuthProvider provider,
            String googleSub,
            String appleSub) {
        return ReflectionTestUtils.invokeMethod(
                oAuthAuthService,
                "resolveOAuthUser",
                email,
                name,
                picture,
                provider,
                googleSub,
                appleSub);
    }
}
