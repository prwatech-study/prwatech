package com.prwatech.skillama.security;

import com.prwatech.common.configuration.SecurityConfig;
import com.prwatech.skillama.exception.SkillamaAuthException;
import com.prwatech.skillama.service.SkillamaAuthSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SkillamaSecurityFilterChainTest {

    private AnnotationConfigWebApplicationContext context;
    private MockMvc mockMvc;
    private SkillamaAuthSupport authSupport;

    @BeforeEach
    void setUp() {
        authSupport = mock(SkillamaAuthSupport.class);
        FilterChainTestConfig.authSupport = authSupport;

        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(FilterChainTestConfig.class);
        context.refresh();

        FilterChainProxy springSecurityFilterChain = context.getBean(FilterChainProxy.class);
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(springSecurityFilterChain)
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        if (context != null) {
            context.close();
        }
        reset(authSupport);
    }

    @Test
    void publicLoginIsNotUnauthorized() throws Exception {
        mockMvc.perform(post("/skillama/users/login"))
                .andExpect(status().isOk());
        verify(authSupport, never()).resolveSessionFromRequest(any());
    }

    @Test
    void sessionWithoutJwtReturns401() throws Exception {
        when(authSupport.resolveSessionFromRequest(any()))
                .thenThrow(new SkillamaAuthException("Session expired. Please sign in again."));
        mockMvc.perform(get("/skillama/users/session"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("WWW-Authenticate"));
    }

    @Test
    void sessionWithValidJwtIsAllowed() throws Exception {
        when(authSupport.resolveSessionFromRequest(any()))
                .thenReturn(new SkillamaAuthSupport.ResolvedSession("user-1", 1));
        mockMvc.perform(get("/skillama/users/session")
                        .header("Authorization", "Bearer valid.jwt"))
                .andExpect(status().isOk());
    }

    @Test
    void invalidJwtReturns401() throws Exception {
        when(authSupport.resolveSessionFromRequest(any()))
                .thenThrow(new SkillamaAuthException("Session expired. Please sign in again."));
        mockMvc.perform(get("/skillama/users/session")
                        .header("Authorization", "Bearer expired"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void guestInitAndAccessControlAreAllowed() throws Exception {
        mockMvc.perform(post("/skillama/user-profile/guest/init"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/skillama/user-profile/access-control"))
                .andExpect(status().isOk());
        verify(authSupport, never()).resolveSessionFromRequest(any());
    }

    @Test
    void guestCannotReachSession() throws Exception {
        when(authSupport.resolveSessionFromRequest(any()))
                .thenThrow(new SkillamaAuthException("Session expired. Please sign in again."));
        mockMvc.perform(get("/skillama/users/session"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void postCoursesWithoutJwtReturns401() throws Exception {
        when(authSupport.resolveSessionFromRequest(any()))
                .thenThrow(new SkillamaAuthException("Session expired. Please sign in again."));
        mockMvc.perform(post("/skillama/courses"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void migratePasswordsWithoutJwtReturns401() throws Exception {
        when(authSupport.resolveSessionFromRequest(any()))
                .thenThrow(new SkillamaAuthException("Session expired. Please sign in again."));
        mockMvc.perform(post("/skillama/users/admin/migrate-passwords"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void internalRecordDoesNotRequireJwt() throws Exception {
        mockMvc.perform(post("/skillama/internal/ai-usage/record"))
                .andExpect(status().isOk());
        verify(authSupport, never()).resolveSessionFromRequest(any());
    }

    @Test
    void listedOriginIsAllowedOnPreflight() throws Exception {
        mockMvc.perform(options("/skillama/users/login")
                        .header("Origin", "https://skillama.co.in")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://skillama.co.in"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void unknownAndSpoofedOriginsAreRejected() throws Exception {
        for (String origin : List.of(
                "https://evil.com",
                "https://skillama.co.in.attacker.com",
                "https://notskillama.co.in",
                "https://foo.bar.skillama.co.in")) {
            mockMvc.perform(options("/skillama/users/login")
                            .header("Origin", origin)
                            .header("Access-Control-Request-Method", "POST"))
                    .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        }
    }

    @Test
    void legacyApiRemainsReachable() throws Exception {
        mockMvc.perform(get("/api/v1/ping")).andExpect(status().isOk());
    }

    @Test
    void unknownPathIsDenied() throws Exception {
        mockMvc.perform(get("/not-mapped")).andExpect(status().isUnauthorized());
    }

    @Configuration
    @EnableWebMvc
    @Import(SecurityConfig.class)
    static class FilterChainTestConfig {
        static SkillamaAuthSupport authSupport;

        @Bean
        SkillamaAuthSupport skillamaAuthSupport() {
            return authSupport;
        }

        @Bean
        SkillamaJwtAuthenticationFilter skillamaJwtAuthenticationFilter(SkillamaAuthSupport support) {
            return new SkillamaJwtAuthenticationFilter(support);
        }

        @Bean
        CorsConfigurationSource corsConfigurationSource() {
            List<String> allowed = SkillamaCorsOrigins.parse(SkillamaCorsOrigins.DEFAULT);
            return request -> SkillamaCorsConfiguration.corsFor(request, allowed);
        }

        @RestController
        static class ProbeController {
            @PostMapping("/skillama/users/login")
            Map<String, Boolean> login() {
                return Map.of("ok", true);
            }

            @GetMapping("/skillama/users/session")
            Map<String, Boolean> session() {
                return Map.of("ok", true);
            }

            @PostMapping("/skillama/user-profile/guest/init")
            Map<String, Boolean> guestInit() {
                return Map.of("ok", true);
            }

            @GetMapping("/skillama/user-profile/access-control")
            Map<String, Boolean> accessControl() {
                return Map.of("ok", true);
            }

            @PostMapping("/skillama/courses")
            Map<String, Boolean> createCourse() {
                return Map.of("ok", true);
            }

            @PostMapping("/skillama/users/admin/migrate-passwords")
            Map<String, Boolean> migrate() {
                return Map.of("ok", true);
            }

            @PostMapping("/skillama/internal/ai-usage/record")
            Map<String, Boolean> internalRecord() {
                return Map.of("ok", true);
            }

            @GetMapping("/api/v1/ping")
            Map<String, Boolean> legacy() {
                return Map.of("ok", true);
            }
        }
    }
}
