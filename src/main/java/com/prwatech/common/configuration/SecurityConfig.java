package com.prwatech.common.configuration;

import com.prwatech.skillama.security.SkillamaAnonymousRequestMatchers;
import com.prwatech.skillama.security.SkillamaJwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfigurationSource;

import javax.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;

@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    private final String swaggerUser;
    private final String swaggerPassword;
    private final SkillamaJwtAuthenticationFilter skillamaJwtAuthenticationFilter;
    private final CorsConfigurationSource corsConfigurationSource;

    public SecurityConfig(
            @Value("${swagger.user:dev}") String swaggerUser,
            @Value("${swagger.password:dev-only-change-me}") String swaggerPassword,
            SkillamaJwtAuthenticationFilter skillamaJwtAuthenticationFilter,
            CorsConfigurationSource corsConfigurationSource) {
        this.swaggerUser = swaggerUser;
        this.swaggerPassword = swaggerPassword;
        this.skillamaJwtAuthenticationFilter = skillamaJwtAuthenticationFilter;
        this.corsConfigurationSource = corsConfigurationSource;
    }

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
        AuthenticationEntryPoint entryPoint = skillamaAuthenticationEntryPoint();

        var registry = http
            .cors().configurationSource(corsConfigurationSource)
            .and()
            // Learner/admin/org auth is Authorization Bearer (not cookies). Guest mutating
            // POSTs rely on a host-only API cookie whose unspecified SameSite defaults to Lax
            // (not sent on cross-site POST) plus the F03 origin allowlist. Enabling Spring CSRF
            // would break Bearer clients. Do not change guest cookie attributes in F03.
            .csrf().disable()
            .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            .and()
            .exceptionHandling().authenticationEntryPoint(entryPoint)
            .and()
            .httpBasic().authenticationEntryPoint(entryPoint)
            .and()
            .authorizeRequests()
                .antMatchers(HttpMethod.OPTIONS, "/**").permitAll();

        for (RequestMatcher matcher : SkillamaAnonymousRequestMatchers.matchers()) {
            registry.requestMatchers(matcher).permitAll();
        }

        registry
                .antMatchers(
                    "/swagger-ui.html",
                    "/swagger-ui/**",
                    "/swagger-resources/**",
                    "/v2/api-docs",
                    "/webjars/**"
                ).authenticated()
                .antMatchers("/skillama/**").authenticated()
                .antMatchers("/api/v1/**", "/api/iam/**", "/api/public/**", "/api/feedbacks/**").permitAll()
                .antMatchers("/files/**", "/error").permitAll()
                .anyRequest().denyAll()
            .and()
            .addFilterBefore(skillamaJwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
    }

    private AuthenticationEntryPoint skillamaAuthenticationEntryPoint() {
        BasicAuthenticationEntryPoint basic = new BasicAuthenticationEntryPoint();
        basic.setRealmName("swagger");
        LinkedHashMap<RequestMatcher, AuthenticationEntryPoint> map = new LinkedHashMap<>();
        map.put(new AntPathRequestMatcher("/swagger-ui.html"), basic);
        map.put(new AntPathRequestMatcher("/swagger-ui/**"), basic);
        map.put(new AntPathRequestMatcher("/swagger-resources/**"), basic);
        map.put(new AntPathRequestMatcher("/v2/api-docs"), basic);
        map.put(new AntPathRequestMatcher("/webjars/**"), basic);
        DelegatingAuthenticationEntryPoint delegating = new DelegatingAuthenticationEntryPoint(map);
        AuthenticationEntryPoint json = (request, response, authException) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"status\":\"error\",\"message\":\"Unauthorized\"}");
        };
        delegating.setDefaultEntryPoint(json);
        return delegating;
    }

    /**
     * Register the custom firewall to allow double slashes in URLs.
     */
    @Override
    public void configure(WebSecurity web) throws Exception {
        web.httpFirewall(allowDoubleSlashHttpFirewall());
    }
}
