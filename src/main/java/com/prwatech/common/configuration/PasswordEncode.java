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
    try {
      String decodedPassword = new String(
          Base64.getDecoder().decode(encodedPassword), StandardCharsets.UTF_8);
      return password.equals(decodedPassword);
    } catch (Exception e) {
      return false;
    }
  }
}
