package com.prwatech.user.controller;

import com.prwatech.common.dto.EmailSendResponseDto;
import com.prwatech.common.service.impl.EmailServiceImpl;
import com.prwatech.user.dto.GoogleSignInUpDto;
import com.prwatech.user.dto.SignInResponseDto;
import com.prwatech.user.dto.SignInSignUpRequestDto;
import com.prwatech.user.dto.UserOtpDto;
import com.prwatech.user.service.IamService;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import java.io.IOException;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/iam")
@AllArgsConstructor
public class IamController {

  private final IamService iamService;

  @ApiOperation(
      value = "Sign in/up user via email and password",
      notes = "Sign in/up user via email and password ")
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
  @ResponseStatus(value = HttpStatus.OK)
  @PutMapping("/sign/in/up")
  public SignInResponseDto signInUser(
      @Valid @RequestBody() SignInSignUpRequestDto signInSignUpRequestDto) {
    return iamService.signInUpWithEmailPassword(signInSignUpRequestDto);
  }

  @ApiOperation(
      value = "Sign in/up user via phone number ",
      notes = "Sign in/up user via phone number ")
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
  @ResponseStatus(value = HttpStatus.OK)
  @PostMapping("/sign/in/up/{phoneNumber}")
  public UserOtpDto signInUnWithPhoneNumber(
      @PathVariable(value = "phoneNumber") @NotNull Long phoneNumber) throws IOException {
    return iamService.singInUpWithPhoneNumber(phoneNumber);
  }

  @ApiOperation(
      value = "Verify otp for sign in and sign up. ",
      notes = "Verify otp for sign in and sign up ")
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
  @ResponseStatus(value = HttpStatus.OK)
  @PutMapping("/verify/otp/{userId}")
  public SignInResponseDto verifyOtpForSignInSignUp(
      @PathVariable(value = "userId") @NotNull String userId,
      @RequestParam(value = "otp") @NotNull Integer otp) {
    return iamService.verifyOtp(userId, otp);
  }

  @ApiOperation(value = "Resend Otp to user. ", notes = "Resend Otp to user. ")
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
  @ResponseStatus(value = HttpStatus.OK)
  @PutMapping("/resend/otp/{phoneNumber}")
  public UserOtpDto resendOtpToUser(
      @PathVariable(value = "phoneNumber") @NotNull Long phoneNumber,
      @RequestParam(value = "userId") @NotNull String userId)
      throws IOException {
    return iamService.reSendOtp(phoneNumber, userId);
  }

  private final EmailServiceImpl emailService;

  @ApiOperation(value = "Test api", notes = "test api ")
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
  @ResponseStatus(value = HttpStatus.OK)
  @PutMapping("/otp-message-test/api")
  public Integer signInUpUserViaGoogle() throws IOException {
    return emailService.sendMail("manishsinghonnline2@gmail.com", "testing mail", "test");
  }

  @ApiOperation(
      value = "Forget password for email and password user",
      notes = "Forget password for email and password user ")
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
  @ResponseStatus(value = HttpStatus.OK)
  @PutMapping("/forget-password")
  public EmailSendResponseDto forgetPassword(@RequestParam("emailId") @NotNull String emailId) {
    return iamService.sendEmailToForgetPassword(emailId);
  }

  @ApiOperation(
      value = "Reset password for email and password user",
      notes = "Reset password for email and password user ")
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
  @ResponseStatus(value = HttpStatus.OK)
  @PutMapping("/reset-password")
  public void resetPassword(
      @RequestParam(value = "otp") @NotNull Integer otp,
      @RequestParam(value = "userId") @NotNull String userId,
      @RequestParam(value = "newPassword") @NotNull String newPassword) {}

  @ApiOperation(
      value = "Sign up, Sign in with google account",
      notes = "Sign up, Sign in with google account")
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
  @ResponseStatus(value = HttpStatus.OK)
  @PutMapping("/sign-in/sign-up/google")
  public SignInResponseDto signInSignUpWithGoogle(
      @RequestBody @Valid GoogleSignInUpDto googleSignInUpDto) {
    return iamService.SignInSignUpWithGoogle(googleSignInUpDto);
  }
}
