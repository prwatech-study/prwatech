package com.prwatech.user.service;

import com.prwatech.user.dto.SignInResponseDto;
import com.prwatech.user.dto.SignInSignUpRequestDto;
import com.prwatech.user.dto.UserOtpDto;

public interface IamService {

  SignInResponseDto signInUpWithEmailPassword(SignInSignUpRequestDto signInSignUpRequestDto);

  UserOtpDto singInUpWithPhoneNumber(Long phoneNumber, Boolean isSingUp);

  SignInResponseDto verifyOtp(String userId, Integer otp);

  UserOtpDto reSendOtp(Long phoneNumber, String userId);
}
