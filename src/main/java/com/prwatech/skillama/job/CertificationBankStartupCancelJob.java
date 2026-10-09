package com.prwatech.skillama.job;

import com.prwatech.skillama.service.CertificationQuestionBankService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * On each deploy / JVM start, cancel any certification bank rebuilds left in
 * RUNNING from the previous process (async workers do not survive restart).
 */
@Component
@RequiredArgsConstructor
public class CertificationBankStartupCancelJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(CertificationBankStartupCancelJob.class);

    private final CertificationQuestionBankService bankService;

    @EventListener(ApplicationReadyEvent.class)
    public void cancelOrphanedRebuildsOnStartup() {
        int cancelled = bankService.cancelAllRunningRebuilds(
                "Bank rebuild cancelled after service restart. Click Rebuild to start again.");
        if (cancelled > 0) {
            LOGGER.info("Cancelled {} orphaned certification bank rebuild(s) on startup", cancelled);
        }
    }
}
