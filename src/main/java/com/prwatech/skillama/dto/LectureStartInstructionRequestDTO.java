package com.prwatech.skillama.dto;

import lombok.Data;

import java.util.List;

@Data
public class LectureStartInstructionRequestDTO {
    private List<String> topics;
}
