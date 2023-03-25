package com.prwatech.common.configuration;

import com.auth0.AuthenticationController;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.JwkProviderBuilder;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@AllArgsConstructor
public class BeanConfiguration {

  private final AppContext appContext;

  @Bean
  public AuthenticationController getAuth0Instance() {

    JwkProvider jwkProvider = new JwkProviderBuilder(appContext.getAuth0DomainId()).build();
    return AuthenticationController.newBuilder(
            appContext.getAuth0DomainId(),
            appContext.getAuth0ClientId(),
            appContext.getAuth0ClientSecretKey())
        .withJwkProvider(jwkProvider)
        .build();
  }
}
