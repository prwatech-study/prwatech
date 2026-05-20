package com.prwatech.skillama.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.prwatech.skillama.model.User;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {
    private String id;
    private String name;
    private String email;
    private String phone;
    private User.UserRole role;
    private User.PlanTier planTier;
    private Boolean active;
    private String gender;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime lastLoginAt;
    private Integer loginCount;
    private Integer activeCourseCount;
    /** Mean course progress 0–100 (may be below 1 when most learners have not started). */
    private Double averageProgress;
    private String createdBy;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;
    private String updatedBy;
}

