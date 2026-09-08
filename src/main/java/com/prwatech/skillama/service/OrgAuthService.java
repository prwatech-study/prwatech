package com.prwatech.skillama.service;

import com.prwatech.authentication.security.JwtUtils;
import com.prwatech.common.configuration.PasswordEncode;
import com.prwatech.common.dto.UserDetails;
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
import com.prwatech.skillama.util.IndiaTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OrgAuthService {

    private final OrganizationRepository organizationRepository;
    private final SkillamaUserRepository userRepository;
    private final UserService userService;
    private final JwtUtils jwtUtils;
    private final OrgFeatureService orgFeatureService;
    private final OAuthAuthService oAuthAuthService;
    private final PasswordEncode passwordEncode;

    @Transactional
    public Map<String, Object> login(OrgAuthLoginRequestDTO request) {
        Organization org = requireActiveOrgBySlug(request.getOrgSlug());
        assertPasswordLoginAllowed(org);
        if (request.getEmail() == null || request.getPassword() == null) {
            throw new IllegalArgumentException("orgSlug, email, and password are required");
        }
        User user = resolveOrgUserByEmail(org, request.getEmail().trim());
        if (!userService.validatePassword(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Invalid email or password");
        }
        return issueTokens(org, user, request.isForceLogin());
    }

    @Transactional
    public Map<String, Object> loginWithGoogle(OrgAuthGoogleRequestDTO request) {
        Organization org = requireActiveOrgBySlug(request.getOrgSlug());
        if (!orgFeatureService.isEnabled(org.getId(), "sso_google_workspace")) {
            throw new IllegalStateException("Google Workspace SSO is not enabled for this organization");
        }
        OAuthAuthService.GoogleIdTokenClaims claims = oAuthAuthService.parseGoogleIdToken(request.getIdToken());
        if (claims.email == null || claims.email.isBlank()) {
            throw new IllegalArgumentException("Google account email is required");
        }
        assertEmailDomainAllowed(org, claims.email);
        assertGoogleHostedDomain(org, claims.hostedDomain);
        User user = resolveOrProvisionSsoUser(org, claims.email, claims.name, claims.sub, null, User.AuthProvider.GOOGLE);
        linkGoogleSub(user, claims.sub);
        return issueTokens(org, user, request.isForceLogin());
    }

    @Transactional
    public Map<String, Object> loginWithMicrosoft(OrgAuthMicrosoftRequestDTO request) {
        Organization org = requireActiveOrgBySlug(request.getOrgSlug());
        if (!orgFeatureService.isEnabled(org.getId(), "sso_microsoft_entra")) {
            throw new IllegalStateException("Microsoft SSO is not enabled for this organization");
        }
        OAuthAuthService.MicrosoftIdTokenClaims claims = oAuthAuthService.parseMicrosoftIdToken(request.getIdToken());
        if (claims.email == null || claims.email.isBlank()) {
            throw new IllegalArgumentException("Microsoft account email is required");
        }
        assertEmailDomainAllowed(org, claims.email);
        assertMicrosoftTenant(org, claims.tenantId);
        User user = resolveOrProvisionSsoUser(org, claims.email, claims.name, null, claims.sub, User.AuthProvider.GOOGLE);
        return issueTokens(org, user, request.isForceLogin());
    }

    private Organization requireActiveOrgBySlug(String orgSlug) {
        if (orgSlug == null || orgSlug.isBlank()) {
            throw new IllegalArgumentException("orgSlug is required");
        }
        Organization org = organizationRepository.findBySlug(orgSlug.trim().toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found"));
        if (org.getStatus() == OrganizationStatus.SUSPENDED) {
            throw new IllegalStateException(
                    "Account suspended. Contact your administrator at " + org.getContactEmail());
        }
        if (org.getStatus() != OrganizationStatus.ACTIVE) {
            throw new IllegalStateException("Organization is not active yet.");
        }
        return org;
    }

    private void assertPasswordLoginAllowed(Organization org) {
        OrganizationSecurity security = org.getSecurity() != null ? org.getSecurity() : new OrganizationSecurity();
        if (security.isRequireSso()) {
            throw new IllegalStateException("This organization requires SSO sign-in.");
        }
        if (!orgFeatureService.isEnabled(org.getId(), "email_password_auth")) {
            throw new IllegalStateException("Email and password sign-in is disabled for this organization.");
        }
    }

    private User resolveOrgUserByEmail(Organization org, String email) {
        User user = userService.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));
        if (user.getOrganizationId() == null || !user.getOrganizationId().equals(org.getId())) {
            throw new IllegalStateException("This account does not belong to this organization.");
        }
        if (!user.isActive()) {
            throw new IllegalStateException("Account is not active. Contact your administrator.");
        }
        return user;
    }

    private User resolveOrProvisionSsoUser(
            Organization org,
            String email,
            String name,
            String googleSub,
            String microsoftSub,
            User.AuthProvider provider) {
        String normalizedEmail = email.trim().toLowerCase();
        Optional<User> existing = userRepository.findByEmail(normalizedEmail);
        if (existing.isPresent()) {
            User user = existing.get();
            if (user.getOrganizationId() == null || !user.getOrganizationId().equals(org.getId())) {
                throw new IllegalStateException("This account does not belong to this organization.");
            }
            if (!user.isActive()) {
                throw new IllegalStateException("Account is not active. Contact your administrator.");
            }
            return user;
        }
        OrganizationSecurity security = org.getSecurity() != null ? org.getSecurity() : new OrganizationSecurity();
        if (!security.isJitProvisioning()) {
            throw new IllegalStateException("Your account has not been provisioned. Contact your administrator.");
        }
        return createJitUser(org, normalizedEmail, name, googleSub);
    }

    private User createJitUser(Organization org, String email, String name, String googleSub) {
        int active = (int) userRepository.countByOrganizationIdAndActiveTrue(org.getId());
        int maxSeats = orgFeatureService.getMaxSeats(org.getId());
        if (active >= maxSeats) {
            throw new IllegalStateException("Organization seat limit reached");
        }
        var now = IndiaTime.now();
        User user = User.builder()
                .name(name != null && !name.isBlank() ? name.trim() : email.split("@")[0])
                .email(email)
                .password(passwordEncode.getEncryptedPassword(java.util.UUID.randomUUID().toString()))
                .role(User.UserRole.USER)
                .orgRole(OrgRole.LEARNER)
                .organizationId(org.getId())
                .planTier(User.PlanTier.ENTERPRISE)
                .active(true)
                .emailVerified(true)
                .authProvider(User.AuthProvider.GOOGLE)
                .googleSub(googleSub)
                .onboardingCompleted(true)
                .onboardingCompletedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .createdBy("SSO_JIT")
                .updatedBy("SSO_JIT")
                .build();
        return userRepository.save(user);
    }

    private void linkGoogleSub(User user, String googleSub) {
        if (googleSub != null && !googleSub.equals(user.getGoogleSub())) {
            user.setGoogleSub(googleSub);
            user.setUpdatedAt(IndiaTime.now());
            userRepository.save(user);
        }
    }

    private void assertEmailDomainAllowed(Organization org, String email) {
        if (org.getSecurity() == null
                || org.getSecurity().getAllowedEmailDomains() == null
                || org.getSecurity().getAllowedEmailDomains().isEmpty()) {
            return;
        }
        String domain = email.substring(email.indexOf('@') + 1).toLowerCase();
        boolean allowed = org.getSecurity().getAllowedEmailDomains().stream()
                .anyMatch(d -> d.equalsIgnoreCase(domain));
        if (!allowed) {
            throw new IllegalStateException("Email domain is not allowed for this organization");
        }
    }

    private void assertGoogleHostedDomain(Organization org, String hostedDomain) {
        Map<String, Object> config = orgFeatureService.getConfig(org.getId(), "sso_google_workspace");
        if (config == null || !config.containsKey("hostedDomain")) {
            return;
        }
        String expected = String.valueOf(config.get("hostedDomain")).toLowerCase();
        if (hostedDomain == null || !hostedDomain.equalsIgnoreCase(expected)) {
            throw new IllegalStateException("Google account must belong to your organization workspace");
        }
    }

    private void assertMicrosoftTenant(Organization org, String tenantId) {
        Map<String, Object> config = orgFeatureService.getConfig(org.getId(), "sso_microsoft_entra");
        if (config == null || !config.containsKey("tenantId")) {
            return;
        }
        String expected = String.valueOf(config.get("tenantId"));
        if (tenantId == null || !tenantId.equalsIgnoreCase(expected)) {
            throw new IllegalStateException("Microsoft account must belong to your organization tenant");
        }
    }

    private Map<String, Object> issueTokens(Organization org, User user, boolean forceLogin) {
        if (!forceLogin && Boolean.TRUE.equals(user.getSessionActive())) {
            Map<String, Object> conflict = new HashMap<>();
            conflict.put("status", "conflict");
            conflict.put("message", "You're already logged in on another device.");
            return conflict;
        }
        userService.recordLogin(user);
        int sessionVersion = userService.startNewSession(user.getId());
        UserDetails userDetails = new UserDetails(user.getEmail());
        String orgRole = user.getOrgRole() != null ? user.getOrgRole().name() : null;
        Map<String, String> tokens = jwtUtils.generateSkillamaToken(
                userDetails, sessionVersion, org.getId(), orgRole, org.getSlug());

        Map<String, Object> body = new HashMap<>();
        body.put("status", "success");
        body.put("accessToken", tokens.get("accessToken"));
        body.put("refreshToken", tokens.get("refreshToken"));
        body.put("organizationId", org.getId());
        body.put("orgSlug", org.getSlug());
        body.put("orgRole", orgRole);
        body.put("userId", user.getId());
        body.put("name", user.getName());
        body.put("email", user.getEmail());
        return body;
    }
}
