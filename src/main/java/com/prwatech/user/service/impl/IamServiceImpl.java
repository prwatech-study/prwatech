package com.prwatech.user.service.impl;

import com.prwatech.authentication.security.JwtUtils;
import com.prwatech.common.Constants;
import com.prwatech.common.configuration.PasswordEncode;
import com.prwatech.common.dto.SmsSendDto;
import com.prwatech.common.dto.UserDetails;
import com.prwatech.common.exception.AlreadyPresentException;
import com.prwatech.common.exception.BadRequestException;
import com.prwatech.common.exception.ForbiddenException;
import com.prwatech.common.exception.NotFoundException;
import com.prwatech.common.exception.UnProcessableEntityException;
import com.prwatech.common.service.SmsSendService;
import com.prwatech.common.utility.Utility;
import com.prwatech.user.dto.SignInResponseDto;
import com.prwatech.user.dto.SignInSignUpRequestDto;
import com.prwatech.user.dto.UserOtpDto;
import com.prwatech.user.model.User;
import com.prwatech.user.model.UserOtpMapping;
import com.prwatech.user.repository.IamRepository;
import com.prwatech.user.repository.UserOtpMappingRepository;
import com.prwatech.user.service.IamService;
import com.prwatech.user.template.IamMongodbTemplateLayer;
import com.prwatech.user.template.UserOtpMappingTemplate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@AllArgsConstructor
public class IamServiceImpl implements IamService {

  private static final org.slf4j.Logger LOGGER =
      org.slf4j.LoggerFactory.getLogger(IamServiceImpl.class);

  private final IamRepository iamRepository;
  private final PasswordEncode passwordEncode;
  private final IamMongodbTemplateLayer iamMongodbTemplateLayer;
  private final UserOtpMappingTemplate userOtpMappingTemplate;
  // private final ModelMapper modelMapper;
  private final JwtUtils jwtUtils;
  private final UserOtpMappingRepository userOtpMappingRepository;
  private final SmsSendService smsSendService;

  @Override
  public SignInResponseDto signInUpWithEmailPassword(
      SignInSignUpRequestDto signInSignUpRequestDto) {

    if (Objects.isNull(signInSignUpRequestDto.getEmail())
        || Objects.isNull(signInSignUpRequestDto.getPassword())) {
      throw new BadRequestException("For Email and Password Sign in, email and password required.");
    }

    if (signInSignUpRequestDto.getIsSignUp()) {
      return signUpWithEmailAndPassword(signInSignUpRequestDto);
    }

    return signInWithEmailAndPassword(signInSignUpRequestDto);
  }

  @Override
  public UserOtpDto singInUpWithPhoneNumber(Long phoneNumber, Boolean isSingUp) {
    if (Objects.isNull(phoneNumber)) {
      throw new UnProcessableEntityException("Phone number can not be null!");
    }
    return (isSingUp) ? signUpWithPhoneNumber(phoneNumber) : signInWithPhoneNumber(phoneNumber);
  }

  private SignInResponseDto signInWithEmailAndPassword(
      SignInSignUpRequestDto signInSignUpRequestDto) {

    Optional<User> userObject =
        iamMongodbTemplateLayer.findByEmail(signInSignUpRequestDto.getEmail());

    if (userObject.isEmpty()) {
      throw new NotFoundException(
          "User not found with this email id : " + signInSignUpRequestDto.getEmail());
    }
    User user = userObject.get();

    if (user.getDisable()) {
      throw new ForbiddenException("Your Account has been disabled !");
    }

    //    if (user.getIsPasswordReset().equals(Boolean.FALSE)) {
    //      // TODO :: ask to log in with mobile and otp.
    //    }

    if (!passwordEncode
        .getEncryptedPassword(signInSignUpRequestDto.getPassword())
        .equals(user.getPassword())) {
      throw new UnProcessableEntityException("Wrong password provided!");
    }

    user.setLastLogin(LocalDateTime.now());
    iamRepository.save(user);

    UserDetails userDetails = new UserDetails(user.getEmail());

    Map<String, String> jwtToken = jwtUtils.generateToken(userDetails);

    SignInResponseDto signInResponseDto = new SignInResponseDto();
    signInResponseDto.setAccessToken(jwtToken.get("accessToken"));
    signInResponseDto.setExpiresIn(LocalDateTime.now().plusMinutes(60));
    signInResponseDto.setRefreshToken(jwtToken.get("refreshToken"));
    signInResponseDto.setRefreshTokenExpiresIn(LocalDateTime.now().plusMinutes(65));

    return signInResponseDto;
  }

  private SignInResponseDto signUpWithEmailAndPassword(
      SignInSignUpRequestDto signInSignUpRequestDto) {
    Optional<User> userObject =
        iamMongodbTemplateLayer.findByEmail(signInSignUpRequestDto.getEmail());
    if (!userObject.isEmpty()) {
      throw new AlreadyPresentException("This email id is already in use!");
    }

    signInSignUpRequestDto.setPassword(
        passwordEncode.getEncryptedPassword(signInSignUpRequestDto.getPassword()));
    User user = new User();
    user.setEmail(signInSignUpRequestDto.getEmail());
    user.setPassword(signInSignUpRequestDto.getPassword());
    user.setDisable(Boolean.FALSE);
    iamRepository.save(user);

    UserDetails userDetails = new UserDetails();
    userDetails.setUsername(user.getEmail());
    Map<String, String> jwtToken = jwtUtils.generateToken(userDetails);

    SignInResponseDto signInResponseDto = new SignInResponseDto();
    signInResponseDto.setAccessToken(jwtToken.get("accessToken"));
    signInResponseDto.setExpiresIn(LocalDateTime.now().plusMinutes(60));
    signInResponseDto.setRefreshToken(jwtToken.get("refreshToken"));
    signInResponseDto.setRefreshTokenExpiresIn(LocalDateTime.now().plusMinutes(65));

    return signInResponseDto;
  }

  private UserOtpDto signUpWithPhoneNumber(Long phoneNumber) {
    Optional<User> userObject = iamMongodbTemplateLayer.findByMobile(phoneNumber);
    User user = new User();
    if (!userObject.isEmpty()) {
      user = userObject.get();
    }

    user.setPhoneNumber(phoneNumber);
    user.setDisable(Boolean.FALSE);
    user = iamRepository.save(user);

    Integer otp = Utility.createRandomOtp();
    SmsSendDto smsSendDto =
        new SmsSendDto(
            Constants.DEFAULT_OTP_SMS_BODY + otp,
            Constants.FTSMS_ROUTE,
            Constants.DEFAULT_LANGUAGE,
            Constants.DEFAULT_FLASH,
            phoneNumber.toString());

    UserOtpMapping userOtpMapping = new UserOtpMapping();
    userOtpMapping.setUserid(user.getId());
    userOtpMapping.setPhoneNumber(phoneNumber);
    userOtpMapping.setOtp(otp);
    userOtpMapping.setExpireAt(LocalDateTime.now().plusMinutes(2));

    userOtpMappingRepository.save(userOtpMapping);

    Boolean isSmsSent = smsSendService.sendSmsToPhoneNumber(smsSendDto);
    if (!isSmsSent.equals(Boolean.TRUE)) {
      throw new UnProcessableEntityException(
          "Please put correct phone number or try with other phone number.");
    }

    return new UserOtpDto(user.getId(), phoneNumber);
  }

  private UserOtpDto signInWithPhoneNumber(Long phoneNumber) {

    Optional<User> userObject = iamMongodbTemplateLayer.findByMobile(phoneNumber);

    if (userObject.isEmpty()) {
      throw new NotFoundException("User not found with this phone number!");
    }

    User user = userObject.get();

    if (user.getDisable()) {
      throw new ForbiddenException("User account is disabled!");
    }

    Integer otp = Utility.createRandomOtp();
    SmsSendDto smsSendDto =
        new SmsSendDto(
            Constants.DEFAULT_OTP_SMS_BODY + otp,
            Constants.FTSMS_ROUTE,
            Constants.DEFAULT_LANGUAGE,
            Constants.DEFAULT_FLASH,
            phoneNumber.toString());

    UserOtpMapping userOtpMapping = new UserOtpMapping();
    userOtpMapping.setUserid(user.getId());
    userOtpMapping.setPhoneNumber(phoneNumber);
    userOtpMapping.setOtp(otp);
    userOtpMapping.setExpireAt(LocalDateTime.now().plusMinutes(2));

    userOtpMappingRepository.save(userOtpMapping);

    Boolean isSmsSent = smsSendService.sendSmsToPhoneNumber(smsSendDto);
    if (!isSmsSent.equals(Boolean.TRUE)) {
      throw new UnProcessableEntityException(
          "Please put correct phone number or try with other phone number.");
    }

    return new UserOtpDto(user.getId(), phoneNumber);
  }

  @Override
  public SignInResponseDto verifyOtp(Long phoneNumber, String userId, Integer otp) {

    Optional<UserOtpMapping> otpMappingObject =
        userOtpMappingTemplate.findOtpMappingByUserIdAndPhone(userId, phoneNumber);
    if (otpMappingObject.isEmpty()) {
      throw new NotFoundException("Wrong otp and phone number !");
    }

    UserOtpMapping userOtpMapping = otpMappingObject.get();

    if (userOtpMapping.getExpireAt().isBefore(LocalDateTime.now())) {
      userOtpMappingTemplate.deleteOtpHistory(userId, phoneNumber);
      throw new UnProcessableEntityException("Your Otp time has been expired.");
    }

    if (userOtpMapping.getOtp() != otp) {
      throw new UnProcessableEntityException("Otp does not matched please try again.");
    }

    userOtpMappingRepository.delete(userOtpMapping);
    Optional<User> usersObject = iamMongodbTemplateLayer.findByMobile(phoneNumber);

    User user = usersObject.get();
    user.setLastLogin(LocalDateTime.now());
    iamRepository.save(user);

    UserDetails userDetails = new UserDetails();
    userDetails.setUsername(Constants.DEFAULT_EMAIL);

    Map<String, String> jwtToken = jwtUtils.generateToken(userDetails);
    return new SignInResponseDto(
        jwtToken.get("accessToken"),
        jwtToken.get("refreshToken"),
        LocalDateTime.now().plusMinutes(60),
        LocalDateTime.now().plusMinutes(65));
  }

  @Override
  public UserOtpDto reSendOtp(Long phoneNumber, String userId) {
    Optional<UserOtpMapping> otpMappingObject =
        userOtpMappingTemplate.findOtpMappingByUserIdAndPhone(userId, phoneNumber);

    if (otpMappingObject.isEmpty()) {
      throw new NotFoundException("No otp mapping found with this number.");
    }

    UserOtpMapping userOtpMapping = otpMappingObject.get();
    Integer otp = Utility.createRandomOtp();
    SmsSendDto smsSendDto =
        new SmsSendDto(
            Constants.DEFAULT_OTP_SMS_BODY + otp,
            Constants.FTSMS_ROUTE,
            Constants.DEFAULT_LANGUAGE,
            Constants.DEFAULT_FLASH,
            phoneNumber.toString());

    userOtpMapping.setOtp(otp);
    userOtpMapping.setExpireAt(LocalDateTime.now().plusMinutes(2));

    userOtpMappingRepository.save(userOtpMapping);

    Boolean isSmsSent = smsSendService.sendSmsToPhoneNumber(smsSendDto);
    if (!isSmsSent.equals(Boolean.TRUE)) {
      throw new UnProcessableEntityException(
          "Please put correct phone number or try with other phone number.");
    }

    return new UserOtpDto(userOtpMapping.getUserid(), phoneNumber);
  }
}
