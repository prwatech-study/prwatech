package com.prwatech.skillama.job;

import com.prwatech.skillama.service.CertificationQuestionBankService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically clears stuck RUNNING bank rebuilds (e.g. after a JVM crash)
 * so admins can click Rebuild again.
 */
@Component
@RequiredArgsConstructor
public class CertificationBankStaleLockJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(CertificationBankStaleLockJob.class);

    private final CertificationQuestionBankService bankService;

    /** Every 15 minutes. */
    @Scheduled(cron = "0 */15 * * * *", zone = "Asia/Kolkata")
    public void recoverStaleLocks() {
        int recovered = bankService.recoverStaleRunningLocks();
        if (recovered > 0) {
            LOGGER.info("Recovered {} stale certification bank rebuild lock(s)", recovered);
        }
    }
}
