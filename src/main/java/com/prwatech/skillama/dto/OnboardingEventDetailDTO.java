package com.prwatech.skillama.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class OnboardingEventDetailDTO {
    private OnboardingEventSummaryDTO event;
    private List<EventStudentDTO> students;
}
