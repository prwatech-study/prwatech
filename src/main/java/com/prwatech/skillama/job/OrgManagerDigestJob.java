package com.prwatech.skillama.job;

import com.prwatech.skillama.service.OrgManagerDigestService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Weekly manager digests — Monday 09:00 IST. */
@Component
@RequiredArgsConstructor
public class OrgManagerDigestJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrgManagerDigestJob.class);

    private final OrgManagerDigestService orgManagerDigestService;

    @Scheduled(cron = "0 0 9 * * MON", zone = "Asia/Kolkata")
    public void sendWeeklyDigests() {
        try {
            int sent = orgManagerDigestService.sendWeeklyDigests();
            LOGGER.info("Org manager digest job sent {} emails", sent);
        } catch (Exception e) {
            LOGGER.warn("Org manager digest job failed: {}", e.getMessage());
        }
    }
}
