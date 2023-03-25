package com.prwatech.common;

import com.prwatech.user.model.User;
import com.prwatech.user.template.IamMongodbTemplateLayer;
import java.util.Optional;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class UserDetailService {

  private final IamMongodbTemplateLayer iamMongodbTemplateLayer;

  public Optional<User> getUserDetailsByEmail(String username) {
    return iamMongodbTemplateLayer.findByEmail(username);
  }
}
