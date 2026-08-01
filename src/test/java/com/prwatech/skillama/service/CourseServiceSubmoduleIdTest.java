package com.prwatech.skillama.service;

import com.prwatech.skillama.model.CourseCurriculum;
import com.prwatech.skillama.repository.CourseCurriculumRepository;
import com.prwatech.skillama.repository.CourseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Submodules need a stable id (independent of label/order) so Tester evaluation
 * responses keep pointing at the right topic even after curriculum edits. These
 * tests cover minting on create, preservation on update, and lazy backfill on read.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CourseServiceSubmoduleIdTest {

    @Mock private CourseRepository courseRepository;
    @Mock private CourseCurriculumRepository curriculumRepository;
    @Mock private MongoTemplate mongoTemplate;

    private CourseService service;

    @BeforeEach
    void setUp() {
        service = new CourseService(courseRepository, curriculumRepository, mongoTemplate);
    }

    private CourseCurriculum.Submodule submodule(String label) {
        CourseCurriculum.Submodule s = new CourseCurriculum.Submodule();
        s.setLabel(label);
        return s;
    }

    private CourseCurriculum module(CourseCurriculum.Submodule... subs) {
        CourseCurriculum module = new CourseCurriculum();
        module.setId("module-1");
        module.setModuleName("Module 1");
        module.setSubmodules(new ArrayList<>(List.of(subs)));
        return module;
    }

    @Test
    void addSubmoduleMintsIdWhenNoneProvided() {
        CourseCurriculum module = module();
        when(curriculumRepository.findById("module-1")).thenReturn(Optional.of(module));
        when(curriculumRepository.save(any(CourseCurriculum.class))).thenAnswer(inv -> inv.getArgument(0));

        CourseCurriculum.Submodule newSub = submodule("Intro");
        CourseCurriculum result = service.addSubmodule("module-1", newSub);

        assertNotNull(result.getSubmodules().get(0).getId());
    }

    @Test
    void updateSubmodulePreservesExistingIdRegardlessOfRequestBody() {
        CourseCurriculum.Submodule existing = submodule("Intro");
        existing.setId("stable-id-123");
        CourseCurriculum module = module(existing);
        when(curriculumRepository.findById("module-1")).thenReturn(Optional.of(module));
        when(curriculumRepository.save(any(CourseCurriculum.class))).thenAnswer(inv -> inv.getArgument(0));

        // Simulate a client sending an update with no id (or a bogus one) in the body.
        CourseCurriculum.Submodule incoming = submodule("Intro (edited)");
        incoming.setId("client-supplied-should-be-ignored");

        CourseCurriculum result = service.updateSubmodule("module-1", 0, incoming);

        assertEquals("stable-id-123", result.getSubmodules().get(0).getId());
        assertEquals("Intro (edited)", result.getSubmodules().get(0).getLabel());
    }

    @Test
    void updateSubmoduleMintsIdIfExistingSubmodulePredatesIdField() {
        CourseCurriculum.Submodule existing = submodule("Intro"); // no id set — pre-migration state
        CourseCurriculum module = module(existing);
        when(curriculumRepository.findById("module-1")).thenReturn(Optional.of(module));
        when(curriculumRepository.save(any(CourseCurriculum.class))).thenAnswer(inv -> inv.getArgument(0));

        CourseCurriculum result = service.updateSubmodule("module-1", 0, submodule("Intro (edited)"));

        assertNotNull(result.getSubmodules().get(0).getId());
    }

    @Test
    void getCurriculumBackfillsMissingIdsAndPersists() {
        CourseCurriculum.Submodule missingId = submodule("Legacy topic"); // no id
        CourseCurriculum module = module(missingId);
        when(curriculumRepository.findByCourseIdOrderByOrderAsc("course-1")).thenReturn(List.of(module));
        when(curriculumRepository.save(any(CourseCurriculum.class))).thenAnswer(inv -> inv.getArgument(0));

        List<CourseCurriculum> result = service.getCurriculumByCourseIdOrdered("course-1");

        assertNotNull(result.get(0).getSubmodules().get(0).getId());
        verify(curriculumRepository, times(1)).save(module);
    }

    @Test
    void getCurriculumDoesNotResaveWhenAllIdsAlreadyPresent() {
        CourseCurriculum.Submodule hasId = submodule("Already migrated");
        hasId.setId("already-there");
        CourseCurriculum module = module(hasId);
        when(curriculumRepository.findByCourseIdOrderByOrderAsc("course-1")).thenReturn(List.of(module));

        List<CourseCurriculum> result = service.getCurriculumByCourseIdOrdered("course-1");

        assertEquals("already-there", result.get(0).getSubmodules().get(0).getId());
        verify(curriculumRepository, never()).save(any(CourseCurriculum.class));
    }

    @Test
    void getCurriculumHandlesModuleWithNoSubmodulesGracefully() {
        CourseCurriculum module = new CourseCurriculum();
        module.setId("empty-module");
        module.setSubmodules(null);
        when(curriculumRepository.findByCourseIdOrderByOrderAsc("course-1")).thenReturn(List.of(module));

        List<CourseCurriculum> result = service.getCurriculumByCourseIdOrdered("course-1");

        assertNull(result.get(0).getSubmodules());
        verify(curriculumRepository, never()).save(any(CourseCurriculum.class));
    }
}
