package com.prwatech.common.configuration;

import com.prwatech.authentication.security.AuthInterceptor;
import com.prwatech.authentication.security.JwtUtils;
import com.prwatech.skillama.security.SkillamaCorsOrigins;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableWebMvc
public class WevMvcConfiguration implements WebMvcConfigurer {

  private static final String[] CLASSPATH_RESOURCE_LOCATIONS = {
    "classpath:/META-INF/resources/", "classpath:/resources/",
    "classpath:/static/", "classpath:/public/"
  };

  private final JwtUtils jwtUtils;
  private final String allowedOriginsRaw;

  public WevMvcConfiguration(
      JwtUtils jwtUtils,
      @Value("${skillama.cors.allowed-origins:" + SkillamaCorsOrigins.DEFAULT + "}")
      String allowedOriginsRaw) {
    this.jwtUtils = jwtUtils;
    this.allowedOriginsRaw = allowedOriginsRaw;
  }

  @Bean
  public AuthInterceptor authenticationInterceptor() {
    return new AuthInterceptor(jwtUtils);
  }

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    if (!registry.hasMappingForPattern("/**")) {
      registry.addResourceHandler("/**").addResourceLocations(CLASSPATH_RESOURCE_LOCATIONS);
    }
    // Add handler for uploaded files
    registry.addResourceHandler("/files/**")
        .addResourceLocations("file:uploads/");
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    String[] origins = SkillamaCorsOrigins.parse(allowedOriginsRaw).toArray(String[]::new);
    registry.addMapping("/**")
        .allowedOrigins(origins)
        .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
        .allowedHeaders("Authorization", "Content-Type", "X-Session-Id")
        .allowCredentials(true)
        .maxAge(3600);
  }

  @Override
  public void addFormatters(final FormatterRegistry registry) {
    ApplicationConversionService.configure(registry);
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(authenticationInterceptor()).addPathPatterns("/api/v1/**");
  }
}
