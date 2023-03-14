package com.prwatech.user.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Users {
    private Long id;
    private String name;
    private String userName;
    private String email;
    private String password;
    private Long phoneNumber;
    private Boolean isProfileImage;
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
