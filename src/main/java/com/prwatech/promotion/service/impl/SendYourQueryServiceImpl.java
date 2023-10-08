package com.prwatech.promotion.service.impl;

import com.prwatech.common.Constants;
import com.prwatech.common.configuration.AppContext;
import com.prwatech.common.dto.EmailSendDto;
import com.prwatech.common.exception.UnProcessableEntityException;
import com.prwatech.common.service.impl.EmailServiceImpl;
import com.prwatech.promotion.dto.HelpAndSupportDto;
import com.prwatech.promotion.dto.SendYourQueryRequestDto;
import com.prwatech.promotion.service.SendYourQueryService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@AllArgsConstructor
public class SendYourQueryServiceImpl implements SendYourQueryService {

  private static final org.slf4j.Logger LOGGER =
      org.slf4j.LoggerFactory.getLogger(SendYourQueryServiceImpl.class);

  private final EmailServiceImpl emailService;
  private final AppContext appContext;

  @Override
  public Boolean sendYourQueryForCourse(SendYourQueryRequestDto sendYourQueryRequestDto) {

    String message =
        "User name : "
            + ((sendYourQueryRequestDto.getName()!=null)?sendYourQueryRequestDto.getName():"")
            + "\n%s"
            + "User Mobile : "
            + ((sendYourQueryRequestDto.getMobileNumber()!=null)?sendYourQueryRequestDto.getMobileNumber():"")
            + "\n%s"
            + "User Email id: "
            + ((sendYourQueryRequestDto.getEmailId()!=null)?sendYourQueryRequestDto.getEmailId():"")
            + "\n%s"
            + "User query message: "
            + ((sendYourQueryRequestDto.getMessage()!=null)?sendYourQueryRequestDto.getMessage():"");

    LOGGER.info("message body :: {}", message);
    EmailSendDto emailSendDto =
        new EmailSendDto(
            appContext.getEmailHostUsername(),
                "manishsinghonline2@gmail.com",
            Constants.COURSE_QUERY_EMAIl_SUBJECT,
            message);
    Boolean isEmailSent = emailService.sendSimpleMail(emailSendDto);
    if (!isEmailSent) {
      LOGGER.error("Email service is not working to send query to admin.");
      throw new UnProcessableEntityException("Unable to sent the query, please try again!");
    }
    return isEmailSent;
  }

  @Override
  public Boolean sendYourQueryToHelpAndSupport(HelpAndSupportDto helpAndSupportDto) {

    String message = "From Course: "+helpAndSupportDto.getCourseTitle()
            +"\n%s To question type: "+ helpAndSupportDto.getQuestionTitle()
            +"\n%s Description: "+ helpAndSupportDto.getDescription();

    LOGGER.info("Message body: {}", message);
    EmailSendDto emailSendDto = new EmailSendDto(
            appContext.getDefaultMailSenderId(),
            appContext.getDefaultSaleEmailId(),
            Constants.DEFAULT_HELP_AND_SUPPORT_SUBJECT,
            message
    );

    Boolean isEmailSent = emailService.sendSimpleMail(emailSendDto);
    if (!isEmailSent) {
      LOGGER.error("Email service is not working to send query to admin.");
      throw new UnProcessableEntityException("Unable to sent the query, please try again!");
    }
    return isEmailSent;
  }
}
