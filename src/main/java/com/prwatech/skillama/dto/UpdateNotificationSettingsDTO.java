package com.prwatech.skillama.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class UpdateNotificationSettingsDTO {
    private List<String> teamRecipientEmails;
    /** Key = notification type id, value = enabled */
    private Map<String, Boolean> typeEnabled;
}
