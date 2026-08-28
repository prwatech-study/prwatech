package com.prwatech.skillama.controller;

import com.prwatech.authentication.security.JwtUtils;
import com.prwatech.common.Constants;
import com.prwatech.skillama.dto.UserPublicDTO;
import com.prwatech.skillama.exception.SkillamaAuthException;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.service.AdminService;
import com.prwatech.skillama.service.FreemiumService;
import com.prwatech.skillama.service.OtpService;
import com.prwatech.skillama.service.PasswordResetService;
import com.prwatech.skillama.service.UserContactService;
import com.prwatech.skillama.service.OAuthAuthService;
import com.prwatech.skillama.service.OnboardingService;
import com.prwatech.skillama.service.DemoAccessService;
import com.prwatech.skillama.service.SkillamaAuthSupport;
import com.prwatech.skillama.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerSecurityTest {

    private MockMvc mockMvc;

    @Mock private UserService userService;
    @Mock private AdminService adminService;
    @Mock private JwtUtils jwtUtils;
    @Mock private OtpService otpService;
    @Mock private FreemiumService freemiumService;
    @Mock private PasswordResetService passwordResetService;
    @Mock private UserContactService userContactService;
    @Mock private OAuthAuthService oAuthAuthService;
    @Mock private OnboardingService onboardingService;
    @Mock private SkillamaAuthSupport skillamaAuthSupport;
    @Mock private DemoAccessService demoAccessService;

    private static final String TOKEN = "Bearer valid.jwt.token";

    @BeforeEach
    void setUp() {
        UserController controller = new UserController(
                userService,
                adminService,
                jwtUtils,
                otpService,
                freemiumService,
                passwordResetService,
                userContactService,
                oAuthAuthService,
                onboardingService,
                skillamaAuthSupport,
                demoAccessService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    @Test
    void getSession_withoutAuth_returns401() throws Exception {
        when(skillamaAuthSupport.resolveUserIdFromRequest(any()))
                .thenThrow(new SkillamaAuthException("Session expired. Please sign in again."));
        mockMvc.perform(get("/skillama/users/session"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getSession_withAuth_returnsLightweightSession() throws Exception {
        User user = User.builder()
                .id("u1")
                .name("Learner")
                .email("learner@skillama.co.in")
                .password("hash")
                .role(User.UserRole.USER)
                .active(true)
                .build();

        when(skillamaAuthSupport.resolveUserIdFromRequest(any())).thenReturn("u1");
        when(userService.findById("u1")).thenReturn(Optional.of(user));

        mockMvc.perform(get("/skillama/users/session").header(Constants.AUTH, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("u1"))
                .andExpect(jsonPath("$.email").value("learner@skillama.co.in"));
    }

    @Test
    void getUserById_otherUserAsRegularUser_returns403() throws Exception {
        User requester = User.builder()
                .id("u1")
                .email("a@skillama.co.in")
                .password("hash")
                .role(User.UserRole.USER)
                .active(true)
                .build();

        when(skillamaAuthSupport.resolveUserIdFromRequest(any())).thenReturn("u1");
        when(userService.findById("u1")).thenReturn(Optional.of(requester));

        mockMvc.perform(get("/skillama/users/u2").header(Constants.AUTH, TOKEN))
                .andExpect(status().isForbidden());
    }

    @Test
    void getUserById_self_returnsPublicDto() throws Exception {
        User requester = User.builder()
                .id("u1")
                .name("Self")
                .email("self@skillama.co.in")
                .password("hash")
                .role(User.UserRole.USER)
                .active(true)
                .build();

        when(skillamaAuthSupport.resolveUserIdFromRequest(any())).thenReturn("u1");
        when(userService.findById("u1")).thenReturn(Optional.of(requester));

        mockMvc.perform(get("/skillama/users/u1").header(Constants.AUTH, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("u1"))
                .andExpect(jsonPath("$.name").value("Self"));
    }

    @Test
    void getAllUsers_withoutAuth_returns401() throws Exception {
        when(skillamaAuthSupport.resolveUserIdFromRequest(any()))
                .thenThrow(new SkillamaAuthException("Session expired. Please sign in again."));
        mockMvc.perform(get("/skillama/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getAllUsers_asAdmin_returnsPaginatedPublicDtos() throws Exception {
        User admin = User.builder()
                .id("admin1")
                .email("admin@skillama.co.in")
                .password("hash")
                .role(User.UserRole.ADMIN)
                .active(true)
                .build();
        User listed = User.builder()
                .id("u9")
                .name("Listed")
                .email("listed@skillama.co.in")
                .password("hash2")
                .role(User.UserRole.USER)
                .active(true)
                .build();

        when(skillamaAuthSupport.resolveUserIdFromRequest(any())).thenReturn("admin1");
        when(userService.findById("admin1")).thenReturn(Optional.of(admin));
        when(userService.findAll(anyInt(), anyInt(), anyString(), anyBoolean()))
                .thenReturn(new PageImpl<>(List.of(listed)));

        mockMvc.perform(get("/skillama/users").header(Constants.AUTH, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("u9"));
    }
}
