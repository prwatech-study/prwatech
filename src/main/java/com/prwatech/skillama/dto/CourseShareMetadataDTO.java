package com.prwatech.skillama.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseShareMetadataDTO {
    private String courseId;
    private String title;
    private String description;
    private String imageUrl;
    private String shareUrl;
}
