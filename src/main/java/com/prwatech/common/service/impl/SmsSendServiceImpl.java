package com.prwatech.common.service.impl;

import com.prwatech.common.dto.FastToSmsWalletDto;
import com.prwatech.common.dto.SmsSendDto;
import com.prwatech.common.dto.SmsSendResponseDto;
import com.prwatech.common.exception.UnProcessableEntityException;
import com.prwatech.common.service.SmsSendService;
import java.util.Objects;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@AllArgsConstructor
@Transactional
@Component
public class SmsSendServiceImpl implements SmsSendService {

  private static final org.slf4j.Logger LOGGER =
      org.slf4j.LoggerFactory.getLogger(SmsSendServiceImpl.class);

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
}
