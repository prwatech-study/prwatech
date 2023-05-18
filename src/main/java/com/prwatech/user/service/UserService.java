package com.prwatech.user.service;

import com.prwatech.user.dto.UserDetailsDto;
import com.prwatech.user.dto.UserProfileDto;
import com.prwatech.user.dto.UserProfileUpdateDto;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public interface UserService {

  UserDetailsDto getUserDetailsById(String id);

  UserProfileDto getUserProfileById(String id);

  void updateUserProfile(String userId, UserProfileUpdateDto profileUpdateDto);

  void deleteEducationDetailsById(String id);

  String getUserReferalCodeByUserId(String id);
}
