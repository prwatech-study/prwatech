package com.prwatech.skillama.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CourseServiceProgressTest {

    @Test
    void calculateProgressPercent_capsAt100WhenCompletedExceedsTotal() {
        assertEquals(100, CourseService.calculateProgressPercent(161, 132));
        assertEquals(77, CourseService.calculateProgressPercent(102, 132));
        assertEquals(0, CourseService.calculateProgressPercent(0, 132));
        assertEquals(0, CourseService.calculateProgressPercent(10, 0));
    }

    @Test
    void calculateCourseCompletionPercent_countsQuizzesInBothTerms() {
        // 20 lectures + 5 module quizzes: all lectures but no quizzes -> 80, not 100.
        assertEquals(80, CourseService.calculateCourseCompletionPercent(20, 20, 0, 5));
        assertEquals(100, CourseService.calculateCourseCompletionPercent(20, 20, 5, 5));
        // Math.round, not truncation: (14+2)/25 = 64.0 -> 64; (3+1)/6 = 66.67 -> 67.
        assertEquals(64, CourseService.calculateCourseCompletionPercent(14, 20, 2, 5));
        assertEquals(67, CourseService.calculateCourseCompletionPercent(3, 4, 1, 2));
    }

    @Test
    void calculateCourseCompletionPercent_capsAndGuardsDegenerateInputs() {
        assertEquals(100, CourseService.calculateCourseCompletionPercent(30, 20, 9, 5));
        assertEquals(0, CourseService.calculateCourseCompletionPercent(0, 0, 0, 0));
        assertEquals(0, CourseService.calculateCourseCompletionPercent(-3, 10, -1, 2));
        // No quizzes configured -> pure lecture ratio, same as before.
        assertEquals(50, CourseService.calculateCourseCompletionPercent(5, 10, 0, 0));
    }

    @Test
    void clampStoredProgressPercent_capsLegacyOverflow() {
        assertEquals(100, CourseService.clampStoredProgressPercent(122));
        assertEquals(50, CourseService.clampStoredProgressPercent(50));
        assertEquals(0, CourseService.clampStoredProgressPercent(null));
    }
}
