package com.prwatech.skillama.service;

import com.prwatech.skillama.model.CourseCurriculum;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CourseServiceGuestCurriculumTest {

    @Test
    void guestView_keepsScriptOnlyOnFirstEnabledLecture() {
        CourseCurriculum module = new CourseCurriculum();
        module.setModuleName("Module 1");
        module.setSubmodules(new ArrayList<>(List.of(
                submodule("Lecture A", "script-a"),
                submodule("Lecture B", "script-b")
        )));

        List<CourseCurriculum> result = CourseService.applyGuestScriptRestrictions(List.of(module));

        assertEquals(1, result.size());
        assertEquals("script-a", result.get(0).getSubmodules().get(0).getScriptText());
        assertNull(result.get(0).getSubmodules().get(1).getScriptText());
    }

    @Test
    void guestView_skipsDisabledSubmodulesBeforeFirstScript() {
        CourseCurriculum.Submodule disabled = submodule("Hidden", "hidden-script");
        disabled.setEnabled(false);

        CourseCurriculum module = new CourseCurriculum();
        module.setModuleName("Module 1");
        module.setSubmodules(new ArrayList<>(List.of(
                disabled,
                submodule("First visible", "visible-script")
        )));

        List<CourseCurriculum> result = CourseService.applyGuestScriptRestrictions(List.of(module));

        assertEquals("visible-script", result.get(0).getSubmodules().get(0).getScriptText());
        assertEquals(1, result.get(0).getSubmodules().size());
    }

    @Test
    void guestView_emptyModulesReturnsEmptyList() {
        assertEquals(0, CourseService.applyGuestScriptRestrictions(List.of()).size());
        assertEquals(0, CourseService.applyGuestScriptRestrictions(null).size());
    }

    private static CourseCurriculum.Submodule submodule(String label, String script) {
        CourseCurriculum.Submodule s = new CourseCurriculum.Submodule();
        s.setLabel(label);
        s.setScriptText(script);
        s.setEnabled(true);
        return s;
    }
}
