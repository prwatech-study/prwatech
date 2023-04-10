package com.prwatech.common.configuration;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Getter
@Configuration
public class AppContext {

  @Value("${swagger.enabled}")
  private Boolean isSwaggerEnabled;

  @Value("${prwatech.auth0.client.domain}")
  private String auth0DomainId;

  @Value("${prwatech.auth0.client.id}")
  private String auth0ClientId;

  @Value("${prwatech.auth0.client.secret.key}")
  private String auth0ClientSecretKey;

  @Value("${bcrypt.password.salt}")
  private String salt;

  @Value("${prwatech.fast.to.sms.api.key}")
  private String fastToSMSApiKey;

  @Value("${spring.mail.host}")
  private String emailHostName;

  @Value("${spring.mail.port}")
  private Integer emailPort;

  @Value("${spring.mail.username}")
  private String emailHostUsername;

  @Value("${spring.mail.password}")
  private String emailHostPassword;

  @Value("${spring.mail.transport.protocol}")
  private String emailProtocol;

  @Value("${spring.mail.properties.mail.smtp.auth}")
  private Boolean emailAuth;

  @Value("${spring.mail.properties.mail.smtp.starttls.enable}")
  private String emailStarttls;

  @Value("${prwatech.default.email.id}")
  private String defaultMailSenderId;

  @Value("${prwatech.default.sale.email.id}")
  private String defaultSaleEmailId;
}
