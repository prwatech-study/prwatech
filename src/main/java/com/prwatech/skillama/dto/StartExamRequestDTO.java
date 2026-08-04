package com.prwatech.skillama.dto;

import com.prwatech.skillama.model.ExamDifficulty;
import com.prwatech.skillama.model.ExamType;
import lombok.Data;

@Data
public class StartExamRequestDTO {
    private String courseId;
    /** Null for a topic-wide/practice exam not scoped to a single module. */
    private String moduleId;
    /** Optional free-text topic label — used for TOPIC_WISE exams. */
    private String topic;
    /** Real FK to CourseCurriculum, set only when the picker resolved a real module. */
    private String curriculumModuleId;
    /** Real FK to CourseCurriculum.Submodule, set only when the picker resolved a real submodule. */
    private String submoduleId;
    private ExamDifficulty difficulty;
    private ExamType examType;
}
