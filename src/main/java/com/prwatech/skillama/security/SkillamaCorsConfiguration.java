package com.prwatech.skillama.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@Configuration
public class SkillamaCorsConfiguration {

    @Value("${skillama.cors.allowed-origins:" + SkillamaCorsOrigins.DEFAULT + "}")
    private String allowedOriginsRaw;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        List<String> allowed = SkillamaCorsOrigins.parse(allowedOriginsRaw);
        return request -> corsFor(request, allowed);
    }

    static CorsConfiguration corsFor(HttpServletRequest request, List<String> allowed) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Session-Id"));
        config.setMaxAge(3600L);
        String origin = request.getHeader("Origin");
        if (SkillamaCorsOrigins.isAllowed(origin, allowed)) {
            config.setAllowedOrigins(List.of(origin));
        }
        return config;
    }
}
