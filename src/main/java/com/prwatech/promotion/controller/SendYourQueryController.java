package com.prwatech.promotion.controller;

import com.prwatech.common.dto.EmailSendDto;
import com.prwatech.common.service.impl.EmailServiceImpl;
import com.prwatech.promotion.dto.HelpAndSupportDto;
import com.prwatech.promotion.dto.SendYourQueryRequestDto;
import com.prwatech.promotion.service.SendYourQueryService;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import javax.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public")
@AllArgsConstructor
public class SendYourQueryController {

  private final SendYourQueryService sendYourQueryService;
  private final EmailServiceImpl emailService;

  @ApiOperation(
      value = "Send query to prwatech sales for contact",
      notes = "Send query to prwatech sales for contact")
  @ApiResponses(
      value = {
        @ApiResponse(code = 200, message = "Success"),
        @ApiResponse(code = 400, message = "Not Available"),
        @ApiResponse(code = 401, message = "UnAuthorized"),
        @ApiResponse(code = 403, message = "Access Forbidden"),
        @ApiResponse(code = 404, message = "Not found"),
        @ApiResponse(code = 422, message = "UnProcessable entity"),
        @ApiResponse(code = 500, message = "Internal server error"),
      })
  @PutMapping("/send-query/")
  public Boolean sendYourQuery(
      @RequestBody @Valid SendYourQueryRequestDto sendYourQueryRequestDto) {
    return sendYourQueryService.sendYourQueryForCourse(sendYourQueryRequestDto);
  }

  @ApiOperation(
          value = "help and support.",
          notes = "help and support.")
  @ApiResponses(
          value = {
                  @ApiResponse(code = 200, message = "Success"),
                  @ApiResponse(code = 400, message = "Not Available"),
                  @ApiResponse(code = 401, message = "UnAuthorized"),
                  @ApiResponse(code = 403, message = "Access Forbidden"),
                  @ApiResponse(code = 404, message = "Not found"),
                  @ApiResponse(code = 422, message = "UnProcessable entity"),
                  @ApiResponse(code = 500, message = "Internal server error"),
          })
  @PutMapping("/help-support")
  public Boolean sendYourQueryToHelpAndSupport(
          @RequestBody @Valid HelpAndSupportDto helpAndSupportDto) {
    return sendYourQueryService.sendYourQueryToHelpAndSupport(helpAndSupportDto);
  }

  @ApiOperation(
          value = "help and support test.",
          notes = "help and support test.")
  @ApiResponses(
          value = {
                  @ApiResponse(code = 200, message = "Success"),
                  @ApiResponse(code = 400, message = "Not Available"),
                  @ApiResponse(code = 401, message = "UnAuthorized"),
                  @ApiResponse(code = 403, message = "Access Forbidden"),
                  @ApiResponse(code = 404, message = "Not found"),
                  @ApiResponse(code = 422, message = "UnProcessable entity"),
                  @ApiResponse(code = 500, message = "Internal server error"),
          })
  @PutMapping("/email-test")
  public void sendEmailTest(
          @RequestBody EmailSendDto emailSendDto) {
          emailService.sendEmail(emailSendDto);
  }
}
