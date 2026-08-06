package com.prwatech.skillama.service;

import com.prwatech.authentication.security.JwtUtils;
import com.prwatech.common.Constants;
import com.prwatech.skillama.exception.SkillamaAuthException;
import com.prwatech.skillama.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.servlet.http.HttpServletRequest;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * The single-active-session choke point: a request's token carries the tokenVersion it was
 * minted with, and must be rejected once the user's stored tokenVersion has moved past it
 * (a newer login elsewhere, or an explicit logout).
 */
@ExtendWith(MockitoExtension.class)
class SkillamaAuthSupportTest {

    @Mock private JwtUtils jwtUtils;
    @Mock private UserService userService;
    @Mock private HttpServletRequest request;

    private SkillamaAuthSupport skillamaAuthSupport;

    @BeforeEach
    void setUp() {
        skillamaAuthSupport = new SkillamaAuthSupport(jwtUtils, userService);
        lenient().when(request.getHeader(Constants.AUTH)).thenReturn("Bearer some-jwt-token");
    }

    @Test
    void resolveUserIdFromRequest_matchingTokenVersion_returnsUserId() {
        User user = User.builder().id("u1").email("learner@example.com").tokenVersion(2).build();
        when(jwtUtils.extractUsername("some-jwt-token")).thenReturn("learner@example.com");
        when(jwtUtils.extractTokenVersion("some-jwt-token")).thenReturn(2);
        when(userService.findByEmailForAuth("learner@example.com")).thenReturn(Optional.of(user));

        String userId = skillamaAuthSupport.resolveUserIdFromRequest(request);

        assertEquals("u1", userId);
    }

    @Test
    void resolveUserIdFromRequest_staleTokenVersion_throwsSessionRevoked() {
        User user = User.builder().id("u1").email("learner@example.com").tokenVersion(3).build();
        when(jwtUtils.extractUsername("some-jwt-token")).thenReturn("learner@example.com");
        when(jwtUtils.extractTokenVersion("some-jwt-token")).thenReturn(2);
        when(userService.findByEmailForAuth("learner@example.com")).thenReturn(Optional.of(user));

        SkillamaAuthException ex = assertThrows(SkillamaAuthException.class,
                () -> skillamaAuthSupport.resolveUserIdFromRequest(request));

        assertEquals("SESSION_REVOKED", ex.getReason());
    }

    @Test
    void resolveUserIdFromRequest_userWithNullTokenVersion_treatedAsZero() {
        User user = User.builder().id("u1").email("legacy@example.com").tokenVersion(null).build();
        when(jwtUtils.extractUsername("some-jwt-token")).thenReturn("legacy@example.com");
        when(jwtUtils.extractTokenVersion("some-jwt-token")).thenReturn(0);
        when(userService.findByEmailForAuth("legacy@example.com")).thenReturn(Optional.of(user));

        String userId = skillamaAuthSupport.resolveUserIdFromRequest(request);

        assertEquals("u1", userId);
    }

    @Test
    void resolveUserIdFromRequest_missingBearerHeader_throwsGenericAuthFailed() {
        when(request.getHeader(Constants.AUTH)).thenReturn(null);

        SkillamaAuthException ex = assertThrows(SkillamaAuthException.class,
                () -> skillamaAuthSupport.resolveUserIdFromRequest(request));

        assertEquals("AUTH_FAILED", ex.getReason());
    }
}
