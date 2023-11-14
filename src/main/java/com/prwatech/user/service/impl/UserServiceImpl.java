package com.prwatech.user.service.impl;

import com.prwatech.common.dto.EmailSendDto;
import com.prwatech.common.exception.NotFoundException;
import com.prwatech.common.exception.UnProcessableEntityException;
import com.prwatech.common.service.impl.EmailServiceImpl;
import com.prwatech.courses.dto.MyDashboardActivity;
import com.prwatech.courses.service.MyCourseService;
import com.prwatech.user.dto.EducationUpdateDto;
import com.prwatech.user.dto.UserDetailsDto;
import com.prwatech.user.dto.UserProfileDto;
import com.prwatech.user.dto.UserProfileUpdateDto;
import com.prwatech.user.model.DeletedUser;
import com.prwatech.user.model.User;
import com.prwatech.user.model.UserEducationDetails;
import com.prwatech.user.repository.DeletedUserRepository;
import com.prwatech.user.repository.IamRepository;
import com.prwatech.user.repository.UserEducationDetailsRepository;
import com.prwatech.user.repository.UserRepository;
import com.prwatech.user.service.UserService;
import com.prwatech.user.template.EducationDetailsTemplates;
import java.util.Objects;
import lombok.AllArgsConstructor;
import org.bson.types.ObjectId;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import static com.prwatech.common.Constants.*;
import static com.prwatech.common.Constants.FORGET_PASSWORD_MAIL_BODY;

@Service
@AllArgsConstructor
public class UserServiceImpl implements UserService {

  private static final org.slf4j.Logger LOGGER =
      org.slf4j.LoggerFactory.getLogger(UserServiceImpl.class);

  private final UserRepository userRepository;
  private final DeletedUserRepository deletedUserRepository;
  private final ModelMapper modelMapper;
  private final MyCourseService myCourseService;
  private final EducationDetailsTemplates educationDetailsTemplates;
  private final UserEducationDetailsRepository educationDetailsRepository;
  private final IamRepository iamRepository;
  private final EmailServiceImpl emailService;
  @Override
  public UserDetailsDto getUserDetailsById(String id) {
    User user =
        userRepository
            .findById(id)
            .orElseThrow(() -> new NotFoundException("No user find with this id!"));

    UserDetailsDto userDetailsDto = modelMapper.map(user, UserDetailsDto.class);
    return userDetailsDto;
  }

  @Override
  public UserProfileDto getUserProfileById(String id) {
    User user =
        userRepository
            .findById(id)
            .orElseThrow(() -> new NotFoundException("No user find with this id!"));
    UserProfileDto userProfileDto = modelMapper.map(user, UserProfileDto.class);

    userProfileDto.setIsPhoneLoggedIn((user.getIsMobileRegistered())?Boolean.TRUE:Boolean.FALSE);
    userProfileDto.setEmail((user.getEmail()==null)?null: user.getEmail());
    MyDashboardActivity myDashboardActivity = myCourseService.getUserDashboardActivityByUserId(id);
    userProfileDto.setEducationDetails(educationDetailsTemplates.getByUserId(id));
    userProfileDto.setEnrolledCourse(myDashboardActivity.getEnrolledCourses());
    userProfileDto.setOnlineCourse(myDashboardActivity.getOnlineCourses());
    userProfileDto.setClassRoomCourse(myDashboardActivity.getClassroomCourses());
    userProfileDto.setCompletedCourse(myDashboardActivity.getCompletedCourses());
    return userProfileDto;
  }

  @Override
  public void updateUserProfile(String userId, UserProfileUpdateDto profileUpdateDto) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new NotFoundException("No user find with this id!"));
    if (Objects.isNull(profileUpdateDto)) {
      throw new UnProcessableEntityException("update data is empty or missing!");
    }

    if (!user.getIsMobileRegistered() && Objects.nonNull(profileUpdateDto.getPhoneNumber())) {
      user.setPhoneNumber(profileUpdateDto.getPhoneNumber());
    }


    if ( Objects.nonNull(profileUpdateDto.getEmail()) && user.getIsMobileRegistered()) {
           user.setEmail(profileUpdateDto.getEmail());

    }

    if(profileUpdateDto.getName()!=null){
      user.setName(profileUpdateDto.getName());
    }

    if (Objects.nonNull(profileUpdateDto.getGender())) {
      user.setGender(profileUpdateDto.getGender());
    }

    if (Objects.nonNull(profileUpdateDto.getDateOfBirth())) {
      user.setDateOfBirth(profileUpdateDto.getDateOfBirth());
    }

    if (Objects.nonNull(profileUpdateDto.getEducationUpdateDto())) {
      UserEducationDetails userEducationDetails ;
      EducationUpdateDto educationUpdateDto = profileUpdateDto.getEducationUpdateDto();

      if(Objects.nonNull(profileUpdateDto.getEducationUpdateDto().getId())){
        userEducationDetails = educationDetailsRepository.findById(educationUpdateDto.getId()).orElse(
                new UserEducationDetails());
      }
      else {
        userEducationDetails = new UserEducationDetails();
        userEducationDetails.setUser_Id(userId);
      }
      userEducationDetails.setInstituteName((educationUpdateDto.getInstituteName()!=null)?
          educationUpdateDto.getInstituteName():userEducationDetails.getInstituteName());

      userEducationDetails.setProgramName((educationUpdateDto.getProgramName())!=null?
          educationUpdateDto.getProgramName():userEducationDetails.getProgramName());

      userEducationDetails.setFieldOfStudy((educationUpdateDto.getFieldOfStudy()!=null)?
              educationUpdateDto.getFieldOfStudy():userEducationDetails.getFieldOfStudy());

      userEducationDetails.setStartTime((educationUpdateDto.getStartTime()!=null)?
              educationUpdateDto.getStartTime():userEducationDetails.getStartTime());

      userEducationDetails.setEndTime((educationUpdateDto.getEndTime()!=null)?
              educationUpdateDto.getEndTime():userEducationDetails.getEndTime());

      userEducationDetails.setIsCompleted(
          (Objects.nonNull(userEducationDetails.getEndTime())) ? Boolean.TRUE : Boolean.FALSE);
      educationDetailsRepository.save(userEducationDetails);
    }

    userRepository.save(user);
  }

  @Override
  public void deleteEducationDetailsById(String id) {
    educationDetailsRepository.deleteById(id);
  }

  @Override
  public String getUserReferalCodeByUserId(String id) {
    User user =
        userRepository
            .findById(id)
            .orElseThrow(() -> new NotFoundException("No user found by this id"));
    if(user.getReferal_Code()==null){
      Integer RF = iamRepository.findAll().size()+1;
      user.setReferal_Code(REFERAL_BIT_1+RF+REFERAL_BIT_2);
      user = iamRepository.save(user);
    }
    return user.getReferal_Code();
  }

  @Override
  public void deleteUserDetails(String id) {
    User user =
            userRepository
                    .findById(id)
                    .orElseThrow(() -> new NotFoundException("No user find with this id!"));
    if (user != null) {
      DeletedUser deletedUser = modelMapper.map(user, DeletedUser.class);
      deletedUserRepository.save(deletedUser);
    }
    userRepository.deleteById(id);

    if (user.getEmail()!=null) {
      EmailSendDto emailSendDto =
              new EmailSendDto(
                      user.getEmail(),
                      USER_ACCOUNT_DELETION_REQUEST,
                      USER_ACCOUNT_DELETION_MESSAGE.replace("XXXX", user.getName() != null ? user.getName() : "User"));

      emailService.sendEmail(emailSendDto);
    }
  }
}
