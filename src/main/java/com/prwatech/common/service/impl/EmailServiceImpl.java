package com.prwatech.common.service.impl;

import com.prwatech.common.configuration.AppContext;
import lombok.AllArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class EmailServiceImpl {

  private static final org.slf4j.Logger LOGGER =
      org.slf4j.LoggerFactory.getLogger(EmailServiceImpl.class);

  private final AppContext appContext;
  private final JavaMailSender javaMailSender;

  public int sendMail(String toEmail, String body, String subject) {
    try {
      SimpleMailMessage message = new SimpleMailMessage();
      message.setFrom(appContext.getDefaultMailSenderId());
      message.setTo(toEmail);
      message.setSubject(subject);
      message.setText(body);
      javaMailSender.send(message);
      return 200;

    } catch (Exception e) {
      LOGGER.error("Something went wrong in sending: {}", e.getMessage());
    }
    return 300;
  }
}
