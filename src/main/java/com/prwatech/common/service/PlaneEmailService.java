package com.prwatech.common.service;

import com.prwatech.common.dto.EmailSendResponseDto;
import com.prwatech.common.dto.PlaneEmailSendDto;

public interface PlaneEmailService {

  EmailSendResponseDto sendPlaneEmailToUser(PlaneEmailSendDto emailSendDto);
}
