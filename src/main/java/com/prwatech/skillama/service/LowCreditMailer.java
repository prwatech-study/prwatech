package com.prwatech.skillama.service;

import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.LowCreditEmailLog;
import com.prwatech.skillama.repository.LowCreditEmailLogRepository;
import com.prwatech.skillama.service.SkillamaHtmlMailer.MailSendResult;
import com.prwatech.skillama.util.IndiaTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/** Sends the low-credit recharge email and keeps a row admins can resend. */
@Service
@RequiredArgsConstructor
public class LowCreditMailer {

    private static final int MAX_PAGE_SIZE = 100;

    private final SkillamaHtmlMailer mailer;
    private final LowCreditEmailLogRepository logRepository;

    public void send(String userId, String email, String name, String creditsLeft) {
        if (email == null || email.isBlank() || creditsLeft == null) {
            return;
        }
        MailSendResult result = mailer.sendLowCredits(email.trim(), name, creditsLeft);
        LowCreditEmailLog row = LowCreditEmailLog.builder()
                .userId(userId)
                .userName(name)
                .email(email.trim())
                .creditsLeft(creditsLeft)
                .attemptCount(1)
                .createdAt(IndiaTime.now())
                .build();
        applyResult(row, result);
        logRepository.save(row);
    }

    public Page<LowCreditEmailLog> list(String status, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(MAX_PAGE_SIZE, Math.max(1, size));
        PageRequest request = PageRequest.of(safePage, safeSize);
        if (status == null || status.isBlank() || "all".equalsIgnoreCase(status)) {
            return logRepository.findAllByOrderByUpdatedAtDesc(request);
        }
        LowCreditEmailLog.Status parsed;
        try {
            parsed = LowCreditEmailLog.Status.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("status must be SENT or FAILED");
        }
        return logRepository.findByStatusOrderByUpdatedAtDesc(parsed, request);
    }

    public LowCreditEmailLog resend(String id) {
        LowCreditEmailLog row = logRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Low credit email not found"));
        MailSendResult result = mailer.sendLowCredits(row.getEmail(), row.getUserName(), row.getCreditsLeft());
        row.setAttemptCount(row.getAttemptCount() + 1);
        applyResult(row, result);
        return logRepository.save(row);
    }

    private static void applyResult(LowCreditEmailLog row, MailSendResult result) {
        row.setStatus(result.isDelivered() ? LowCreditEmailLog.Status.SENT : LowCreditEmailLog.Status.FAILED);
        row.setError(result.isDelivered() ? null : result.getError());
        row.setUpdatedAt(IndiaTime.now());
    }
}
