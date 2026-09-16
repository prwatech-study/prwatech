package com.prwatech.common.configuration;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.io.IOException;
import java.io.InputStream;

@Configuration
public class BeanConfiguration {

  private final AppContext appContext;

  @Value("${firebase.credentials.json:}")
  private String firebaseCredentialsJson;

  @Value("${firebase.credentials.file:}")
  private String firebaseCredentialsFile;

  public BeanConfiguration(AppContext appContext) {
    this.appContext = appContext;
  }

  @Bean
  public JavaMailSender javaMailSender() {
    JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
    mailSender.setHost(appContext.getEmailHostName());
    mailSender.setPort(appContext.getEmailPort());
    mailSender.setUsername(appContext.getEmailHostUsername());
    mailSender.setPassword(appContext.getEmailHostPassword());
    return mailSender;
  }

  @Bean
  FirebaseApp firebaseApp(GoogleCredentials credentials) {
    FirebaseOptions options = FirebaseOptions.builder()
            .setCredentials(credentials)
            .build();
    if (FirebaseApp.getApps().isEmpty()) {
      return FirebaseApp.initializeApp(options);
    }
    return FirebaseApp.getApps().get(0);
  }

  @Bean
  FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
    return FirebaseMessaging.getInstance(firebaseApp);
  }

  @Bean
  GoogleCredentials googleCredentials() throws IOException {
    try (InputStream stream = FirebaseCredentialsLoader.open(firebaseCredentialsJson, firebaseCredentialsFile)) {
      return GoogleCredentials.fromStream(stream);
    }
  }
}
