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
    ISSUE_REPLY(
            "Issue reply to learner",
            "When an admin replies to a reported issue; sent to the reporter's email.",
            NotificationAudience.LEARNER,
            NotificationCategory.TECH_SUPPORT),
    CREDITS_ADDED(
            "Credits added",
            "When credits are added by an admin, a recharge, a referral, or a course share.",
            NotificationAudience.LEARNER,
            NotificationCategory.GENERAL),
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
            NotificationCategory.SALES),
    ORG_LIFECYCLE(
            "Organization lifecycle",
            "Organization created, renewed, or branding updated.",
            NotificationAudience.TEAM,
            NotificationCategory.SALES),
    ORG_CONTRACT_ALERT(
            "Organization contract alert",
            "Contract expiry, grace period, or suspension notices for corporate accounts.",
            NotificationAudience.TEAM,
            NotificationCategory.SALES),
    LEARNING_STREAK_REMINDER(
            "Learning streak reminder",
            "Daily email when a learner is at risk of losing their streak.",
            NotificationAudience.LEARNER,
            NotificationCategory.GENERAL),
    LEARNING_STREAK_LOST(
            "Learning streak lost",
            "Email when a learner's daily streak has ended.",
            NotificationAudience.LEARNER,
            NotificationCategory.GENERAL),
    EVENT_REGISTRATION(
            "Event registration",
            "Plain welcome email when a new student registers with an event referral code.",
            NotificationAudience.LEARNER,
            NotificationCategory.GENERAL),
    EVENT_SPEND_ALERT(
            "Event spend alert",
            "Team email when an onboarding event crosses its spend alarm.",
            NotificationAudience.TEAM,
            NotificationCategory.OPERATIONS),
    ORG_MANAGER_DIGEST(
            "Manager weekly digest",
            "Weekly team progress digest for org managers / admins / owners.",
            NotificationAudience.LEARNER,
            NotificationCategory.OPERATIONS);

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
