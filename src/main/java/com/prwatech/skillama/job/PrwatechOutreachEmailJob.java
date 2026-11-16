package com.prwatech.skillama.job;

import com.prwatech.skillama.service.outreach.PrwatechOutreachService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PrwatechOutreachEmailJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(PrwatechOutreachEmailJob.class);

    private final PrwatechOutreachService outreachService;

    /** Daily Prwatech legacy invite batch — 09:30 IST. */
    @Scheduled(cron = "0 30 9 * * *", zone = "Asia/Kolkata")
    public void sendDailyBatch() {
        try {
            outreachService.processDailyBatch();
        } catch (Exception e) {
            LOGGER.warn("Prwatech outreach email job failed: {}", e.getMessage());
        }
    }
}