package com.prwatech.courses.dto;

import com.prwatech.courses.enums.CourseLevelCategory;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class AddCartDto {

    private String User_Id;
    private String CourseId;
    private CourseLevelCategory Course_Type;
}
