package com.prwatech.promotion.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.NotNull;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class HelpAndSupportDto {

    @NotNull(message = "Course title can not be null")
    private String courseTitle;
    @NotNull(message = "Question title can not be null")
    private String questionTitle;
    @NotNull(message = "Description can not be null")
    private String description;
}
