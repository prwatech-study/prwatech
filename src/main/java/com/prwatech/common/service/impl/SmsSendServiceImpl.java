package com.prwatech.common.service.impl;

import com.prwatech.common.Constants;
import com.prwatech.common.configuration.AppContext;
import com.prwatech.common.dto.FastToSmsWalletDto;
import com.prwatech.common.dto.SmsSendDto;
import com.prwatech.common.dto.SmsSendResponseDto;
import com.prwatech.common.exception.UnProcessableEntityException;
import com.prwatech.common.service.SmsSendService;
import java.io.IOException;
import java.util.Objects;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@AllArgsConstructor
@Transactional
@Component
public class SmsSendServiceImpl implements SmsSendService {

  private static final org.slf4j.Logger LOGGER =
      org.slf4j.LoggerFactory.getLogger(SmsSendServiceImpl.class);

  private final AppContext context;

  private final FastToSmsService fastToSmsService;

  @Override
  public Boolean sendSmsToPhoneNumber(SmsSendDto smsSendDto) {

    FastToSmsWalletDto fastToSmsWalletDto = fastToSmsService.getWalletStatement();

    if (Objects.isNull(fastToSmsWalletDto) || !fastToSmsWalletDto.getIsReturn().equals("true")) {
      LOGGER.error("Sms service wallet is not sufficient to send sms.");

      throw new UnProcessableEntityException(
          "Please try again after some time! We are facing problem to sent sms.");
    }

    SmsSendResponseDto smsSendResponseDto = fastToSmsService.sentMessage(smsSendDto);

    if (Objects.isNull(smsSendResponseDto) || !smsSendResponseDto.getIsReturn().equals("true")) {
      LOGGER.error("Sms has not sent successfully!");
      throw new UnProcessableEntityException(
          "Please try again, service is not working as expected!");
    }

    return Boolean.TRUE;
  }

  @Override
  public Boolean sendNormalOtp(SmsSendDto smsSendDto) {

    FastToSmsWalletDto fastToSmsWalletDto = fastToSmsService.getWalletStatement();

    if (Objects.isNull(fastToSmsWalletDto) || !fastToSmsWalletDto.getIsReturn().equals("true")) {
      LOGGER.error("Sms service wallet is not sufficient to send sms.");

      throw new UnProcessableEntityException(
          "Please try again after some time! We are facing problem to sent sms.");
    }
    SmsSendResponseDto smsSendResponseDto = fastToSmsService.sendNormalOtpMessage(smsSendDto);

    if (Objects.isNull(smsSendResponseDto) || !smsSendResponseDto.getIsReturn().equals("true")) {
      LOGGER.error("Sms has not sent successfully!");
      throw new UnProcessableEntityException(
          "Please try again, service is not working as expected!");
    }
    return Boolean.TRUE;
  }

  @Override
  public Boolean sendDefaultOtpMessage(SmsSendDto smsSendDto) throws IOException {
    FastToSmsWalletDto fastToSmsWalletDto = fastToSmsService.getWalletStatement();

    if (Objects.isNull(fastToSmsWalletDto) || !fastToSmsWalletDto.getIsReturn().equals("true")) {
      LOGGER.error("Sms service wallet is not sufficient to send sms.");

      throw new UnProcessableEntityException(
          "Please try again after some time! We are facing problem to sent sms.");
    }
    SmsSendResponseDto smsSendResponseDto = fastToSmsService.sendOtpMessage(smsSendDto);

    if (Objects.isNull(smsSendResponseDto) || !smsSendResponseDto.getIsReturn().equals("true")) {
      LOGGER.error("Sms has not sent successfully!");
      throw new UnProcessableEntityException(
          "Please try again, service is not working as expected!");
    }
    return Boolean.TRUE;
  }

  @Override
  public Boolean sendPhoneSms(SmsSendDto smsSendDto) {
      final String ACCOUNT_SID = context.getTwilioSid();
      final String AUTH_TOKEN =context.getTwilioAuth();
    try{
      Twilio.init(ACCOUNT_SID, AUTH_TOKEN);
      Message message = Message.creator(
                      new com.twilio.type.PhoneNumber("+91"+smsSendDto.getNumbers()),
                      new com.twilio.type.PhoneNumber(Constants.DEFAULT_SMS_PROVIDER_NUMBER),
                      smsSendDto.getMessage())
              .create();

      if(message.getSid()!=null && message.getStatus().equals("sent")){
        LOGGER.info("Message has been sent to : {}",smsSendDto.getNumbers());
        return true;
      }

    }catch (Exception e){
      LOGGER.error("Something went wrong while sending sms to : {} with error: {}", smsSendDto.getNumbers(), e.getMessage());
      return false;
    }
    return false;
  }
}
