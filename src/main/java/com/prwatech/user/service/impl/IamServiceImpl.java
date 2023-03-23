package com.prwatech.user.service.impl;

import com.prwatech.common.exception.AlreadyPresentException;
import com.prwatech.common.exception.BadRequestException;
import com.prwatech.common.exception.NotFoundException;
import com.prwatech.common.exception.UnProcessableEntityException;
import com.prwatech.user.dto.SignInRequestDto;
import com.prwatech.user.dto.SignInResponseDto;
import com.prwatech.user.model.UserOtpMapping;
import com.prwatech.user.model.Users;
import com.prwatech.user.repository.IamMongodbTemplateLayer;
import com.prwatech.user.repository.IamRepository;
import com.prwatech.user.repository.UserOtpMappingTemplate;
import com.prwatech.user.service.IamService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

@Service
@Transactional
@AllArgsConstructor
public class IamServiceImpl implements IamService {

    private static final org.slf4j.Logger LOGGER =
            org.slf4j.LoggerFactory.getLogger(IamServiceImpl.class);

    private final IamRepository iamRepository;

    private final IamMongodbTemplateLayer iamMongodbTemplateLayer;
    private final UserOtpMappingTemplate userOtpMappingTemplate;
    @Override
    public SignInResponseDto signInWithPhoneOrEmailPassword(SignInRequestDto signInRequestDto, Boolean isSignUp) {

        if(Boolean.TRUE.equals(signInRequestDto.getIsEmailLogin())
                && (Objects.isNull(signInRequestDto.getEmail()) || Objects.isNull(signInRequestDto.getPassword()))){
            throw new BadRequestException("For Email and Password Sign in, email and password required.");
        }
        else if (Boolean.FALSE.equals(signInRequestDto) && Objects.isNull(signInRequestDto.getPhoneNumber())){
            throw new BadRequestException("For sign in with phone, phone number can not be null");
        }

        if(!isSignUp){
        return (Boolean.TRUE.equals(signInRequestDto.getIsEmailLogin()))
                ?signInWithEmailAndPassword(signInRequestDto)
                :signInWithPhoneNumber(signInRequestDto, isSignUp);
        }

        return (Boolean.TRUE.equals(isSignUp))
                ?signUpWithEmailAndPassword(signInRequestDto)
                :signInWithPhoneNumber(signInRequestDto, isSignUp);
    }

    private SignInResponseDto signInWithEmailAndPassword(SignInRequestDto signInRequestDto){

        Optional<Users> userObject = iamMongodbTemplateLayer.findByEmail(signInRequestDto.getEmail());

       if(!userObject.isPresent()){
           throw new NotFoundException("User not found with this email id : "+ signInRequestDto.getEmail());
       }

       if(userObject.get().getPassword().equals(signInRequestDto.getPassword())){
           throw new UnProcessableEntityException("Wrong password! Please give correct password.");
       }

       //TODO :: Generate token and return.

        return null;
    }

    private SignInResponseDto signUpWithEmailAndPassword(SignInRequestDto signInRequestDto){
        Optional<Users> userObject = iamMongodbTemplateLayer.findByEmail(signInRequestDto.getEmail());
        if(userObject.isPresent()){
            throw new AlreadyPresentException("This email id is already in use!");
        }

        //TODO :: create user, return token.
     return null;
    }

    private SignInResponseDto signInWithPhoneNumber(SignInRequestDto signInRequestDto, Boolean isSignUp){

        Optional<Users> userObject = iamMongodbTemplateLayer.findByMobile(signInRequestDto.getPhoneNumber());

        if(!userObject.isPresent() && !isSignUp){
            throw new NotFoundException("User not found with this phone number!");
        }
        else if(!userObject.isPresent() && isSignUp){
            //TODO :: create user.
        }

        //TODO :: send otp to user mobile number.

        return null;
    }

    @Override
    public SignInResponseDto verifyOtpOnSignInTime(Long phoneNumber, Long userId, Integer otp) {

        Optional<UserOtpMapping> otpMappingObject = userOtpMappingTemplate.findOtpMappingByUserIdAndPhone(userId, phoneNumber);
        if(!otpMappingObject.isPresent()){
            throw new NotFoundException("Wrong user id and phone number !");
        }

        if(otpMappingObject.get().getExpireAt().isBefore(LocalDateTime.now())){
            userOtpMappingTemplate.deleteOtpHistory(userId, phoneNumber);
            throw new UnProcessableEntityException("Your Otp time has been expired.");
        }

        if(otpMappingObject.get().getOtp()!=otp){
            throw new UnProcessableEntityException("Otp does not matched please try again.");
        }

        //TODO :: create token and send.
        return null;
    }
}
