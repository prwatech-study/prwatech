package com.prwatech.common.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;

@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    // Define multiple in-memory users
    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth.inMemoryAuthentication()
            .withUser("jitendra").password("{noop}prwatech1234").roles("SWAGGER")
            .and()
            .withUser("prwatech").password("{noop}prwatech1234").roles("SWAGGER");
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
            .authorizeRequests()
                .antMatchers(
                    "/swagger-ui.html",
                    "/swagger-ui/**",
                    "/swagger-resources/**",
                    "/v2/api-docs",
                    "/webjars/**"
                ).authenticated() // Require auth for Swagger
                .anyRequest().permitAll() // Allow other requests
            .and()
            .httpBasic() // Use HTTP Basic Auth (popup)
            .and()
            .csrf().disable(); // Disable CSRF for simplicity (enable as needed)
    }
}
