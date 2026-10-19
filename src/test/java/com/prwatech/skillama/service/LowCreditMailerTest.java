package com.prwatech.skillama.service;

import com.prwatech.skillama.model.LowCreditEmailLog;
import com.prwatech.skillama.repository.LowCreditEmailLogRepository;
import com.prwatech.skillama.service.SkillamaHtmlMailer.MailSendResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LowCreditMailerTest {

    @Mock private SkillamaHtmlMailer mailer;
    @Mock private LowCreditEmailLogRepository logRepository;

    @Test
    void recordsAFailedSendSoAnAdminCanResendIt() {
        when(mailer.sendLowCredits("a@b.com", "Ada", "7")).thenReturn(MailSendResult.failed("smtp down"));
        when(logRepository.save(any(LowCreditEmailLog.class))).thenAnswer(inv -> inv.getArgument(0));
        LowCreditMailer service = new LowCreditMailer(mailer, logRepository);

        service.send("user-1", "a@b.com", "Ada", "7");

        ArgumentCaptor<LowCreditEmailLog> captor = ArgumentCaptor.forClass(LowCreditEmailLog.class);
        verify(logRepository).save(captor.capture());
        assertEquals(LowCreditEmailLog.Status.FAILED, captor.getValue().getStatus());
        assertEquals("smtp down", captor.getValue().getError());
        assertEquals("user-1", captor.getValue().getUserId());
        assertEquals(1, captor.getValue().getAttemptCount());
    }

    @Test
    void resendMarksTheSameRowSent() {
        LowCreditEmailLog existing = LowCreditEmailLog.builder()
                .id("row-1")
                .email("a@b.com")
                .userName("Ada")
                .creditsLeft("7")
                .status(LowCreditEmailLog.Status.FAILED)
                .attemptCount(1)
                .build();
        when(logRepository.findById("row-1")).thenReturn(Optional.of(existing));
        when(mailer.sendLowCredits("a@b.com", "Ada", "7")).thenReturn(MailSendResult.sent());
        when(logRepository.save(any(LowCreditEmailLog.class))).thenAnswer(inv -> inv.getArgument(0));
        LowCreditMailer service = new LowCreditMailer(mailer, logRepository);

        LowCreditEmailLog updated = service.resend("row-1");

        assertEquals(LowCreditEmailLog.Status.SENT, updated.getStatus());
        assertNull(updated.getError());
        assertEquals(2, updated.getAttemptCount());
    }
}
