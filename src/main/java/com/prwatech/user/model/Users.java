package com.prwatech.user.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Document(value = "Users")
public class Users {

    @Id
    private String id;

    @Field(name = "name")
    private String Name;

    @Field(name = "username")
    private String UserName;

    @Field(name = "Email")
    private String Email;

    private String Password;

    private Long PhoneNumber;

    private Boolean isProfileImage;

    private Boolean isPasswordReset = Boolean.FALSE;

    private String profileImage;
    private String qualification;
    private String address;
    private String referalCode;
    private String refererCode;
    private List<Users> referedUsers = new ArrayList<>();
    private Boolean isResumeUploaded;
    private String resumeUrl;
    private Boolean verified;
    private Boolean disable;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
