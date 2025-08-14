package com.prwatech.common.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;

@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    /**
     * Allows double slashes in URLs. Only enable this if you absolutely need to support such requests.
     * Best practice: Fix client or gateway to avoid double slashes. Allowing them can introduce security risks.
     */
    @Bean
    public HttpFirewall allowDoubleSlashHttpFirewall() {
        StrictHttpFirewall firewall = new StrictHttpFirewall();
        firewall.setAllowUrlEncodedDoubleSlash(true); // only works for encoded `//`
        return firewall;
    }

    // Define multiple in-memory users
    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth.inMemoryAuthentication()
            .withUser("jitendra").password("{noop}prwatech1234").roles("SWAGGER")
            .and()
            .withUser("prwatech").password("{noop}prwatech@2025").roles("SWAGGER")
            .and()
            .withUser("sushant").password("{noop}prwatech1234").roles("SWAGGER");
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

    /**
     * Register the custom firewall to allow double slashes in URLs.
     */
    @Override
    public void configure(WebSecurity web) throws Exception {
        web.httpFirewall(allowDoubleSlashHttpFirewall());
    }
}
