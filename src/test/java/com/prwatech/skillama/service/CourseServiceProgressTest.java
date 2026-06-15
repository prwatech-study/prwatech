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
    void clampStoredProgressPercent_capsLegacyOverflow() {
        assertEquals(100, CourseService.clampStoredProgressPercent(122));
        assertEquals(50, CourseService.clampStoredProgressPercent(50));
        assertEquals(0, CourseService.clampStoredProgressPercent(null));
    }
}
