package com.prwatech.user.model;

import lombok.AllArgsConstructor;
import lombok.Generated;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Document(value = "users_otp_mapping")
public class UserOtpMapping {

    @Id
    private Long id;

    @Field("user_id")
    private Long userid;

    @Field("phone_number")
    private Long phoneNumber;

    @Field("otp")
    private Integer otp;

    @Field("expire_at")
    private LocalDateTime expireAt;
}
