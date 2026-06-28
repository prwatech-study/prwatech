package com.prwatech.skillama.model;

/**
 * How runnable code output is produced for a course in the LMS.
 */
public final class CourseCodeOutputMode {

    public static final String COMPILER = "compiler";
    public static final String AI = "ai";

    private CourseCodeOutputMode() {
    }

    public static String normalize(String value) {
        if (value != null && COMPILER.equalsIgnoreCase(value.trim())) {
            return COMPILER;
        }
        return AI;
    }
}
