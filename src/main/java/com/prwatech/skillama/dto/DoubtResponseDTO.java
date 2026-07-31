package com.prwatech.skillama.dto;

import com.prwatech.skillama.model.DoubtStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DoubtResponseDTO {
    private String id;
    private String courseId;
    private String moduleId;
    private String lessonId;
    private DoubtStatus status;
    private List<DoubtMessageDTO> messages;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime resolvedAt;
}
