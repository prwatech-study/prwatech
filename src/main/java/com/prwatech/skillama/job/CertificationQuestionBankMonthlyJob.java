package com.prwatech.skillama.job;

import com.prwatech.skillama.model.GlobalCertificationExam;
import com.prwatech.skillama.repository.GlobalCertificationExamRepository;
import com.prwatech.skillama.service.CertificationQuestionBankService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Monthly rebuild of active certification question banks (5× target).
 * Enforces global single-flight: at most one rebuild starts per run.
 */
@Component
@RequiredArgsConstructor
public class CertificationQuestionBankMonthlyJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(CertificationQuestionBankMonthlyJob.class);
    private static final String ACTOR = "monthly-bank-job";

    private final GlobalCertificationExamRepository certRepository;
    private final CertificationQuestionBankService bankService;

    /** 03:00 Asia/Kolkata on the 1st of each month. */
    @Scheduled(cron = "0 0 3 1 * *", zone = "Asia/Kolkata")
    public void rebuildActiveBanks() {
        if (bankService.hasFreshRunningRebuild()) {
            LOGGER.info("Monthly certification bank rebuild skipped — another rebuild is already running");
            return;
        }
        List<GlobalCertificationExam> active = certRepository.findByActiveTrue();
        LOGGER.info("Monthly certification bank rebuild scanning {} active certs (one at a time)", active.size());
        for (GlobalCertificationExam cert : active) {
            try {
                bankService.rebuildIfIdle(cert.getId(), ACTOR);
                if (bankService.hasFreshRunningRebuild()) {
                    LOGGER.info("Monthly job started rebuild for {}; remaining certs wait for a later run",
                            cert.getId());
                    return;
                }
            } catch (Exception e) {
                LOGGER.warn("Monthly bank rebuild failed for {}: {}", cert.getId(), e.getMessage());
            }
        }
    }
}
