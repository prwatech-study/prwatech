package com.prwatech.user.service.impl;

import static com.prwatech.common.Constants.FORGET_PASSWORD_MAIL_BODY;
import static com.prwatech.common.Constants.FORGET_PASSWORD_MAIL_SUBJECT;
import static com.prwatech.common.Constants.SUCCESSFUL;

import com.prwatech.authentication.security.JwtUtils;
import com.prwatech.common.Constants;
import com.prwatech.common.configuration.AppContext;
import com.prwatech.common.configuration.PasswordEncode;
import com.prwatech.common.dto.EmailSendDto;
import com.prwatech.common.dto.SmsSendDto;
import com.prwatech.common.dto.UserDetails;
import com.prwatech.common.exception.BadRequestException;
import com.prwatech.common.exception.ForbiddenException;
import com.prwatech.common.exception.NotFoundException;
import com.prwatech.common.exception.UnProcessableEntityException;
import com.prwatech.common.service.SmsSendService;
import com.prwatech.common.service.impl.EmailServiceImpl;
import com.prwatech.common.utility.Utility;
import com.prwatech.user.dto.ForgetPasswordResponseDto;
import com.prwatech.user.dto.GoogleSignInUpDto;
import com.prwatech.user.dto.SignInResponseDto;
import com.prwatech.user.dto.SignInSignUpRequestDto;
import com.prwatech.user.dto.UserOtpDto;
import com.prwatech.user.model.User;
import com.prwatech.user.model.UserOtpMapping;
import com.prwatech.user.repository.IamRepository;
import com.prwatech.user.repository.UserOtpMappingRepository;
import com.prwatech.user.service.IamService;
import com.prwatech.user.service.UserService;
import com.prwatech.user.template.IamMongodbTemplateLayer;
import com.prwatech.user.template.UserOtpMappingTemplate;
import java.io.IOException;
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
  private final JwtUtils jwtUtils;
  private final UserOtpMappingRepository userOtpMappingRepository;
  private final SmsSendService smsSendService;
  private final AppContext appContext;
  private final UserService userService;
  private final EmailServiceImpl emailService;

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
  public UserOtpDto singInUpWithPhoneNumber(Long phoneNumber) throws IOException {
    if (Objects.isNull(phoneNumber)) {
      throw new UnProcessableEntityException("Phone number can not be null!");
    }
    return signUpWithPhoneNumber(phoneNumber);
  }

  private SignInResponseDto signInWithEmailAndPassword(
      SignInSignUpRequestDto signInSignUpRequestDto) {

    Optional<User> userObject =
        iamMongodbTemplateLayer.findByEmail(signInSignUpRequestDto.getEmail());

    if (userObject.isEmpty()) {
      throw new NotFoundException(
          "User not found with this email id : " + signInSignUpRequestDto.getEmail());
    }
    if (userObject.get().getIsGoogleSignedIn().equals(Boolean.TRUE)) {
      throw new UnProcessableEntityException(
          "This email is associated with google account! Please proceed with Google Sign in!");
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
    signInResponseDto.setUserId(user.getId());
    signInResponseDto.setAccessToken(jwtToken.get("accessToken"));
    signInResponseDto.setExpiresIn(LocalDateTime.now().plusMinutes(60));
    signInResponseDto.setRefreshToken(jwtToken.get("refreshToken"));
    signInResponseDto.setRefreshTokenExpiresIn(LocalDateTime.now().plusMinutes(65));
    signInResponseDto.setUserDetailsDto(userService.getUserDetailsById(user.getId()));

    return signInResponseDto;
  }

  private SignInResponseDto signUpWithEmailAndPassword(
      SignInSignUpRequestDto signInSignUpRequestDto) {
    Optional<User> userObject =
        iamMongodbTemplateLayer.findByEmail(signInSignUpRequestDto.getEmail());
    if (userObject.isPresent() || !userObject.isEmpty()) {
      throw new UnProcessableEntityException("This email is already in use!");
    }

    if (userObject.isPresent() && userObject.get().getIsGoogleSignedIn().equals(Boolean.TRUE)) {
      throw new UnProcessableEntityException("This email is already in use!");
    }

    signInSignUpRequestDto.setPassword(
        passwordEncode.getEncryptedPassword(signInSignUpRequestDto.getPassword()));
    User user = new User();
    user.setEmail(signInSignUpRequestDto.getEmail());
    user.setPassword(signInSignUpRequestDto.getPassword());
    user.setIsGoogleSignedIn(Boolean.FALSE);
    user.setDisable(Boolean.FALSE);
    user.setLastLogin(LocalDateTime.now());
    iamRepository.save(user);

    UserDetails userDetails = new UserDetails();
    userDetails.setUsername(user.getEmail());
    Map<String, String> jwtToken = jwtUtils.generateToken(userDetails);

    SignInResponseDto signInResponseDto = new SignInResponseDto();
    signInResponseDto.setUserId(user.getId());
    signInResponseDto.setAccessToken(jwtToken.get("accessToken"));
    signInResponseDto.setExpiresIn(LocalDateTime.now().plusMinutes(60));
    signInResponseDto.setRefreshToken(jwtToken.get("refreshToken"));
    signInResponseDto.setRefreshTokenExpiresIn(LocalDateTime.now().plusMinutes(65));
    signInResponseDto.setUserDetailsDto(userService.getUserDetailsById(user.getId()));
    return signInResponseDto;
  }

  private UserOtpDto signUpWithPhoneNumber(Long phoneNumber) throws IOException {
    Optional<User> userObject = iamMongodbTemplateLayer.findByMobile(phoneNumber);
    User user = new User();
    if (!userObject.isPresent() && userObject.isEmpty()) {

      user.setPhoneNumber(phoneNumber);
      user.setEmail(null);
      user.setDisable(Boolean.FALSE);
      user.setIsMobileRegistered(Boolean.TRUE);
      user = iamRepository.save(user);
    } else {
      user = userObject.get();
    }

    Integer otp = Utility.createRandomOtp();
    SmsSendDto smsSendDto =
        new SmsSendDto(
            otp.toString(),
            Constants.FTSMS_ROUTE,
            Constants.DEFAULT_LANGUAGE,
            Constants.DEFAULT_FLASH,
            phoneNumber.toString());

    Boolean isSmsSent = smsSendService.sendDefaultOtpMessage(smsSendDto);

    if (!isSmsSent.equals(Boolean.TRUE)) {
      throw new UnProcessableEntityException(
          "Please put correct phone number or try with other phone number.");
    }

    Optional<UserOtpMapping> otpMappingObject =
        userOtpMappingTemplate.findOtpMappingByUserId(user.getId());
    if (!otpMappingObject.isEmpty()) {
      userOtpMappingRepository.deleteById(otpMappingObject.get().getId());
    }

    UserOtpMapping userOtpMapping = new UserOtpMapping();
    userOtpMapping.setUserId(user.getId());
    userOtpMapping.setPhoneNumber(phoneNumber);
    userOtpMapping.setOtp(otp);
    userOtpMapping.setExpireAt(LocalDateTime.now().plusMinutes(2));

    userOtpMapping = userOtpMappingRepository.save(userOtpMapping);

    return new UserOtpDto(user.getId(), userOtpMapping.getId(), phoneNumber);
  }

  private UserOtpDto signInWithPhoneNumber(Long phoneNumber) throws IOException {

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
            otp.toString(),
            Constants.FTSMS_ROUTE,
            Constants.DEFAULT_LANGUAGE,
            Constants.DEFAULT_FLASH,
            phoneNumber.toString());

    Boolean isSmsSent = smsSendService.sendDefaultOtpMessage(smsSendDto);
    if (!isSmsSent.equals(Boolean.TRUE)) {
      throw new UnProcessableEntityException(
          "Please put correct phone number or try with other phone number.");
    }

    Optional<UserOtpMapping> otpMappingObject =
        userOtpMappingTemplate.findOtpMappingByUserId(user.getId());
    if (!otpMappingObject.isEmpty()) {
      userOtpMappingRepository.deleteById(otpMappingObject.get().getId());
    }

    UserOtpMapping userOtpMapping = new UserOtpMapping();
    userOtpMapping.setUserId(user.getId());
    userOtpMapping.setPhoneNumber(phoneNumber);
    userOtpMapping.setOtp(otp);
    userOtpMapping.setExpireAt(LocalDateTime.now().plusMinutes(2));

    userOtpMapping = userOtpMappingRepository.save(userOtpMapping);

    return new UserOtpDto(user.getId(), userOtpMapping.getId(), phoneNumber);
  }

  @Override
  public SignInResponseDto verifyOtp(String userId, Integer otp) {

    Optional<UserOtpMapping> otpMappingObject =
        userOtpMappingTemplate.findOtpMappingByUserId(userId);
    if (otpMappingObject.isEmpty() || !otpMappingObject.isPresent()) {
      throw new NotFoundException("Wrong otp and phone number !");
    }

    UserOtpMapping userOtpMapping = otpMappingObject.get();

    if (userOtpMapping.getExpireAt().isBefore(LocalDateTime.now())) {
      userOtpMappingRepository.deleteById(userOtpMapping.getId());
      throw new UnProcessableEntityException("Your Otp time has been expired.");
    }

    if (!userOtpMapping.getOtp().equals(otp)) {
      throw new UnProcessableEntityException("Otp does not matched please try again.");
    }

    userOtpMappingRepository.delete(userOtpMapping);
    Optional<User> usersObject =
        iamMongodbTemplateLayer.findByMobile(userOtpMapping.getPhoneNumber());

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
        LocalDateTime.now().plusMinutes(65),
        user.getId(),
        userService.getUserDetailsById(userId));
  }

  @Override
  public UserOtpDto reSendOtp(Long phoneNumber, String userId) throws IOException {
    Optional<UserOtpMapping> otpMappingObject =
        userOtpMappingTemplate.findOtpMappingByUserId(userId);

    if (otpMappingObject.isEmpty()) {
      throw new NotFoundException("No otp mapping found with this number.");
    }

    UserOtpMapping userOtpMapping = otpMappingObject.get();
    Integer otp = Utility.createRandomOtp();
    SmsSendDto smsSendDto =
        new SmsSendDto(
            otp.toString(),
            Constants.FTSMS_ROUTE,
            Constants.DEFAULT_LANGUAGE,
            Constants.DEFAULT_FLASH,
            phoneNumber.toString());

    Boolean isSmsSent = smsSendService.sendDefaultOtpMessage(smsSendDto);
    if (!isSmsSent.equals(Boolean.TRUE)) {
      throw new UnProcessableEntityException(
          "Please put correct phone number or try with other phone number.");
    }
    userOtpMapping.setOtp(otp);
    userOtpMapping.setExpireAt(LocalDateTime.now().plusMinutes(2));

    userOtpMapping = userOtpMappingRepository.save(userOtpMapping);

    return new UserOtpDto(userOtpMapping.getUserId(), userOtpMapping.getId(), phoneNumber);
  }

  @Override
  public ForgetPasswordResponseDto sendEmailToForgetPassword(String emailId) {
    Optional<User> userObject = iamMongodbTemplateLayer.findByEmail(emailId);
    if (!userObject.isPresent() || userObject.isEmpty()) {
      throw new NotFoundException("No user found by this email id!");
    }

    if (Objects.isNull(userObject.get().getPassword()) || userObject.get().getIsGoogleSignedIn()) {
      throw new UnProcessableEntityException(
          "You have not created account via email and password to this application!");
    }

    Integer otp = Utility.createRandomOtp();

    EmailSendDto emailSendDto =
        new EmailSendDto(
            appContext.getDefaultMailSenderId(),
            emailId,
            FORGET_PASSWORD_MAIL_SUBJECT,
            FORGET_PASSWORD_MAIL_BODY + otp);

    Boolean isEmailSent = emailService.sendNormalEmailWithPlanText(emailSendDto);

    if (!isEmailSent) {
      throw new UnProcessableEntityException(
          "Something went wrong, Please try again to reset password! ");
    }

    Optional<UserOtpMapping> userOtpMappingObject =
        userOtpMappingTemplate.findOtpMappingByUserId(userObject.get().getId());
    if (userOtpMappingObject.isPresent() && !userOtpMappingObject.isEmpty()) {
      userOtpMappingRepository.deleteById(userOtpMappingObject.get().getId());
    }
    UserOtpMapping userOtpMapping = new UserOtpMapping();

    userOtpMapping.setUserId(userObject.get().getId());
    userOtpMapping.setOtp(otp);
    userOtpMapping.setExpireAt(LocalDateTime.now().plusMinutes(5));

    userOtpMappingRepository.save(userOtpMapping);

    return new ForgetPasswordResponseDto(
        userObject.get().getId(), emailId, SUCCESSFUL, Boolean.TRUE);
  }

  @Override
  public SignInResponseDto SignInSignUpWithGoogle(GoogleSignInUpDto googleSignInUpDto) {

    Optional<User> userObject = iamMongodbTemplateLayer.findByEmail(googleSignInUpDto.getEmail());

    if (!userObject.isEmpty() && !userObject.get().getIsGoogleSignedIn()) {
      throw new UnProcessableEntityException("This email is already in use!");
    }

    User user = new User();
    if (!userObject.isEmpty()) {
      user = userObject.get();
    } else {
      user.setEmail(googleSignInUpDto.getEmail());
      user.setName(googleSignInUpDto.getName());
      user.setProfileImage(googleSignInUpDto.getImageUrl());
      user.setIsProfileImageUploaded(Boolean.TRUE);
      user.setIsGoogleSignedIn(Boolean.TRUE);
      user.setDisable(Boolean.FALSE);
    }

    user.setLastLogin(LocalDateTime.now());
    iamRepository.save(user);

    UserDetails userDetails = new UserDetails();
    userDetails.setUsername(user.getEmail());
    Map<String, String> jwtToken = jwtUtils.generateToken(userDetails);

    SignInResponseDto signInResponseDto = new SignInResponseDto();
    signInResponseDto.setUserId(user.getId());
    signInResponseDto.setAccessToken(jwtToken.get("accessToken"));
    signInResponseDto.setExpiresIn(LocalDateTime.now().plusMinutes(60));
    signInResponseDto.setRefreshToken(jwtToken.get("refreshToken"));
    signInResponseDto.setRefreshTokenExpiresIn(LocalDateTime.now().plusMinutes(65));
    signInResponseDto.setUserDetailsDto(userService.getUserDetailsById(user.getId()));

    return signInResponseDto;
  }

  @Override
  public Boolean resetPassword(String userId, String newPassword, Integer otp) {

    Optional<User> userObject = iamRepository.findById(userId);
    if (!userObject.isPresent() || userObject.isEmpty()) {
      throw new NotFoundException("No user found by this userid!");
    }

    Optional<UserOtpMapping> userOtpMappingObject =
        userOtpMappingTemplate.findOtpMappingByUserId(userId);
    if (!userOtpMappingObject.isPresent() || userOtpMappingObject.isEmpty()) {
      throw new NotFoundException("No otp found in database with this user!");
    }

    if (userOtpMappingObject.get().getExpireAt().isBefore(LocalDateTime.now())) {
      throw new UnProcessableEntityException("Otp has been expired!");
    }

    if (!userOtpMappingObject.get().getOtp().equals(otp)) {
      throw new UnProcessableEntityException("Wrong Otp! Please provide correct otp!");
    }

    User user = userObject.get();
    user.setPassword(passwordEncode.getEncryptedPassword(newPassword));
    iamRepository.save(user);

    userOtpMappingRepository.deleteById(userOtpMappingObject.get().getId());

    return Boolean.TRUE;
  }
}
