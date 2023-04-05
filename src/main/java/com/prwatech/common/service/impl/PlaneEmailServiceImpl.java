package com.prwatech.common.service.impl;

import static com.prwatech.common.Constants.SUCCESSFUL;
import static com.prwatech.common.Constants.UNSUCCESSFUL;

import com.prwatech.common.dto.EmailSendResponseDto;
import com.prwatech.common.dto.PlaneEmailSendDto;
import com.prwatech.common.service.PlaneEmailService;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class PlaneEmailServiceImpl implements PlaneEmailService {

  private static final org.slf4j.Logger LOGGER =
      org.slf4j.LoggerFactory.getLogger(PlaneEmailServiceImpl.class);

  private final JavaMailSender javaMailSender;

  @Override
  public EmailSendResponseDto sendPlaneEmailToUser(PlaneEmailSendDto emailSendDto) {
    try {
      LOGGER.info("Creating the mail to send :: ");
      SimpleMailMessage message = new SimpleMailMessage();
      message.setFrom(emailSendDto.getSenderMailId());
      message.setTo(emailSendDto.getReceiverMailId());
      message.setSubject(emailSendDto.getSubject());
      message.setText(emailSendDto.getTextBody());
      javaMailSender.send(message);

      LOGGER.info("Mail sent successfully, with status code :: 200");

      return new EmailSendResponseDto(
          null, emailSendDto.getReceiverMailId(), SUCCESSFUL, HttpStatus.OK);

    } catch (Exception e) {
      LOGGER.error("Something went wrong while sending the mail :: {}", e.getMessage());
    }
    return new EmailSendResponseDto(
        null, emailSendDto.getReceiverMailId(), UNSUCCESSFUL, HttpStatus.NOT_IMPLEMENTED);
  }
}
