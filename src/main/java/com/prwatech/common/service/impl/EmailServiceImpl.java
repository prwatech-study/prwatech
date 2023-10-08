package com.prwatech.common.service.impl;

import com.prwatech.common.configuration.AppContext;
import com.prwatech.common.dto.EmailSendDto;
import com.prwatech.common.exception.UnProcessableEntityException;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import lombok.AllArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.util.Properties;


@Component
@AllArgsConstructor
public class EmailServiceImpl {

  private static final org.slf4j.Logger LOGGER =
      org.slf4j.LoggerFactory.getLogger(EmailServiceImpl.class);
  private final AppContext appContext;
  private final JavaMailSender mailSender;

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

  public Boolean sendSimpleMail(EmailSendDto emailSendDto){
    try {

      MimeMessage mimeMessage = mailSender.createMimeMessage();
      MimeMessageHelper mimeMessageHelper = new MimeMessageHelper(mimeMessage);

      mimeMessageHelper.setFrom(appContext.getEmailHostUsername());
      mimeMessageHelper.setSubject(emailSendDto.getSubject());
      mimeMessageHelper.setTo(emailSendDto.getReceiverEmailId());
      mimeMessage.setText(emailSendDto.getTextMessage());

      mailSender.send(mimeMessage);

      return Boolean.TRUE;
    }
    catch (Exception e){
      LOGGER.error("Something went wrong while sending the mail to email id: "+ emailSendDto.getReceiverEmailId()+
              "with error: "+ e.getMessage());
    }
    return Boolean.FALSE;
  }

}
