package com.prwatech.skillama.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class NotificationTypeSettingDTO {
    private String id;
    private String label;
    private String description;
    /** TEAM or LEARNER */
    private String audience;
    private String category;
    private String categoryLabel;
    private boolean enabled;
    /** Human-readable recipient summary for the admin UI. */
    private String recipientSummary;
    /** TEAM types: configured team emails. LEARNER types: empty. */
    private List<String> recipientEmails;
}
