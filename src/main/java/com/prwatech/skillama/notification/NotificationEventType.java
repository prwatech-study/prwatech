package com.prwatech.skillama.notification;

/**
 * Skillama transactional email events shown in the admin notification control center.
 */
public enum NotificationEventType {
    USER_REGISTRATION(
            "New user registration",
            "Notifies your team when someone registers (legacy activation flow).",
            NotificationAudience.TEAM,
            NotificationCategory.GENERAL),
    FEEDBACK_NEW(
            "New learner feedback",
            "When a learner submits a rating or review from the LMS.",
            NotificationAudience.TEAM,
            NotificationCategory.TECH_SUPPORT),
    FEEDBACK_REPLY(
            "Feedback reply to learner",
            "When an admin replies to feedback; sent to the learner's account email.",
            NotificationAudience.LEARNER,
            NotificationCategory.GENERAL),
    ISSUE_REPORT(
            "Issue / bug report",
            "When a learner reports a problem from the LMS.",
            NotificationAudience.TEAM,
            NotificationCategory.TECH_SUPPORT),
    COURSE_ASSIGNED_LEARNER(
            "Course assigned (learner)",
            "When one or more courses are assigned to a learner.",
            NotificationAudience.LEARNER,
            NotificationCategory.OPERATIONS),
    COURSE_ASSIGNED_TEAM(
            "Course assigned (team)",
            "Internal copy when courses are assigned to a learner.",
            NotificationAudience.TEAM,
            NotificationCategory.OPERATIONS),
    COURSE_UNASSIGNED_LEARNER(
            "Course unassigned (learner)",
            "When course access is removed from a learner.",
            NotificationAudience.LEARNER,
            NotificationCategory.OPERATIONS),
    COURSE_UNASSIGNED_TEAM(
            "Course unassigned (team)",
            "Internal copy when course access is removed.",
            NotificationAudience.TEAM,
            NotificationCategory.OPERATIONS),
    UPGRADE_REQUEST(
            "Full access / upgrade request",
            "When a freemium user requests paid or full access.",
            NotificationAudience.TEAM,
            NotificationCategory.SALES);

    private final String label;
    private final String description;
    private final NotificationAudience audience;
    private final NotificationCategory category;

    NotificationEventType(
            String label,
            String description,
            NotificationAudience audience,
            NotificationCategory category) {
        this.label = label;
        this.description = description;
        this.audience = audience;
        this.category = category;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    public NotificationAudience getAudience() {
        return audience;
    }

    public NotificationCategory getCategory() {
        return category;
    }

    public enum NotificationAudience {
        TEAM,
        LEARNER
    }
}
