package com.prwatech.skillama;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Team inboxes for Skillama transactional notifications (signup, reviews, issue reports).
 */
public final class SkillamaNotificationEmails {

    /** Default team inboxes when admin has not configured notification settings yet. */
    public static final List<String> DEFAULT_TEAM_INBOXES = Collections.unmodifiableList(Arrays.asList(
            "techsupport@prwatech.com",
            "eduprwa@gmail.com",
            "jitendrachandwani4@gmail.com"
    ));

    /** @deprecated Use {@link com.prwatech.skillama.service.NotificationSettingsService} */
    @Deprecated
    public static final List<String> TEAM_INBOXES = DEFAULT_TEAM_INBOXES;

    private SkillamaNotificationEmails() {
    }
}
