package com.prwatech.common.service.impl;

import com.prwatech.common.configuration.AppContext;
import com.prwatech.common.dto.EmailSendDto;
import com.prwatech.common.exception.UnProcessableEntityException;
import java.util.Properties;
import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.Multipart;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class EmailServiceImpl {

  private static final org.slf4j.Logger LOGGER =
      org.slf4j.LoggerFactory.getLogger(EmailServiceImpl.class);
  private final AppContext appContext;

  private Properties getProperties() {
    Properties property = new Properties();
    property.put("mail.smtp.auth", appContext.getEmailAuth());
    property.put("mail.smtp.starttls.enable", appContext.getEmailStarttls());
    property.put("mail.smtp.host", appContext.getEmailHostName());
    property.put("mail.smtp.port", appContext.getEmailPort());
    property.put("mail.smtp.ssl.trust", appContext.getEmailHostName());
    return property;
  }

  public Boolean sendNormalEmailWithPlanText(EmailSendDto emailSendDto) {

    try {
      Session session =
          Session.getInstance(
              getProperties(),
              new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                  return new PasswordAuthentication(
                      appContext.getEmailHostUsername(), appContext.getEmailHostPassword());
                }
              });

      Message message = new MimeMessage(session);

      LOGGER.info("Preparing text email to send!");

      message.setFrom(new InternetAddress(emailSendDto.getSenderEmailId()));
      message.setRecipient(
          Message.RecipientType.TO, new InternetAddress(emailSendDto.getReceiverEmailId()));
      message.setSubject(emailSendDto.getSubject());

      MimeBodyPart mimeBodyPart = new MimeBodyPart();
      mimeBodyPart.setContent(emailSendDto.getTextMessage(), "text/html; charset=utf-8");

      Multipart multipart = new MimeMultipart();
      multipart.addBodyPart(mimeBodyPart);

      message.setContent(multipart);

      Transport.send(message);
      LOGGER.info(
          "Email delivered successfully to receiver with email id : {}",
          emailSendDto.getReceiverEmailId());
      return Boolean.TRUE;

    } catch (Exception e) {
      LOGGER.error("Something went while sending email! due to:: {}", e.getMessage());
      throw new UnProcessableEntityException(
          "Something went wrong while sending email, please try again!");
    }
  }
}
