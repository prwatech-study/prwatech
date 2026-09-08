package com.prwatech.skillama.service;

import com.prwatech.authentication.security.JwtUtils;
import com.prwatech.common.Constants;
import com.prwatech.skillama.exception.SkillamaAuthException;
import com.prwatech.skillama.model.Organization;
import com.prwatech.skillama.model.OrganizationStatus;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.OrganizationRepository;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;

@Service
@RequiredArgsConstructor
public class SkillamaAuthSupport {

    private final JwtUtils jwtUtils;
    private final UserService userService;
    private final OrganizationRepository organizationRepository;

    public String resolveUserIdFromRequest(HttpServletRequest request) {
        return resolveSessionFromRequest(request).userId();
    }

    /** Same choke point as {@link #resolveUserIdFromRequest}, also exposing the request's own
     * tokenVersion — needed by the heartbeat endpoint to identify which UserSession row is
     * "this" login, as opposed to some other session the same account had at another time. */
    public ResolvedSession resolveSessionFromRequest(HttpServletRequest request) {
        final String requestTokenHeader = request.getHeader(Constants.AUTH);
        if (requestTokenHeader == null || !requestTokenHeader.startsWith("Bearer ")) {
            throw new SkillamaAuthException("Session expired. Please sign in again.");
        }

        String jwtToken = requestTokenHeader.substring(7).trim();
        String email;
        int tokenVersion;
        try {
            email = jwtUtils.extractUsername(jwtToken);
            tokenVersion = jwtUtils.extractTokenVersion(jwtToken);
        } catch (ExpiredJwtException e) {
            throw new SkillamaAuthException("Session expired. Please sign in again.");
        } catch (JwtException e) {
            throw new SkillamaAuthException("Session expired. Please sign in again.");
        }

        User user = userService.findByEmailForAuth(email)
                .orElseThrow(() -> new SkillamaAuthException("Account not found. Please sign in again."));

        assertJwtOrganizationBinding(jwtToken, user);
        assertOrganizationAllowsAccess(user);

        int currentVersion = user.getTokenVersion() != null ? user.getTokenVersion() : 0;
        if (tokenVersion < currentVersion) {
            throw new SkillamaAuthException(
                    "You've been signed out because this account was signed in elsewhere.",
                    "SESSION_REVOKED");
        }

        return new ResolvedSession(user.getId(), tokenVersion);
    }

    private void assertJwtOrganizationBinding(String jwtToken, User user) {
        String tokenOrgId = jwtUtils.extractOrganizationId(jwtToken);
        if (tokenOrgId == null) {
            if (user.getOrganizationId() != null && !TenantSecurityService.isPlatformStaff(user)) {
                throw new SkillamaAuthException("Organization session required for this account.");
            }
            return;
        }
        if (user.getOrganizationId() == null) {
            throw new SkillamaAuthException("Invalid organization token for this account.");
        }
        if (!tokenOrgId.equals(user.getOrganizationId())) {
            throw new SkillamaAuthException("Organization token mismatch.", "ORG_MISMATCH");
        }
    }

    private void assertOrganizationAllowsAccess(User user) {
        if (user.getOrganizationId() == null) {
            return;
        }
        Organization org = organizationRepository.findById(user.getOrganizationId()).orElse(null);
        if (org == null) {
            throw new SkillamaAuthException("Organization not found for this account.");
        }
        if (org.getStatus() == OrganizationStatus.SUSPENDED) {
            throw new SkillamaAuthException(
                    "Organization account suspended. Contact your administrator.",
                    "ORG_SUSPENDED");
        }
        if (org.getStatus() == OrganizationStatus.ARCHIVED) {
            throw new SkillamaAuthException("Organization account archived.");
        }
        if (org.getStatus() == OrganizationStatus.PROVISIONING) {
            throw new SkillamaAuthException("Organization is not active yet.");
        }
    }

    public record ResolvedSession(String userId, int tokenVersion) {
    }
}
