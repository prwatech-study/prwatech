package com.prwatech.skillama.service;

import com.prwatech.skillama.model.CourseCurriculum;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    /**
     * Content-integrity warnings (admin visibility for silently-invisible/incomplete content —
     * disabled submodules, missing images) layered on top of the existing script-missing check.
     */
    private static CourseCurriculum moduleWith(CourseCurriculum.Submodule... subs) {
        CourseCurriculum module = new CourseCurriculum();
        module.setModuleName("Test Module");
        module.setSubmodules(List.of(subs));
        return module;
    }

    @Test
    void disabledSubmoduleFlaggedRegardlessOfOtherFields() {
        CourseCurriculum.Submodule s = new CourseCurriculum.Submodule();
        s.setEnabled(false);
        s.setImagePath("images/x.png");
        s.setScriptText("some narration");
        CourseService.applyPracticalScriptIntegrityWarnings(List.of(moduleWith(s)));
        assertEquals("SUBMODULE_DISABLED", s.getContentIntegrityIssueCode());
    }

    @Test
    void enabledSubmoduleMissingImageFlagged() {
        CourseCurriculum.Submodule s = new CourseCurriculum.Submodule();
        s.setEnabled(true);
        s.setImagePath("  ");
        s.setScriptText("some narration");
        CourseService.applyPracticalScriptIntegrityWarnings(List.of(moduleWith(s)));
        assertEquals("IMAGE_MISSING", s.getContentIntegrityIssueCode());
    }

    @Test
    void enabledCompleteSubmoduleHasNoWarning() {
        CourseCurriculum.Submodule s = new CourseCurriculum.Submodule();
        s.setEnabled(true);
        s.setImagePath("images/x.png");
        s.setScriptText("some narration");
        CourseService.applyPracticalScriptIntegrityWarnings(List.of(moduleWith(s)));
        assertNull(s.getContentIntegrityIssueCode());
    }

    @Test
    void missingScriptTakesPriorityOverMissingImageWhenEnabledAndPractical() {
        CourseCurriculum.Submodule s = new CourseCurriculum.Submodule();
        s.setEnabled(true);
        s.setPracticalRequired(true);
        s.setScriptText("");
        s.setImagePath("");
        CourseService.applyPracticalScriptIntegrityWarnings(List.of(moduleWith(s)));
        assertEquals("PRACTICAL_SCRIPT_MISSING", s.getContentIntegrityIssueCode());
    }
}
