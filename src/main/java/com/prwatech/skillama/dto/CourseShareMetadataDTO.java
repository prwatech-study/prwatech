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
    private String tagline;
    private String overview;
    private java.util.List<String> objectives;
    private java.util.List<String> highlights;
    private java.util.List<String> prerequisites;
    private java.util.List<String> outcomes;
    private String audience;
    private String aiTutorHelp;
    /** Module + lecture labels only (no scripts). */
    private java.util.List<CourseOutlineModuleDTO> modules;
}
