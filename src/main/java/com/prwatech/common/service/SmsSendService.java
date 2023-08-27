package com.prwatech.common.service;

import com.prwatech.common.dto.SmsSendDto;
import java.io.IOException;
import org.springframework.stereotype.Component;

@Component
public interface SmsSendService {

  public Boolean sendDefaultOtpMessage(SmsSendDto smsSendDto) throws IOException;

}
