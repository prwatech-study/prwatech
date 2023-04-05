package com.prwatech.user.service;

import com.prwatech.user.dto.UserDetailsDto;
import org.springframework.stereotype.Component;

@Component
public interface UserService {

  UserDetailsDto getUserDetailsById(String id);
}
