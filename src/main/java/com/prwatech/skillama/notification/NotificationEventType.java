package com.prwatech.skillama.notification;

/**
 * Skillama transactional email events shown in the admin notification control center.
 */
public enum NotificationEventType {
    USER_REGISTRATION(
            "New user registration",
            "Notifies your team when someone registers (legacy activation flow).",
            NotificationAudience.TEAM),
    FEEDBACK_NEW(
            "New learner feedback",
            "When a learner submits a rating or review from the LMS.",
            NotificationAudience.TEAM),
    FEEDBACK_REPLY(
            "Feedback reply to learner",
            "When an admin replies to feedback; sent to the learner's account email.",
            NotificationAudience.LEARNER),
    ISSUE_REPORT(
            "Issue / bug report",
            "When a learner reports a problem from the LMS.",
            NotificationAudience.TEAM),
    COURSE_ASSIGNED_LEARNER(
            "Course assigned (learner)",
            "When one or more courses are assigned to a learner.",
            NotificationAudience.LEARNER),
    COURSE_ASSIGNED_TEAM(
            "Course assigned (team)",
            "Internal copy when courses are assigned to a learner.",
            NotificationAudience.TEAM),
    COURSE_UNASSIGNED_LEARNER(
            "Course unassigned (learner)",
            "When course access is removed from a learner.",
            NotificationAudience.LEARNER),
    COURSE_UNASSIGNED_TEAM(
            "Course unassigned (team)",
            "Internal copy when course access is removed.",
            NotificationAudience.TEAM),
    UPGRADE_REQUEST(
            "Full access / upgrade request",
            "When a freemium user requests paid or full access.",
            NotificationAudience.TEAM);

    private final String label;
    private final String description;
    private final NotificationAudience audience;

    NotificationEventType(String label, String description, NotificationAudience audience) {
        this.label = label;
        this.description = description;
        this.audience = audience;
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

    public enum NotificationAudience {
        TEAM,
        LEARNER
    }
}
