package com.prwatech.skillama.notification;

/**
 * Admin-facing grouping for notification routing (tech, sales, general, etc.).
 */
public enum NotificationCategory {
    GENERAL("General"),
    TECH_SUPPORT("Tech & support"),
    SALES("Sales"),
    OPERATIONS("Course operations");

    private final String label;

    NotificationCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
