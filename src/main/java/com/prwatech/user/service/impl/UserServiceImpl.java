package com.prwatech.user.service.impl;

import com.prwatech.common.exception.NotFoundException;
import com.prwatech.user.dto.UserDetailsDto;
import com.prwatech.user.model.User;
import com.prwatech.user.repository.UserRepository;
import com.prwatech.user.service.UserService;
import lombok.AllArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class UserServiceImpl implements UserService {

  private final UserRepository userRepository;
  private final ModelMapper modelMapper;

  @Override
  public UserDetailsDto getUserDetailsById(String id) {
    User user =
        userRepository
            .findById(id)
            .orElseThrow(() -> new NotFoundException("No user find with this id!"));
    UserDetailsDto userDetailsDto = modelMapper.map(user, UserDetailsDto.class);
    return userDetailsDto;
  }
}
