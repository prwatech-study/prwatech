package com.prwatech.common.configuration;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class PasswordEncode {

  public String getEncryptedPassword(String password) {
    return Base64.getEncoder().encodeToString(password.getBytes(StandardCharsets.UTF_8));
  }

  public Boolean compare(String password, String encodedPassword) {
    return password.equals(
        Base64.getDecoder().decode(encodedPassword.getBytes(StandardCharsets.UTF_8)));
  }
}
