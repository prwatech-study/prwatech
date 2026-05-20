package com.prwatech.skillama.service;

import com.prwatech.skillama.model.CourseCurriculum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CourseServicePracticalScriptTest {

    @Test
    void practicalWithoutScriptThrows() {
        CourseCurriculum.Submodule s = new CourseCurriculum.Submodule();
        s.setPracticalRequired(true);
        s.setScriptText("   ");
        assertThrows(IllegalArgumentException.class, () -> CourseService.validatePracticalSubmoduleScript(s));
    }

    @Test
    void practicalWithScriptOk() {
        CourseCurriculum.Submodule s = new CourseCurriculum.Submodule();
        s.setPracticalRequired(true);
        s.setScriptText("print('hi')");
        assertDoesNotThrow(() -> CourseService.validatePracticalSubmoduleScript(s));
    }

    @Test
    void nonPracticalEmptyScriptOk() {
        CourseCurriculum.Submodule s = new CourseCurriculum.Submodule();
        s.setPracticalRequired(false);
        s.setScriptText("");
        assertDoesNotThrow(() -> CourseService.validatePracticalSubmoduleScript(s));
    }
}
