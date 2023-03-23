package com.prwatech.common;

import com.prwatech.user.model.Users;
import com.prwatech.user.repository.IamMongodbTemplateLayer;
import lombok.AllArgsConstructor;

import java.util.Optional;

@AllArgsConstructor
public class UserDetailService {

    private final IamMongodbTemplateLayer iamMongodbTemplateLayer;

    public Optional<Users> getUserDetailsByEmail(String username){
        return iamMongodbTemplateLayer.findByEmail(username);
    }
}
