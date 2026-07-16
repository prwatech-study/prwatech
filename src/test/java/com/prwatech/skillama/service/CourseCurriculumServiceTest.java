package com.prwatech.skillama.service;

import com.prwatech.skillama.model.CourseCurriculum;
import com.prwatech.skillama.repository.CourseCurriculumRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseCurriculumServiceTest {

    @Mock private CourseCurriculumRepository curriculumRepository;
    @Mock private MongoTemplate mongoTemplate;

    private CourseCurriculumService service;

    @BeforeEach
    void setUp() {
        service = new CourseCurriculumService(curriculumRepository, mongoTemplate);
    }

    @Test
    void createStampsCreatedAtAndPersists() {
        when(curriculumRepository.save(any(CourseCurriculum.class))).thenAnswer(inv -> inv.getArgument(0));
        CourseCurriculum saved = service.create(
                CourseCurriculum.builder().courseId("c1").moduleName("M1").build());
        assertNotNull(saved.getCreatedAt());
        assertEquals("c1", saved.getCourseId());
    }

    @Test
    void findByIdDelegatesToRepository() {
        when(curriculumRepository.findById("id1"))
                .thenReturn(Optional.of(CourseCurriculum.builder().courseId("c1").build()));
        assertTrue(service.findById("id1").isPresent());
    }
}
