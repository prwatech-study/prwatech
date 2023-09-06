package com.prwatech.courses.dto;

import com.prwatech.courses.enums.CourseLevelCategory;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AddWishListDto {
    private String userId;
    private String courseId;
    private CourseLevelCategory courseLevelCategory;
}
