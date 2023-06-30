package com.prwatech.courses.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class RegisterWebinarRequestDto {

    private String webinarId;
    private String email;
    private String name;
    private Long phoneNumber;
}
