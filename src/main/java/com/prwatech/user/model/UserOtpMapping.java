package com.prwatech.user.model;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Document(value = "users_otp_mapping")
public class UserOtpMapping {

  @Id private String id;

  @Field("user_id")
  private String userid;

  @Field("phone_number")
  private Long phoneNumber;

  @Field("otp")
  private Integer otp;

  @Field("expire_at")
  private LocalDateTime expireAt;
}
