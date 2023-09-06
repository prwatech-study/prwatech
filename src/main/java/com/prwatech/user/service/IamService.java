package com.prwatech.user.service;

import com.prwatech.user.dto.ForgetPasswordResponseDto;
import com.prwatech.user.dto.GoogleSignInUpDto;
import com.prwatech.user.dto.SignInResponseDto;
import com.prwatech.user.dto.SignInSignUpRequestDto;
import com.prwatech.user.dto.UserOtpDto;
import java.io.IOException;

public interface IamService {

  SignInResponseDto signInUpWithEmailPassword(SignInSignUpRequestDto signInSignUpRequestDto);

  UserOtpDto singInUpWithPhoneNumber(Long phoneNumber, String referalCode) throws IOException;

  SignInResponseDto verifyOtp(String userId, Integer otp);

  UserOtpDto reSendOtp(Long phoneNumber, String userId) throws IOException;

  ForgetPasswordResponseDto sendEmailToForgetPassword(String emailId);

  Boolean resetPassword(String userId, String newPassword, Integer otp);

  SignInResponseDto SignInSignUpWithGoogle(GoogleSignInUpDto googleSignInUpDto);
}
