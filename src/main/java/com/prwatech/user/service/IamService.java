package com.prwatech.user.service;

import com.prwatech.common.dto.EmailSendResponseDto;
import com.prwatech.user.dto.GoogleSignInUpDto;
import com.prwatech.user.dto.SignInResponseDto;
import com.prwatech.user.dto.SignInSignUpRequestDto;
import com.prwatech.user.dto.UserOtpDto;
import java.io.IOException;

public interface IamService {

  SignInResponseDto signInUpWithEmailPassword(SignInSignUpRequestDto signInSignUpRequestDto);

  UserOtpDto singInUpWithPhoneNumber(Long phoneNumber) throws IOException;

  SignInResponseDto verifyOtp(String userId, Integer otp);

  UserOtpDto reSendOtp(Long phoneNumber, String userId) throws IOException;

  EmailSendResponseDto sendEmailToForgetPassword(String emailId);

  SignInResponseDto SignInSignUpWithGoogle(GoogleSignInUpDto googleSignInUpDto);
}
