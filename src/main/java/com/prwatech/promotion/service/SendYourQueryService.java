package com.prwatech.promotion.service;

import com.prwatech.promotion.dto.HelpAndSupportDto;
import com.prwatech.promotion.dto.SendYourQueryRequestDto;
import org.springframework.stereotype.Component;

@Component
public interface SendYourQueryService {

  Boolean sendYourQueryForCourse(SendYourQueryRequestDto sendYourQueryRequestDto);

  Boolean sendYourQueryToHelpAndSupport(HelpAndSupportDto helpAndSupportDto);
}
