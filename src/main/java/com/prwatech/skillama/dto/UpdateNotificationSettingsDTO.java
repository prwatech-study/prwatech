package com.prwatech.skillama.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class UpdateNotificationSettingsDTO {
    /** Key = notification type id, value = enabled */
    private Map<String, Boolean> typeEnabled;
    /** Key = notification type id, value = recipient emails for that type only */
    private Map<String, List<String>> typeRecipientEmails;
}
