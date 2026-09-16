package com.prwatech.skillama.security;

import com.prwatech.skillama.service.SkillamaAuthSupport;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SkillamaJwtFilterConfiguration {

    @Bean
    public SkillamaJwtAuthenticationFilter skillamaJwtAuthenticationFilter(
            SkillamaAuthSupport skillamaAuthSupport) {
        return new SkillamaJwtAuthenticationFilter(skillamaAuthSupport);
    }
}
