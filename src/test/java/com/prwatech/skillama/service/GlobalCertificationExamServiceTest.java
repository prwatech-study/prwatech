package com.prwatech.skillama.service;

import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.CertificationExamMeta;
import com.prwatech.skillama.model.CertificationTier;
import com.prwatech.skillama.model.GlobalCertificationExam;
import com.prwatech.skillama.repository.CertificationBankQuestionRepository;
import com.prwatech.skillama.repository.GlobalCertificationExamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalCertificationExamServiceTest {

    @Mock private GlobalCertificationExamRepository repository;
    @Mock private CertificationBankQuestionRepository bankQuestionRepository;
    @Mock private CertificationGuidelinesFetcher guidelinesFetcher;

    private GlobalCertificationExamService service;

    @BeforeEach
    void setUp() {
        service = new GlobalCertificationExamService(repository, bankQuestionRepository, guidelinesFetcher);
    }

    @Test
    void deleteRemovesBankQuestionsBeforeCatalogRow() {
        when(repository.existsById("cert-1")).thenReturn(true);

        service.delete("cert-1");

        InOrder order = inOrder(bankQuestionRepository, repository);
        order.verify(bankQuestionRepository).deleteByCertificationExamId("cert-1");
        order.verify(repository).deleteById("cert-1");
    }

    @Test
    void deleteThrowsWhenMissing() {
        when(repository.existsById("missing")).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> service.delete("missing"));
        verify(bankQuestionRepository, never()).deleteByCertificationExamId("missing");
        verify(repository, never()).deleteById("missing");
    }

    @Test
    void isBankReadyRequiresVersionAndEnoughQuestions() {
        GlobalCertificationExam ready = GlobalCertificationExam.builder()
                .bankVersion(1)
                .bankQuestionCount(55)
                .build();
        assertTrue(GlobalCertificationExamService.isBankReady(ready, 55));

        // Partial bank (>=20) is usable even when under full exam size (55).
        GlobalCertificationExam partial = GlobalCertificationExam.builder()
                .bankVersion(1)
                .bankQuestionCount(32)
                .build();
        assertTrue(GlobalCertificationExamService.isBankReady(partial, 55));

        GlobalCertificationExam thin = GlobalCertificationExam.builder()
                .bankVersion(1)
                .bankQuestionCount(10)
                .build();
        // need=55 → min(55,20)=20 threshold
        assertFalse(GlobalCertificationExamService.isBankReady(thin, 55));

        GlobalCertificationExam noVersion = GlobalCertificationExam.builder()
                .bankVersion(0)
                .bankQuestionCount(100)
                .build();
        assertFalse(GlobalCertificationExamService.isBankReady(noVersion, 50));

        assertFalse(GlobalCertificationExamService.isBankReady(null, 50));
    }

    @Test
    void isBankReadyUsesLowerThresholdWhenExamIsSmall() {
        GlobalCertificationExam small = GlobalCertificationExam.builder()
                .bankVersion(1)
                .bankQuestionCount(12)
                .build();
        // need=12 → min(12,20)=12
        assertTrue(GlobalCertificationExamService.isBankReady(small, 12));
        assertFalse(GlobalCertificationExamService.isBankReady(small, 15));
    }

    @Test
    void targetQuestionCountUsesMidpointAndSwapsInvertedRange() {
        assertEquals(55, service.targetQuestionCount(CertificationExamMeta.builder()
                .questionCountMin(50).questionCountMax(60).build()));
        assertEquals(55, service.targetQuestionCount(CertificationExamMeta.builder()
                .questionCountMin(60).questionCountMax(50).build()));
        assertEquals(55, service.targetQuestionCount(null));
        assertEquals(10, service.targetQuestionCount(CertificationExamMeta.builder()
                .questionCountMin(1).questionCountMax(2).build()));
    }

    @Test
    void timeLimitSecondsUsesDurationWithFloor() {
        assertEquals(5400, service.timeLimitSeconds(CertificationExamMeta.builder()
                .durationMinutes(90).build()));
        assertEquals(15 * 60, service.timeLimitSeconds(CertificationExamMeta.builder()
                .durationMinutes(5).build()));
        assertEquals(90 * 60, service.timeLimitSeconds(null));
    }

    @Test
    void nameKeyCollapsesWhitespaceAndLowercases() {
        assertEquals("cloud digital leader", GlobalCertificationExamService.nameKey("  Cloud   Digital Leader "));
    }

    @Test
    void requireReturnsRow() {
        GlobalCertificationExam row = GlobalCertificationExam.builder()
                .id("c1")
                .provider("GCP")
                .tier(CertificationTier.FOUNDATIONAL)
                .name("CDL")
                .build();
        when(repository.findById("c1")).thenReturn(java.util.Optional.of(row));
        assertEquals("CDL", service.require("c1").getName());
    }

    @Test
    void requireThrowsWhenMissing() {
        when(repository.findById("x")).thenReturn(java.util.Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.require("x"));
    }
}
