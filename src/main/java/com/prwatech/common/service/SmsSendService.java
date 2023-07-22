package com.prwatech.common.service;

import com.prwatech.common.dto.SmsSendDto;
import java.io.IOException;
import org.springframework.stereotype.Component;

@Component
public interface SmsSendService {

  public Boolean sendSmsToPhoneNumber(SmsSendDto smsSendDto);

  public Boolean sendNormalOtp(SmsSendDto smsSendDto);

  public Boolean sendDefaultOtpMessage(SmsSendDto smsSendDto) throws IOException;

  public Boolean sendPhoneSms(SmsSendDto smsSendDto);
}
