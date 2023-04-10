package com.prwatech.common.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

@Configuration
public class BeanConfiguration {

  @Bean
  public JavaMailSender javaMailSender() {
    return javaMailSender();
  }
}
