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
  public Boolean sendDefaultOtpMessage(SmsSendDto smsSendDto) throws IOException {

    if (Objects.isNull(smsSendDto)) {
      LOGGER.error("Sms send dto is null.");

      throw new UnProcessableEntityException(
          "Please try again after some time! We are facing problem to sent sms.");
    }
    SmsSendResponseDto smsSendResponseDto = fastToSmsService.sendOtpMessage(smsSendDto);

    if (Objects.isNull(smsSendResponseDto) || smsSendResponseDto.getRequest_id()==null) {
      LOGGER.error("Sms has not sent successfully!");
      throw new UnProcessableEntityException(
          "Please try again, service is not working as expected!");
    }
    return Boolean.TRUE;
  }
}
