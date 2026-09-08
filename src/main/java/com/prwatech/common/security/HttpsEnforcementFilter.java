package com.prwatech.common.security;

import java.io.IOException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class HttpsEnforcementFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        String host = request.getHeader("Host");
        String redirect =
                HttpsEnforcement.redirectLocation(
                        request.getRequestURL().toString(),
                        request.getQueryString(),
                        forwardedProto,
                        host);
        if (redirect != null) {
            response.setStatus(308);
            response.setHeader("Location", redirect);
            return;
        }
        if (HttpsEnforcement.isHttps(forwardedProto) && !HttpsEnforcement.isLocalHost(host)) {
            response.setHeader("Strict-Transport-Security", HttpsEnforcement.HSTS_HEADER_VALUE);
        }
        filterChain.doFilter(request, response);
    }
}
