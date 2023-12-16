package com.prwatech.common.configuration;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import lombok.AllArgsConstructor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;


import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

@Configuration
@AllArgsConstructor
public class BeanConfiguration {

  private final AppContext appContext;
  private final static String firebaseCredPath="/templates/firbase_sdk.json";
  @Bean
  public JavaMailSender javaMailSender(){

    JavaMailSenderImpl  mailSender = new JavaMailSenderImpl();
    mailSender.setHost(appContext.getEmailHostName());
    mailSender.setPort(appContext.getEmailPort());
    mailSender.setUsername(appContext.getEmailHostUsername());
    mailSender.setPassword(appContext.getEmailHostPassword());

    return mailSender;
  }

  @Bean
  FirebaseApp firebaseApp(GoogleCredentials credentials) throws IOException {
    FirebaseOptions options = FirebaseOptions.builder()
            .setCredentials(credentials)
            .build();
    if(FirebaseApp.getApps().isEmpty()){
      return FirebaseApp.initializeApp(options);
    }
    return FirebaseApp.initializeApp(options);
  }
  @Bean
  FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
    return FirebaseMessaging.getInstance(firebaseApp);
  }
  @Bean
  GoogleCredentials googleCredentials() throws IOException {
    return GoogleCredentials.fromStream(new ClassPathResource(firebaseCredPath).getInputStream());
  }


}
