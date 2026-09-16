package com.prwatech.common.configuration;

import org.springframework.beans.factory.annotation.Value;
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

    @Value("${swagger.user:dev}")
    private String swaggerUser;

    @Value("${swagger.password:dev-only-change-me}")
    private String swaggerPassword;

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

    /**
     * Swagger HTTP Basic user. Password comes from SWAGGER_PASSWORD (or swagger.password).
     * Local default is a non-production placeholder — never commit live credentials here.
     */
    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth.inMemoryAuthentication()
            .withUser(swaggerUser).password(swaggerPasswordEncoderId(swaggerPassword)).roles("SWAGGER");
    }

    static String swaggerPasswordEncoderId(String password) {
        if (password == null || password.isBlank()) {
            return "{noop}dev-only-change-me";
        }
        if (password.startsWith("{")) {
            return password;
        }
        return "{noop}" + password;
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
