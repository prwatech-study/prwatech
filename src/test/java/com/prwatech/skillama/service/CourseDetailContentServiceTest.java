package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.CourseOutlineModuleDTO;
import com.prwatech.skillama.model.CourseCurriculum;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CourseDetailContentServiceTest {

    private static CourseCurriculum.Submodule lecture(String label, Boolean enabled) {
        CourseCurriculum.Submodule s = new CourseCurriculum.Submodule();
        s.setLabel(label);
        s.setEnabled(enabled);
        s.setScriptText("SECRET SCRIPT — must never appear on the public outline");
        return s;
    }

    @Test
    void buildOutline_usesLabelsOnlyAndSkipsDisabled() {
        CourseCurriculum module = CourseCurriculum.builder()
                .moduleName("Functions")
                .submodules(List.of(
                        lecture("What is a function", true),
                        lecture("Hidden lecture", false),
                        lecture("  Return values  ", null)))
                .build();

        List<CourseOutlineModuleDTO> outline = CourseDetailContentService.buildOutline(List.of(module));

        assertEquals(1, outline.size());
        assertEquals("Functions", outline.get(0).getModuleName());
        assertEquals(List.of("What is a function", "Return values"), outline.get(0).getLectures());
        assertTrue(outline.get(0).getLectures().stream().noneMatch(l -> l.contains("SECRET")));
    }

    @Test
    void buildOutline_emptyCurriculum_returnsEmpty() {
        assertTrue(CourseDetailContentService.buildOutline(null).isEmpty());
        assertTrue(CourseDetailContentService.buildOutline(List.of()).isEmpty());
    }
}
