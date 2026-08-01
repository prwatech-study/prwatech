package com.prwatech.skillama.model;

/**
 * Assignable admin panel modules. OWNER always has full access.
 * Null/empty permissions on an ADMIN user = legacy full access (all modules, all CRUD).
 */
public enum AdminModule {
    DASHBOARD,
    USERS,
    COURSES,
    CURRICULUM,
    ASSIGNMENTS,
    FEEDBACK,
    UPGRADE_REQUESTS,
    AUDIT_LOGS,
    ANALYTICS,
    FREEMIUM,
    SETTINGS_DEMO_VIDEO,
    SETTINGS_REFERRAL,
    SETTINGS_NOTIFICATIONS,
    SUPPORT,
    CHAT_MONITOR,
    AI_USAGE,
    TESTER_EVALUATIONS,
    AI_MENTOR_DOUBTS,
    AI_EXAMS
}
