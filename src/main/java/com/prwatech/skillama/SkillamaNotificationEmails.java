package com.prwatech.skillama;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Team inboxes for Skillama transactional notifications (signup, reviews, issue reports).
 */
public final class SkillamaNotificationEmails {

    public static final List<String> TEAM_INBOXES = Collections.unmodifiableList(Arrays.asList(
            "techsupport@prwatech.com",
            "eduprwa@gmail.com",
            "jitendrachandwani4@gmail.com"
    ));

    private SkillamaNotificationEmails() {
    }
}
