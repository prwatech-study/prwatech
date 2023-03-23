package com.prwatech.user.service;

import com.prwatech.user.dto.SignInRequestDto;
import com.prwatech.user.dto.SignInResponseDto;

public interface IamService {

    SignInResponseDto signInWithPhoneOrEmailPassword(SignInRequestDto signInRequestDto, Boolean isSignUp);

    SignInResponseDto verifyOtpOnSignInTime(Long phoneNumber, Long userId, Integer otp);

}
