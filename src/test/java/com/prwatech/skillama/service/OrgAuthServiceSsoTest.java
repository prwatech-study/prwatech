package com.prwatech.skillama.service;

import com.prwatech.authentication.security.JwtUtils;
import com.prwatech.common.configuration.PasswordEncode;
import com.prwatech.skillama.dto.OrgAuthGoogleRequestDTO;
import com.prwatech.skillama.dto.OrgAuthLoginRequestDTO;
import com.prwatech.skillama.dto.OrgAuthMicrosoftRequestDTO;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.OrgRole;
import com.prwatech.skillama.model.Organization;
import com.prwatech.skillama.model.OrganizationSecurity;
import com.prwatech.skillama.model.OrganizationStatus;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.OrganizationRepository;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The org SSO login path is the front door for corporate tenants. Each guard here is the
 * only thing standing between a valid Google/Microsoft token and access to someone else's
 * tenant, so all of them are pinned: org status, feature entitlement, email domain,
 * hosted-domain / tenant binding, and JIT provisioning.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrgAuthServiceSsoTest {

    private static final String ORG_ID = "org-a";
    private static final String SLUG = "acme";

    @Mock private OrganizationRepository organizationRepository;
    @Mock private SkillamaUserRepository userRepository;
    @Mock private UserService userService;
    @Mock private JwtUtils jwtUtils;
    @Mock private OrgFeatureService orgFeatureService;
    @Mock private OAuthAuthService oAuthAuthService;
    @Mock private PasswordEncode passwordEncode;

    @InjectMocks private OrgAuthService orgAuthService;

    private Organization org(OrganizationStatus status, OrganizationSecurity security) {
        return Organization.builder()
                .id(ORG_ID)
                .slug(SLUG)
                .name("Acme Corp")
                .contactEmail("lnd@acme.com")
                .status(status)
                .security(security)
                .build();
    }

    private OrganizationSecurity security(boolean requireSso, boolean jit, String... domains) {
        return OrganizationSecurity.builder()
                .allowedEmailDomains(List.of(domains))
                .requireSso(requireSso)
                .jitProvisioning(jit)
                .build();
    }

    private void givenOrg(Organization organization) {
        when(organizationRepository.findBySlug(SLUG)).thenReturn(Optional.of(organization));
    }

    private void givenGoogleSsoEnabled() {
        when(orgFeatureService.isEnabled(ORG_ID, "sso_google_workspace")).thenReturn(true);
    }

    private void givenGoogleClaims(String email, String hostedDomain) {
        when(oAuthAuthService.parseGoogleIdToken("token"))
                .thenReturn(new OAuthAuthService.GoogleIdTokenClaims(
                        "google-sub", email, "Jane", null, hostedDomain));
    }

    private void givenMicrosoftClaims(String email, String tenantId) {
        when(orgFeatureService.isEnabled(ORG_ID, "sso_microsoft_entra")).thenReturn(true);
        when(oAuthAuthService.parseMicrosoftIdToken("token"))
                .thenReturn(new OAuthAuthService.MicrosoftIdTokenClaims(
                        "ms-sub", email, "Jane", tenantId));
    }

    private OrgAuthGoogleRequestDTO googleRequest() {
        return OrgAuthGoogleRequestDTO.builder().orgSlug(SLUG).idToken("token").forceLogin(true).build();
    }

    private OrgAuthMicrosoftRequestDTO microsoftRequest() {
        return OrgAuthMicrosoftRequestDTO.builder().orgSlug(SLUG).idToken("token").forceLogin(true).build();
    }

    private User member(String orgId, boolean active) {
        return User.builder()
                .id("u1")
                .name("Jane")
                .email("jane@acme.com")
                .organizationId(orgId)
                .orgRole(OrgRole.LEARNER)
                .role(User.UserRole.USER)
                .active(active)
                .build();
    }

    private void givenSessionIssuable() {
        when(userService.startNewSession(anyString())).thenReturn(1);
        when(jwtUtils.generateSkillamaToken(any(), anyInt(), anyString(), any(), anyString()))
                .thenReturn(Map.of("accessToken", "at", "refreshToken", "rt"));
    }

    // --- org resolution -------------------------------------------------------

    @Test
    void login_unknownSlugIsNotFound() {
        when(organizationRepository.findBySlug("ghost")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> orgAuthService.login(
                OrgAuthLoginRequestDTO.builder().orgSlug("ghost").email("a@b.com").password("p").build()));
    }

    @Test
    void login_blankSlugIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> orgAuthService.login(
                OrgAuthLoginRequestDTO.builder().orgSlug("  ").email("a@b.com").password("p").build()));
    }

    @Test
    void loginWithGoogle_suspendedOrgIsBlockedWithContactEmail() {
        givenOrg(org(OrganizationStatus.SUSPENDED, security(false, true, "acme.com")));

        IllegalStateException ex = assertThrows(
                IllegalStateException.class, () -> orgAuthService.loginWithGoogle(googleRequest()));

        assertTrue(ex.getMessage().contains("suspended"));
        assertTrue(ex.getMessage().contains("lnd@acme.com"));
    }

    // --- password login vs requireSso ----------------------------------------

    @Test
    void login_requireSsoBlocksPasswordLogin() {
        givenOrg(org(OrganizationStatus.ACTIVE, security(true, false, "acme.com")));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> orgAuthService.login(
                OrgAuthLoginRequestDTO.builder().orgSlug(SLUG).email("jane@acme.com").password("p").build()));

        assertTrue(ex.getMessage().contains("requires SSO"));
        verify(userService, never()).validatePassword(anyString(), anyString());
    }

    @Test
    void login_disabledPasswordFeatureBlocksLogin() {
        givenOrg(org(OrganizationStatus.ACTIVE, security(false, false, "acme.com")));
        when(orgFeatureService.isEnabled(ORG_ID, "email_password_auth")).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> orgAuthService.login(
                OrgAuthLoginRequestDTO.builder().orgSlug(SLUG).email("jane@acme.com").password("p").build()));
    }

    @Test
    void login_unknownEmailSaysAccountNotFound() {
        givenOrg(org(OrganizationStatus.ACTIVE, security(false, false, "acme.com")));
        when(orgFeatureService.isEnabled(ORG_ID, "email_password_auth")).thenReturn(true);
        when(userService.findByEmail("ghost@acme.com")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> orgAuthService.login(
                OrgAuthLoginRequestDTO.builder().orgSlug(SLUG).email("ghost@acme.com").password("p").build()));

        assertEquals("No account found for this email.", ex.getMessage());
        verify(userService, never()).validatePassword(anyString(), anyString());
    }

    @Test
    void login_wrongPasswordSaysWrongPassword() {
        givenOrg(org(OrganizationStatus.ACTIVE, security(false, false, "acme.com")));
        when(orgFeatureService.isEnabled(ORG_ID, "email_password_auth")).thenReturn(true);
        User user = User.builder()
                .email("jane@acme.com")
                .password("hashed")
                .organizationId(ORG_ID)
                .active(true)
                .build();
        when(userService.findByEmail("jane@acme.com")).thenReturn(Optional.of(user));
        when(userService.validatePassword("bad", "hashed")).thenReturn(false);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> orgAuthService.login(
                OrgAuthLoginRequestDTO.builder().orgSlug(SLUG).email("jane@acme.com").password("bad").build()));

        assertEquals("Wrong password.", ex.getMessage());
    }

    // --- feature entitlement --------------------------------------------------

    @Test
    void loginWithGoogle_requiresGoogleSsoEntitlement() {
        givenOrg(org(OrganizationStatus.ACTIVE, security(false, true, "acme.com")));
        when(orgFeatureService.isEnabled(ORG_ID, "sso_google_workspace")).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> orgAuthService.loginWithGoogle(googleRequest()));
        verify(oAuthAuthService, never()).parseGoogleIdToken(anyString());
    }

    @Test
    void loginWithMicrosoft_requiresMicrosoftSsoEntitlement() {
        givenOrg(org(OrganizationStatus.ACTIVE, security(false, true, "acme.com")));
        when(orgFeatureService.isEnabled(ORG_ID, "sso_microsoft_entra")).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> orgAuthService.loginWithMicrosoft(microsoftRequest()));
        verify(oAuthAuthService, never()).parseMicrosoftIdToken(anyString());
    }

    // --- email domain allowlist ----------------------------------------------

    @Test
    void loginWithGoogle_rejectsEmailOutsideAllowedDomains() {
        givenOrg(org(OrganizationStatus.ACTIVE, security(false, true, "acme.com")));
        givenGoogleSsoEnabled();
        givenGoogleClaims("attacker@gmail.com", "gmail.com");

        IllegalStateException ex = assertThrows(
                IllegalStateException.class, () -> orgAuthService.loginWithGoogle(googleRequest()));

        assertTrue(ex.getMessage().contains("domain is not allowed"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void loginWithGoogle_allowedDomainComparisonIsCaseInsensitive() {
        givenOrg(org(OrganizationStatus.ACTIVE, security(false, false, "ACME.com")));
        givenGoogleSsoEnabled();
        givenGoogleClaims("jane@Acme.COM", null);
        when(userRepository.findByEmail("jane@acme.com")).thenReturn(Optional.of(member(ORG_ID, true)));
        givenSessionIssuable();

        Map<String, Object> result = orgAuthService.loginWithGoogle(googleRequest());

        assertEquals("success", result.get("status"));
    }

    @Test
    void loginWithGoogle_emptyAllowlistPermitsAnyDomain() {
        givenOrg(org(OrganizationStatus.ACTIVE, security(false, false)));
        givenGoogleSsoEnabled();
        givenGoogleClaims("jane@partner.com", null);
        when(userRepository.findByEmail("jane@partner.com")).thenReturn(Optional.of(member(ORG_ID, true)));
        givenSessionIssuable();

        assertEquals("success", orgAuthService.loginWithGoogle(googleRequest()).get("status"));
    }

    @Test
    void loginWithGoogle_missingEmailIsRejected() {
        givenOrg(org(OrganizationStatus.ACTIVE, security(false, true, "acme.com")));
        givenGoogleSsoEnabled();
        givenGoogleClaims("  ", "acme.com");

        assertThrows(IllegalArgumentException.class, () -> orgAuthService.loginWithGoogle(googleRequest()));
    }

    // --- hosted domain / tenant binding --------------------------------------

    @Test
    void loginWithGoogle_rejectsMismatchedHostedDomain() {
        givenOrg(org(OrganizationStatus.ACTIVE, security(false, true, "acme.com")));
        givenGoogleSsoEnabled();
        givenGoogleClaims("jane@acme.com", "other.com");
        when(orgFeatureService.getConfig(ORG_ID, "sso_google_workspace"))
                .thenReturn(Map.of("hostedDomain", "acme.com"));

        IllegalStateException ex = assertThrows(
                IllegalStateException.class, () -> orgAuthService.loginWithGoogle(googleRequest()));

        assertTrue(ex.getMessage().contains("organization workspace"));
    }

    @Test
    void loginWithGoogle_rejectsMissingHostedDomainWhenConfigured() {
        givenOrg(org(OrganizationStatus.ACTIVE, security(false, true, "acme.com")));
        givenGoogleSsoEnabled();
        givenGoogleClaims("jane@acme.com", null);
        when(orgFeatureService.getConfig(ORG_ID, "sso_google_workspace"))
                .thenReturn(Map.of("hostedDomain", "acme.com"));

        assertThrows(IllegalStateException.class, () -> orgAuthService.loginWithGoogle(googleRequest()));
    }

    @Test
    void loginWithGoogle_unconfiguredHostedDomainSkipsTheCheck() {
        givenOrg(org(OrganizationStatus.ACTIVE, security(false, false, "acme.com")));
        givenGoogleSsoEnabled();
        givenGoogleClaims("jane@acme.com", null);
        when(orgFeatureService.getConfig(ORG_ID, "sso_google_workspace")).thenReturn(null);
        when(userRepository.findByEmail("jane@acme.com")).thenReturn(Optional.of(member(ORG_ID, true)));
        givenSessionIssuable();

        assertEquals("success", orgAuthService.loginWithGoogle(googleRequest()).get("status"));
    }

    @Test
    void loginWithMicrosoft_rejectsMismatchedTenant() {
        givenOrg(org(OrganizationStatus.ACTIVE, security(false, true, "acme.com")));
        givenMicrosoftClaims("jane@acme.com", "tenant-b");
        when(orgFeatureService.getConfig(ORG_ID, "sso_microsoft_entra"))
                .thenReturn(Map.of("tenantId", "tenant-a"));

        IllegalStateException ex = assertThrows(
                IllegalStateException.class, () -> orgAuthService.loginWithMicrosoft(microsoftRequest()));

        assertTrue(ex.getMessage().contains("organization tenant"));
    }

    @Test
    void loginWithMicrosoft_acceptsMatchingTenant() {
        givenOrg(org(OrganizationStatus.ACTIVE, security(false, false, "acme.com")));
        givenMicrosoftClaims("jane@acme.com", "tenant-a");
        when(orgFeatureService.getConfig(ORG_ID, "sso_microsoft_entra"))
                .thenReturn(Map.of("tenantId", "tenant-a"));
        when(userRepository.findByEmail("jane@acme.com")).thenReturn(Optional.of(member(ORG_ID, true)));
        givenSessionIssuable();

        assertEquals("success", orgAuthService.loginWithMicrosoft(microsoftRequest()).get("status"));
    }

    // --- cross-tenant account binding ----------------------------------------

    @Test
    void loginWithGoogle_rejectsUserBelongingToAnotherOrg() {
        givenOrg(org(OrganizationStatus.ACTIVE, security(false, true, "acme.com")));
        givenGoogleSsoEnabled();
        givenGoogleClaims("jane@acme.com", null);
        when(userRepository.findByEmail("jane@acme.com")).thenReturn(Optional.of(member("org-b", true)));

        IllegalStateException ex = assertThrows(
                IllegalStateException.class, () -> orgAuthService.loginWithGoogle(googleRequest()));

        assertTrue(ex.getMessage().contains("does not belong to this organization"));
    }

    @Test
    void loginWithGoogle_rejectsDeactivatedUser() {
        givenOrg(org(OrganizationStatus.ACTIVE, security(false, true, "acme.com")));
        givenGoogleSsoEnabled();
        givenGoogleClaims("jane@acme.com", null);
        when(userRepository.findByEmail("jane@acme.com")).thenReturn(Optional.of(member(ORG_ID, false)));

        IllegalStateException ex = assertThrows(
                IllegalStateException.class, () -> orgAuthService.loginWithGoogle(googleRequest()));

        assertTrue(ex.getMessage().contains("not active"));
    }

    // --- JIT provisioning -----------------------------------------------------

    @Test
    void loginWithGoogle_unknownUserRejectedWhenJitDisabled() {
        givenOrg(org(OrganizationStatus.ACTIVE, security(false, false, "acme.com")));
        givenGoogleSsoEnabled();
        givenGoogleClaims("newbie@acme.com", null);
        when(userRepository.findByEmail("newbie@acme.com")).thenReturn(Optional.empty());

        IllegalStateException ex = assertThrows(
                IllegalStateException.class, () -> orgAuthService.loginWithGoogle(googleRequest()));

        assertTrue(ex.getMessage().contains("not been provisioned"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void loginWithGoogle_jitCreatesLearnerInCallingOrg() {
        givenOrg(org(OrganizationStatus.ACTIVE, security(false, true, "acme.com")));
        givenGoogleSsoEnabled();
        givenGoogleClaims("newbie@acme.com", null);
        when(userRepository.findByEmail("newbie@acme.com")).thenReturn(Optional.empty());
        when(userRepository.countByOrganizationIdAndActiveTrue(ORG_ID)).thenReturn(3L);
        when(orgFeatureService.getMaxSeats(ORG_ID)).thenReturn(50);
        when(passwordEncode.getEncryptedPassword(anyString())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User saved = inv.getArgument(0);
            saved.setId("new-1");
            return saved;
        });
        givenSessionIssuable();

        Map<String, Object> result = orgAuthService.loginWithGoogle(googleRequest());

        assertEquals("success", result.get("status"));
        assertEquals(ORG_ID, result.get("organizationId"));
        assertEquals(OrgRole.LEARNER.name(), result.get("orgRole"));
    }

    @Test
    void loginWithGoogle_jitBlockedAtSeatLimit() {
        givenOrg(org(OrganizationStatus.ACTIVE, security(false, true, "acme.com")));
        givenGoogleSsoEnabled();
        givenGoogleClaims("newbie@acme.com", null);
        when(userRepository.findByEmail("newbie@acme.com")).thenReturn(Optional.empty());
        when(userRepository.countByOrganizationIdAndActiveTrue(ORG_ID)).thenReturn(50L);
        when(orgFeatureService.getMaxSeats(ORG_ID)).thenReturn(50);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class, () -> orgAuthService.loginWithGoogle(googleRequest()));

        assertTrue(ex.getMessage().contains("seat limit"));
        verify(userRepository, never()).save(any());
    }

    // --- session conflict -----------------------------------------------------

    @Test
    void loginWithGoogle_activeSessionWithoutForceReturnsConflict() {
        givenOrg(org(OrganizationStatus.ACTIVE, security(false, false, "acme.com")));
        givenGoogleSsoEnabled();
        givenGoogleClaims("jane@acme.com", null);
        User existing = member(ORG_ID, true);
        existing.setSessionActive(true);
        when(userRepository.findByEmail("jane@acme.com")).thenReturn(Optional.of(existing));

        Map<String, Object> result = orgAuthService.loginWithGoogle(
                OrgAuthGoogleRequestDTO.builder().orgSlug(SLUG).idToken("token").forceLogin(false).build());

        assertEquals("conflict", result.get("status"));
        verify(userService, never()).startNewSession(anyString());
    }

    @Test
    void loginWithGoogle_issuedTokenCarriesOrgClaims() {
        givenOrg(org(OrganizationStatus.ACTIVE, security(false, false, "acme.com")));
        givenGoogleSsoEnabled();
        givenGoogleClaims("jane@acme.com", null);
        when(userRepository.findByEmail("jane@acme.com")).thenReturn(Optional.of(member(ORG_ID, true)));
        givenSessionIssuable();

        orgAuthService.loginWithGoogle(googleRequest());

        verify(jwtUtils).generateSkillamaToken(
                any(), anyInt(), eq(ORG_ID), eq(OrgRole.LEARNER.name()), eq(SLUG));
    }
}
