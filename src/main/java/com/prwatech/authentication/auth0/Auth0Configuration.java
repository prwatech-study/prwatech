package com.prwatech.authentication.auth0;

import com.prwatech.authentication.dto.Auth0SignUpDto;
import com.prwatech.common.Constants;
import com.prwatech.common.configuration.AppContext;
import com.prwatech.user.dto.SignUpRequestDto;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import static com.prwatech.common.Constants.USERNAME_PASSWORD_CONNECTION;

@Component
@AllArgsConstructor
public class Auth0Configuration {

    private final AppContext appContext;

    //register up user with email and password.
    public void createUserInAuth0(SignUpRequestDto signUpRequestDto) throws MalformedURLException {
        String urlLink ="https://"+appContext.getAuth0DomainId()+"/"+ USERNAME_PASSWORD_CONNECTION +"/signup";
        URL url = new URL(urlLink);

        Map<String, String> requestParam = new HashMap<>();
        requestParam.put("client_id", appContext.getAuth0ClientId());


    }

    //authenticate user (Log in).

    //log out user.

    //validate token.

    //get refresh token.

}
