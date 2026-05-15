package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserActivityDTO {
    private String userId;
    private LocalDateTime lastLoginAt;
    private List<QueryActivityItemDTO> queryLog;
    private List<LectureActivityItemDTO> lectureCompletions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QueryActivityItemDTO {
        private String queryType;
        private String courseId;
        private LocalDateTime createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LectureActivityItemDTO {
        private String lectureLabel;
        private String courseId;
        private LocalDateTime completedAt;
    }
}
