package com.prwatech.skillama.model;

/** AI Exam MVP exam types — see the Back-office design doc for the deferred ones. */
public enum ExamType {
    PRACTICE,
    TOPIC_WISE,
    MODULE_WISE,
    AI_RECOMMENDED,
    /** Official-guideline-backed global certification practice exam. */
    GLOBAL_CERTIFICATION
}
