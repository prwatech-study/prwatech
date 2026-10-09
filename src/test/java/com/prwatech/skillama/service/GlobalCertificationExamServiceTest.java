package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.GlobalCertificationExamDTO;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.CertificationBankBuildStatus;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    void effectiveBankStatusDemotesIncompleteReadyRows() {
        GlobalCertificationExam partial = GlobalCertificationExam.builder()
                .bankStatus(CertificationBankBuildStatus.READY)
                .bankVersion(1).bankTargetSize(275).bankQuestionCount(231).build();
        assertEquals(CertificationBankBuildStatus.FAILED,
                GlobalCertificationExamService.effectiveBankStatus(partial, 55));

        GlobalCertificationExam complete = GlobalCertificationExam.builder()
                .bankStatus(CertificationBankBuildStatus.READY)
                .bankVersion(1).bankTargetSize(275).bankQuestionCount(275).build();
        assertEquals(CertificationBankBuildStatus.READY,
                GlobalCertificationExamService.effectiveBankStatus(complete, 55));
    }

    @Test
    void isBankReadyOnlyForACompleteBank() {
        GlobalCertificationExam complete = GlobalCertificationExam.builder()
                .bankVersion(1).bankTargetSize(275).bankQuestionCount(275).build();
        assertTrue(GlobalCertificationExamService.isBankReady(complete, 55));

        GlobalCertificationExam partial = GlobalCertificationExam.builder()
                .bankVersion(1).bankTargetSize(275).bankQuestionCount(231).build();
        assertFalse(GlobalCertificationExamService.isBankReady(partial, 55));

        GlobalCertificationExam noVersion = GlobalCertificationExam.builder()
                .bankVersion(0).bankTargetSize(275).bankQuestionCount(275).build();
        assertFalse(GlobalCertificationExamService.isBankReady(noVersion, 55));

        assertFalse(GlobalCertificationExamService.isBankReady(null, 55));
    }

    @Test
    void isBankReadyFallsBackToRebuildTargetFormulaWhenTargetMissing() {
        GlobalCertificationExam full = GlobalCertificationExam.builder()
                .bankVersion(1).bankQuestionCount(275).build();
        GlobalCertificationExam oneShort = GlobalCertificationExam.builder()
                .bankVersion(1).bankQuestionCount(274).build();
        assertTrue(GlobalCertificationExamService.isBankReady(full, 55));
        assertFalse(GlobalCertificationExamService.isBankReady(oneShort, 55));

        // Small exams still need the 50-question floor the rebuild targets.
        GlobalCertificationExam small = GlobalCertificationExam.builder()
                .bankVersion(1).bankQuestionCount(25).build();
        assertFalse(GlobalCertificationExamService.isBankReady(small, 5));
        small.setBankQuestionCount(50);
        assertTrue(GlobalCertificationExamService.isBankReady(small, 5));
    }

    @Test
    void listAllReportsOldPartialReadyBankAsNotReady() {
        GlobalCertificationExam partial = GlobalCertificationExam.builder()
                .id("c3").provider("GCP").tier(CertificationTier.FOUNDATIONAL).name("CDL").active(true)
                .bankStatus(CertificationBankBuildStatus.READY)
                .bankVersion(1).bankTargetSize(275).bankQuestionCount(231)
                .build();
        when(repository.findAll()).thenReturn(List.of(partial));

        GlobalCertificationExamDTO dto = service.listAll(false).get(0);

        assertFalse(dto.isBankReady());
        assertEquals(CertificationBankBuildStatus.FAILED, dto.getBankStatus(),
                "incomplete banks must not be reported as READY");
        assertEquals(231, dto.getBankQuestionCount());
        assertEquals(275, dto.getBankTargetSize());
    }

    @Test
    void listAllExposesBuildProgressOnlyWhileRunning() {
        GlobalCertificationExam running = GlobalCertificationExam.builder()
                .id("c1").provider("GCP").tier(CertificationTier.FOUNDATIONAL).name("CDL").active(true)
                .bankStatus(CertificationBankBuildStatus.RUNNING)
                .bankBuildStartedAt(LocalDateTime.now().minusMinutes(5))
                .bankVersion(1).bankTargetSize(275).bankQuestionCount(275).bankBuildQuestionCount(40)
                .build();
        GlobalCertificationExam failed = GlobalCertificationExam.builder()
                .id("c2").provider("GCP").tier(CertificationTier.ASSOCIATE).name("ACE").active(true)
                .bankStatus(CertificationBankBuildStatus.FAILED)
                .bankVersion(1).bankTargetSize(275).bankQuestionCount(275).bankBuildQuestionCount(99)
                .build();
        when(repository.findAll()).thenReturn(List.of(running, failed));

        List<GlobalCertificationExamDTO> dtos = service.listAll(false);
        GlobalCertificationExamDTO runningDto = dtos.stream().filter(d -> "c1".equals(d.getId())).findFirst().orElseThrow();
        GlobalCertificationExamDTO failedDto = dtos.stream().filter(d -> "c2".equals(d.getId())).findFirst().orElseThrow();

        assertEquals(40, runningDto.getBankBuildQuestionCount());
        assertTrue(runningDto.isBankReady(), "complete live bank stays usable during a rebuild");
        assertNull(failedDto.getBankBuildQuestionCount());
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

    @Test
    void listAllAllowsRebuildOnIdleRowsWhileAnotherIsRunning() {
        GlobalCertificationExam running = GlobalCertificationExam.builder()
                .id("c1")
                .provider("GCP")
                .tier(CertificationTier.FOUNDATIONAL)
                .name("CDL")
                .active(true)
                .bankStatus(CertificationBankBuildStatus.RUNNING)
                .bankBuildStartedAt(LocalDateTime.now().minusMinutes(5))
                .bankVersion(0)
                .bankQuestionCount(0)
                .build();
        GlobalCertificationExam idle = GlobalCertificationExam.builder()
                .id("c2")
                .provider("GCP")
                .tier(CertificationTier.ASSOCIATE)
                .name("ACE")
                .active(true)
                .bankStatus(CertificationBankBuildStatus.READY)
                .bankVersion(1)
                .bankTargetSize(275)
                .bankQuestionCount(275)
                .build();
        when(repository.findAll()).thenReturn(List.of(running, idle));

        List<GlobalCertificationExamDTO> dtos = service.listAll(false);

        assertEquals(2, dtos.size());
        GlobalCertificationExamDTO runningDto = dtos.stream().filter(d -> "c1".equals(d.getId())).findFirst().orElseThrow();
        GlobalCertificationExamDTO idleDto = dtos.stream().filter(d -> "c2".equals(d.getId())).findFirst().orElseThrow();
        assertFalse(runningDto.isRebuildAllowed());
        assertTrue(idleDto.isRebuildAllowed());
    }

    @Test
    void listAllAllowsRebuildWhenNoFreshRunning() {
        GlobalCertificationExam idle = GlobalCertificationExam.builder()
                .id("c2")
                .provider("GCP")
                .tier(CertificationTier.ASSOCIATE)
                .name("ACE")
                .active(true)
                .bankStatus(CertificationBankBuildStatus.READY)
                .bankVersion(1)
                .bankQuestionCount(55)
                .build();
        when(repository.findAll()).thenReturn(List.of(idle));

        List<GlobalCertificationExamDTO> dtos = service.listAll(false);

        assertEquals(1, dtos.size());
        assertTrue(dtos.get(0).isRebuildAllowed());
    }
}
