package com.prwatech.common.configuration;

import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@Configuration
@AllArgsConstructor
public class BeanConfiguration {

  private final AppContext appContext;

  @Bean
  public JavaMailSender javaMailSender(){

    JavaMailSenderImpl  mailSender = new JavaMailSenderImpl();
    mailSender.setHost(appContext.getEmailHostName());
    mailSender.setPort(appContext.getEmailPort());
    mailSender.setUsername(appContext.getEmailHostUsername());
    mailSender.setPassword(appContext.getEmailHostPassword());

    return mailSender;
  }
}
