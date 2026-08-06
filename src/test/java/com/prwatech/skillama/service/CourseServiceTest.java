package com.prwatech.skillama.service;

import com.prwatech.skillama.model.CourseCurriculum;
import com.prwatech.skillama.repository.CourseCurriculumRepository;
import com.prwatech.skillama.repository.CourseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseServiceTest {

    @Mock private CourseRepository courseRepository;
    @Mock private CourseCurriculumRepository curriculumRepository;
    @Mock private MongoTemplate skillamaMongoTemplate;

    private CourseService courseService() {
        return new CourseService(courseRepository, curriculumRepository, skillamaMongoTemplate);
    }

    private static CourseCurriculum.Submodule submodule(String id, String label) {
        CourseCurriculum.Submodule s = new CourseCurriculum.Submodule();
        s.setId(id);
        s.setLabel(label);
        return s;
    }

    private static CourseCurriculum.Submodule practicalSubmodule(String id, String label, String datasetId) {
        CourseCurriculum.Submodule s = submodule(id, label);
        s.setEnabled(true);
        s.setPracticalRequired(true);
        s.setDatasetId(datasetId);
        return s;
    }

    // Regression test for a real production incident: the admin "edit module" form only ever
    // sends {moduleName, title, order} — it never includes submodules. updateModule() used to
    // unconditionally overwrite submodules with whatever the caller sent, which meant every
    // module rename/reorder silently wiped out all of that module's submodules.
    @Test
    void updateModule_omittedSubmodules_preservesExisting() {
        CourseCurriculum existing = CourseCurriculum.builder()
                .id("module-1")
                .moduleName("Operators in Python")
                .submodules(List.of(submodule("s1", "Arithmetic Operators"), submodule("s2", "Logical Operators")))
                .build();
        when(curriculumRepository.findById("module-1")).thenReturn(Optional.of(existing));
        when(curriculumRepository.save(any(CourseCurriculum.class))).thenAnswer(inv -> inv.getArgument(0));

        CourseCurriculum update = new CourseCurriculum();
        update.setModuleName("Operators in Python (renamed)");
        update.setOrder(2);
        // submodules deliberately left unset — this is exactly what the edit-module form sends

        CourseCurriculum result = courseService().updateModule("module-1", update);

        assertNotNull(result);
        assertEquals("Operators in Python (renamed)", result.getModuleName());
        assertEquals(2, result.getSubmodules().size());
        assertEquals("Arithmetic Operators", result.getSubmodules().get(0).getLabel());
    }

    @Test
    void updateModule_explicitSubmodules_stillReplacesThem() {
        CourseCurriculum existing = CourseCurriculum.builder()
                .id("module-1")
                .moduleName("Operators in Python")
                .submodules(List.of(submodule("s1", "Old Topic")))
                .build();
        when(curriculumRepository.findById("module-1")).thenReturn(Optional.of(existing));
        when(curriculumRepository.save(any(CourseCurriculum.class))).thenAnswer(inv -> inv.getArgument(0));

        CourseCurriculum update = new CourseCurriculum();
        update.setModuleName("Operators in Python");
        update.setSubmodules(List.of(submodule("s1", "New Topic"), submodule("s2", "Another Topic")));

        CourseCurriculum result = courseService().updateModule("module-1", update);

        assertEquals(2, result.getSubmodules().size());
        assertEquals("New Topic", result.getSubmodules().get(0).getLabel());
    }

    // Regression test for a real production bug: the learner-facing curriculum view
    // (GET /courses/{id}/curriculum, non-admin) rebuilds each submodule field-by-field and used to
    // drop `id` and `datasetId`, so a submodule with a dataset attached via the practical-exercise
    // upload flow showed up as datasetId: null for every learner, even though it was persisted correctly.
    @Test
    void filterCurriculumForLearner_preservesIdAndDatasetId() {
        CourseCurriculum module = CourseCurriculum.builder()
                .id("module-1")
                .courseId("course-1")
                .moduleName("Operators in Python")
                .submodules(List.of(practicalSubmodule("s1", "Practical: Analyze Sales Data", "dataset-1")))
                .build();

        List<CourseCurriculum> result = courseService().filterCurriculumForLearner(List.of(module));

        CourseCurriculum.Submodule sub = result.get(0).getSubmodules().get(0);
        assertEquals("s1", sub.getId());
        assertEquals("dataset-1", sub.getDatasetId());
    }

    @Test
    void applyGuestScriptRestrictions_preservesIdAndDatasetId() {
        CourseCurriculum module = CourseCurriculum.builder()
                .id("module-1")
                .courseId("course-1")
                .moduleName("Operators in Python")
                .submodules(List.of(practicalSubmodule("s1", "Practical: Analyze Sales Data", "dataset-1")))
                .build();

        List<CourseCurriculum> result = CourseService.applyGuestScriptRestrictions(List.of(module));

        CourseCurriculum.Submodule sub = result.get(0).getSubmodules().get(0);
        assertEquals("s1", sub.getId());
        assertEquals("dataset-1", sub.getDatasetId());
    }
}
