package com.prwatech.skillama.service;

import com.prwatech.skillama.model.Course;
import com.prwatech.skillama.repository.CourseCurriculumRepository;
import com.prwatech.skillama.repository.CourseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Guards the course "active" (deactivate) toggle contract between the admin UI and the backend:
 * the flag must round-trip through create/update and control learner visibility.
 */
@ExtendWith(MockitoExtension.class)
class CourseServiceActiveTest {

    @Mock
    private CourseRepository courseRepository;
    @Mock
    private CourseCurriculumRepository curriculumRepository;
    @Mock
    private MongoTemplate skillamaMongoTemplate;

    @InjectMocks
    private CourseService courseService;

    // --- create() ---

    @Test
    void create_defaultsActiveToTrue_whenNull() {
        when(courseRepository.save(any(Course.class))).thenAnswer(inv -> inv.getArgument(0));

        Course saved = courseService.create(Course.builder().name("C").active(null).build());

        assertTrue(saved.getActive());
    }

    @Test
    void create_preservesExplicitInactive() {
        when(courseRepository.save(any(Course.class))).thenAnswer(inv -> inv.getArgument(0));

        Course saved = courseService.create(Course.builder().name("C").active(false).build());

        assertFalse(saved.getActive());
    }

    // --- update() --- (the original bug: active:false was dropped)

    @Test
    void update_persistsActiveFalse() {
        Course existing = Course.builder().id("c1").name("C").active(true).build();
        when(courseRepository.findById("c1")).thenReturn(Optional.of(existing));
        when(courseRepository.save(any(Course.class))).thenAnswer(inv -> inv.getArgument(0));

        Course payload = Course.builder().name("C").active(false).build();
        Course updated = courseService.update("c1", payload);

        assertFalse(updated.getActive());
    }

    @Test
    void update_leavesActiveUnchanged_whenPayloadActiveNull() {
        Course existing = Course.builder().id("c1").name("C").active(true).build();
        when(courseRepository.findById("c1")).thenReturn(Optional.of(existing));
        when(courseRepository.save(any(Course.class))).thenAnswer(inv -> inv.getArgument(0));

        Course payload = Course.builder().name("Renamed").active(null).build();
        Course updated = courseService.update("c1", payload);

        assertTrue(updated.getActive());
    }

    // --- isAvailableToLearner() ---

    @Test
    void isAvailableToLearner_trueWhenActiveAndNotArchived() {
        assertTrue(CourseService.isAvailableToLearner(Course.builder().active(true).build()));
    }

    @Test
    void isAvailableToLearner_trueForLegacyNullActive() {
        assertTrue(CourseService.isAvailableToLearner(Course.builder().active(null).build()));
    }

    @Test
    void isAvailableToLearner_falseWhenDeactivated() {
        assertFalse(CourseService.isAvailableToLearner(Course.builder().active(false).build()));
    }

    @Test
    void isAvailableToLearner_falseWhenArchived() {
        Course archived = Course.builder().active(true).build();
        archived.setDeletedAt(com.prwatech.skillama.util.IndiaTime.now());
        assertFalse(CourseService.isAvailableToLearner(archived));
    }

    @Test
    void findBrowsableCourses_includesActiveCoursesThatAreNotPublic() {
        Course publicTeaser = Course.builder().id("p").name("Public teaser").isPublic(true).active(true).build();
        Course catalog = Course.builder().id("c").name("Catalog course").isPublic(false).active(true).build();
        Course deactivated = Course.builder().id("d").name("Off").isPublic(false).active(false).build();
        when(skillamaMongoTemplate.find(any(Query.class), eq(Course.class)))
                .thenReturn(List.of(publicTeaser, catalog, deactivated));

        List<Course> browsable = courseService.findBrowsableCourses();

        assertEquals(2, browsable.size());
        assertEquals("c", browsable.get(0).getId());
        assertEquals("p", browsable.get(1).getId());
    }

    @Test
    void findPublicCourses_stillRequiresIsPublicFlag() {
        when(courseRepository.findByIsPublicTrue()).thenReturn(List.of(
                Course.builder().id("p").name("Public").isPublic(true).active(true).build(),
                Course.builder().id("off").name("Off").isPublic(true).active(false).build()));

        List<Course> pub = courseService.findPublicCourses();

        assertEquals(1, pub.size());
        assertEquals("p", pub.get(0).getId());
    }
}
