package com.prwatech.skillama.service;

import com.prwatech.authentication.security.JwtUtils;
import com.prwatech.common.Constants;
import com.prwatech.skillama.exception.SkillamaAuthException;
import com.prwatech.skillama.model.User;
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

        int currentVersion = user.getTokenVersion() != null ? user.getTokenVersion() : 0;
        if (tokenVersion < currentVersion) {
            throw new SkillamaAuthException(
                    "You've been signed out because this account was signed in elsewhere.",
                    "SESSION_REVOKED");
        }

        return new ResolvedSession(user.getId(), tokenVersion);
    }

    public record ResolvedSession(String userId, int tokenVersion) {
    }
}
