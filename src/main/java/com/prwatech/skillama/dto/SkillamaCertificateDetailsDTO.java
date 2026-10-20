package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillamaCertificateDetailsDTO {
    private String learnerName;
    private String courseName;
    private String courseId;
    private String completedAt;
    private String organizationLogoUrl;
    private Integer progressPercent;
}
