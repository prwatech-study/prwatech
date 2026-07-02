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
        final String requestTokenHeader = request.getHeader(Constants.AUTH);
        if (requestTokenHeader == null || !requestTokenHeader.startsWith("Bearer ")) {
            throw new SkillamaAuthException("Session expired. Please sign in again.");
        }

        String jwtToken = requestTokenHeader.substring(7).trim();
        String email;
        try {
            email = jwtUtils.extractUsername(jwtToken);
        } catch (ExpiredJwtException e) {
            throw new SkillamaAuthException("Session expired. Please sign in again.");
        } catch (JwtException e) {
            throw new SkillamaAuthException("Session expired. Please sign in again.");
        }

        return userService.findByEmailForAuth(email)
                .map(User::getId)
                .orElseThrow(() -> new SkillamaAuthException("Account not found. Please sign in again."));
    }
}
