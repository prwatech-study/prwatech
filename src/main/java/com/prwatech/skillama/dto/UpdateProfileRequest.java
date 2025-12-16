package com.prwatech.skillama.dto;

import com.prwatech.skillama.model.GenderEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {
    private String name;
    private String email;
    private GenderEnum gender;
    // Note: Password update should be separate endpoint for security
}

