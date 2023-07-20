package com.prwatech.courses.dto;

import com.prwatech.courses.model.CourseCurriculam;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CourseCurriCulamDto {

    private CourseCurriculam courseCurriculam;
    private Integer currentVideo;
}
