package com.prwatech.user.service.impl;

import com.prwatech.common.exception.NotFoundException;
import com.prwatech.common.exception.UnProcessableEntityException;
import com.prwatech.courses.dto.MyDashboardActivity;
import com.prwatech.courses.service.MyCourseService;
import com.prwatech.user.dto.EducationUpdateDto;
import com.prwatech.user.dto.UserDetailsDto;
import com.prwatech.user.dto.UserProfileDto;
import com.prwatech.user.dto.UserProfileUpdateDto;
import com.prwatech.user.model.User;
import com.prwatech.user.model.UserEducationDetails;
import com.prwatech.user.repository.UserEducationDetailsRepository;
import com.prwatech.user.repository.UserRepository;
import com.prwatech.user.service.UserService;
import com.prwatech.user.template.EducationDetailsTemplates;
import java.util.Objects;
import lombok.AllArgsConstructor;
import org.bson.types.ObjectId;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class UserServiceImpl implements UserService {

  private static final org.slf4j.Logger LOGGER =
      org.slf4j.LoggerFactory.getLogger(UserServiceImpl.class);

  private final UserRepository userRepository;
  private final ModelMapper modelMapper;
  private final MyCourseService myCourseService;
  private final EducationDetailsTemplates educationDetailsTemplates;
  private final UserEducationDetailsRepository educationDetailsRepository;

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

//    if (Objects.nonNull(profileUpdateDto.getPhoneNumber())) {
//      user.setPhoneNumber(profileUpdateDto.getPhoneNumber());
//    }
//
//    if (Objects.nonNull(profileUpdateDto.getEmail())) {
//      user.setEmail(profileUpdateDto.getEmail());
//    }

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
    return user.getReferal_Code();
  }
}
