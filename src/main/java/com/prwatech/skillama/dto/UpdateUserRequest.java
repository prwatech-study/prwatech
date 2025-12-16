package com.prwatech.skillama.dto;

import com.prwatech.skillama.model.GenderEnum;
import com.prwatech.skillama.model.User;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserRequest {
    private String name;
    private String email;
    private User.UserRole role;
    private Boolean active;
    private GenderEnum gender;
}

