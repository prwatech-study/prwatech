package com.prwatech.skillama.security;

import com.prwatech.skillama.exception.SkillamaAuthException;
import com.prwatech.skillama.service.SkillamaAuthSupport;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Populates {@link SecurityContextHolder} from the existing Skillama JWT path.
 * Not a new authentication service — delegates to {@link SkillamaAuthSupport}.
 */
public class SkillamaJwtAuthenticationFilter extends OncePerRequestFilter {

    private final SkillamaAuthSupport skillamaAuthSupport;

    public SkillamaJwtAuthenticationFilter(SkillamaAuthSupport skillamaAuthSupport) {
        this.skillamaAuthSupport = skillamaAuthSupport;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        if (!SkillamaAnonymousRequestMatchers.isSkillamaApi(request)) {
            return true;
        }
        if (SkillamaAnonymousRequestMatchers.isAnonymousSkillama(request)) {
            return true;
        }
        return SkillamaAnonymousRequestMatchers.isSwagger(request);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        try {
            SkillamaAuthSupport.ResolvedSession session =
                    skillamaAuthSupport.resolveSessionFromRequest(request);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(session.userId(), null, List.of());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (SkillamaAuthException e) {
            SecurityContextHolder.clearContext();
            writeUnauthorized(response, e.getMessage());
        }
    }

    static void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String safe = message == null ? "Unauthorized" : message.replace("\\", "\\\\").replace("\"", "\\\"");
        response.getWriter().write("{\"status\":\"error\",\"message\":\"" + safe + "\"}");
    }
}
