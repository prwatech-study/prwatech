package com.prwatech.skillama.security;

import com.prwatech.skillama.exception.SkillamaAuthException;
import com.prwatech.skillama.service.SkillamaAuthSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SkillamaJwtAuthenticationFilterTest {

    @Mock private SkillamaAuthSupport skillamaAuthSupport;

    private SkillamaJwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new SkillamaJwtAuthenticationFilter(skillamaAuthSupport);
        SecurityContextHolder.clearContext();
    }

    private static MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod(method);
        request.setRequestURI(path);
        request.setServletPath(path);
        return request;
    }

    @Test
    void publicLoginDoesNotRequireJwt() throws Exception {
        MockHttpServletRequest req = request("POST", "/skillama/users/login");
        filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());
        verify(skillamaAuthSupport, never()).resolveSessionFromRequest(any());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void guestInitDoesNotRequireJwt() throws Exception {
        MockHttpServletRequest req = request("POST", "/skillama/user-profile/guest/init");
        filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());
        verify(skillamaAuthSupport, never()).resolveSessionFromRequest(any());
    }

    @Test
    void internalRecordDoesNotRequireJwt() throws Exception {
        MockHttpServletRequest req = request("POST", "/skillama/internal/ai-usage/record");
        filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());
        verify(skillamaAuthSupport, never()).resolveSessionFromRequest(any());
    }

    @Test
    void sessionWithoutJwtReturns401() throws Exception {
        when(skillamaAuthSupport.resolveSessionFromRequest(any()))
                .thenThrow(new SkillamaAuthException("Session expired. Please sign in again."));
        MockHttpServletRequest req = request("GET", "/skillama/users/session");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(req, response, new MockFilterChain());
        assertEquals(401, response.getStatus());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void invalidJwtReturns401() throws Exception {
        when(skillamaAuthSupport.resolveSessionFromRequest(any()))
                .thenThrow(new SkillamaAuthException("Session expired. Please sign in again."));
        MockHttpServletRequest req = request("GET", "/skillama/users/session");
        req.addHeader("Authorization", "Bearer not-a-jwt");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(req, response, new MockFilterChain());
        assertEquals(401, response.getStatus());
    }

    @Test
    void validJwtSetsSecurityContext() throws Exception {
        when(skillamaAuthSupport.resolveSessionFromRequest(any()))
                .thenReturn(new SkillamaAuthSupport.ResolvedSession("user-1", 1));
        MockHttpServletRequest req = request("GET", "/skillama/users/session");
        req.addHeader("Authorization", "Bearer valid.jwt");
        filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("user-1", SecurityContextHolder.getContext().getAuthentication().getPrincipal());
    }

    @Test
    void postCoursesWithoutJwtReturns401() throws Exception {
        when(skillamaAuthSupport.resolveSessionFromRequest(any()))
                .thenThrow(new SkillamaAuthException("Session expired. Please sign in again."));
        MockHttpServletRequest req = request("POST", "/skillama/courses");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(req, response, new MockFilterChain());
        assertEquals(401, response.getStatus());
    }

    @Test
    void migratePasswordsWithoutJwtReturns401() throws Exception {
        when(skillamaAuthSupport.resolveSessionFromRequest(any()))
                .thenThrow(new SkillamaAuthException("Session expired. Please sign in again."));
        MockHttpServletRequest req = request("POST", "/skillama/users/admin/migrate-passwords");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(req, response, new MockFilterChain());
        assertEquals(401, response.getStatus());
    }

    @Test
    void getCurriculumWithoutJwtReturns401() throws Exception {
        when(skillamaAuthSupport.resolveSessionFromRequest(any()))
                .thenThrow(new SkillamaAuthException("Session expired. Please sign in again."));
        MockHttpServletRequest req = request("GET", "/skillama/curriculum/mod-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(req, response, new MockFilterChain());
        assertEquals(401, response.getStatus());
    }

    @Test
    void guestCannotReachSession() throws Exception {
        when(skillamaAuthSupport.resolveSessionFromRequest(any()))
                .thenThrow(new SkillamaAuthException("Session expired. Please sign in again."));
        MockHttpServletRequest req = request("GET", "/skillama/users/session");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(req, response, new MockFilterChain());
        assertEquals(401, response.getStatus());
    }
}
