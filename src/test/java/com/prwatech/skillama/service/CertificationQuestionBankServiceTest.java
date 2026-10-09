package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.CertificationBankStatusDTO;
import com.prwatech.skillama.dto.ModuleQuizQuestionDTO;
import com.prwatech.skillama.model.CertificationBankBuildStatus;
import com.prwatech.skillama.model.CertificationBankQuestion;
import com.prwatech.skillama.model.CertificationExamMeta;
import com.prwatech.skillama.model.ExamQuestionType;
import com.prwatech.skillama.model.ExamSession;
import com.prwatech.skillama.model.GlobalCertificationExam;
import com.prwatech.skillama.repository.CertificationBankQuestionRepository;
import com.prwatech.skillama.repository.GlobalCertificationExamRepository;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CertificationQuestionBankServiceTest {

    @Mock private GlobalCertificationExamRepository certRepository;
    @Mock private CertificationBankQuestionRepository bankQuestionRepository;
    @Mock private GlobalCertificationExamService certService;
    @Mock private SkillamaAiClient skillamaAiClient;
    @Mock private SkillamaUserRepository userRepository;
    @Mock private MongoTemplate skillamaMongoTemplate;

    private final Executor syncExecutor = Runnable::run;

    private CertificationQuestionBankService service;

    @BeforeEach
    void setUp() {
        service = new CertificationQuestionBankService(
                certRepository,
                bankQuestionRepository,
                certService,
                skillamaAiClient,
                userRepository,
                skillamaMongoTemplate,
                syncExecutor);
        when(certService.targetQuestionCount(any())).thenReturn(50);
    }

    private GlobalCertificationExam cert(String id, CertificationBankBuildStatus status,
                                         Integer version, Integer count) {
        return GlobalCertificationExam.builder()
                .id(id)
                .provider("GCP")
                .name("Cloud Digital Leader")
                .active(true)
                .bankStatus(status)
                .bankVersion(version)
                .bankQuestionCount(count)
                .bankTargetSize(250)
                .parsedMeta(CertificationExamMeta.builder()
                        .questionCountMin(50)
                        .questionCountMax(60)
                        .durationMinutes(90)
                        .domains(List.of("Cloud Concepts", "Security"))
                        .build())
                .build();
    }

    private List<CertificationBankQuestion> pool(String certId, int size) {
        return IntStream.rangeClosed(1, size)
                .mapToObj(i -> CertificationBankQuestion.builder()
                        .id("q" + i)
                        .certificationExamId(certId)
                        .bankVersion(1)
                        .active(true)
                        .questionId(i)
                        .question("Question " + i + "?")
                        .questionType(ExamQuestionType.SINGLE)
                        .options(List.of(
                                ExamSession.ExamOption.builder().key("A").text("a").build(),
                                ExamSession.ExamOption.builder().key("B").text("b").build()))
                        .correctKey("A")
                        .correctKeys(List.of("A"))
                        .domain(i % 2 == 0 ? "Security" : "Cloud Concepts")
                        .build())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @Test
    void assemblePaperFailsFastWhenBankNotReady() {
        GlobalCertificationExam idle = cert("c1", CertificationBankBuildStatus.IDLE, 0, 0);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.assemblePaper(idle, 50));
        assertEquals(CertificationQuestionBankService.BANK_NOT_READY_MESSAGE, ex.getMessage());
        verify(bankQuestionRepository, never())
                .findByCertificationExamIdAndBankVersionAndActiveTrueOrderByCreatedAtAsc(anyString(), any(Integer.class));
    }

    @Test
    void assemblePaperFailsWhenPoolTooThinEvenIfCountsLookReady() {
        GlobalCertificationExam ready = cert("c1", CertificationBankBuildStatus.READY, 1, 55);
        when(bankQuestionRepository
                .findByCertificationExamIdAndBankVersionAndActiveTrueOrderByCreatedAtAsc("c1", 1))
                .thenReturn(pool("c1", 5)); // below min(50,10)=10

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.assemblePaper(ready, 50));
        assertEquals(CertificationQuestionBankService.BANK_NOT_READY_MESSAGE, ex.getMessage());
    }

    @Test
    void assemblePaperServesLastReadyVersionEvenWhileRebuildRunning() {
        GlobalCertificationExam rebuilding = cert("c1", CertificationBankBuildStatus.RUNNING, 1, 55);
        when(bankQuestionRepository
                .findByCertificationExamIdAndBankVersionAndActiveTrueOrderByCreatedAtAsc("c1", 1))
                .thenReturn(pool("c1", 55));

        List<ModuleQuizQuestionDTO> paper = service.assemblePaper(rebuilding, 50);

        assertEquals(50, paper.size());
        assertEquals(1, paper.get(0).getId());
        assertTrue(paper.stream().allMatch(q -> q.getQuestion() != null));
    }

    @Test
    void recoverStaleRunningLocksMarksFailedAndDeletesPartialBuild() {
        GlobalCertificationExam stale = cert("c1", CertificationBankBuildStatus.RUNNING, 1, 55);
        stale.setBankBuildVersion(2);
        stale.setBankBuildStartedAt(LocalDateTime.now().minusHours(3));
        when(skillamaMongoTemplate.find(any(Query.class), eq(GlobalCertificationExam.class)))
                .thenReturn(List.of(stale));
        when(certRepository.findById("c1")).thenReturn(Optional.of(stale));

        int recovered = service.recoverStaleRunningLocks();

        assertEquals(1, recovered);
        verify(skillamaMongoTemplate).updateFirst(any(Query.class), any(Update.class), eq(GlobalCertificationExam.class));
        verify(bankQuestionRepository).deleteByCertificationExamIdAndBankVersion("c1", 2);
    }

    @Test
    void requestRebuildRejectsWhenFreshRebuildAlreadyRunning() {
        GlobalCertificationExam running = cert("c1", CertificationBankBuildStatus.RUNNING, 1, 55);
        running.setBankBuildStartedAt(LocalDateTime.now().minusMinutes(10));
        when(skillamaMongoTemplate.find(any(Query.class), eq(GlobalCertificationExam.class)))
                .thenReturn(List.of()); // nothing stale
        when(certService.require("c1")).thenReturn(running);
        when(skillamaMongoTemplate.findAndModify(
                any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(GlobalCertificationExam.class)))
                .thenReturn(null);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.requestRebuild("c1", "admin-1"));
        assertEquals(CertificationQuestionBankService.ALREADY_RUNNING_MESSAGE, ex.getMessage());
    }

    @Test
    void rebuildIfIdleSkipsFreshRunningWithoutThrowing() {
        GlobalCertificationExam running = cert("c1", CertificationBankBuildStatus.RUNNING, 1, 55);
        running.setBankBuildStartedAt(LocalDateTime.now().minusMinutes(5));
        when(skillamaMongoTemplate.find(any(Query.class), eq(GlobalCertificationExam.class)))
                .thenReturn(List.of());
        when(certService.require("c1")).thenReturn(running);

        service.rebuildIfIdle("c1", "job");

        verify(skillamaMongoTemplate, never()).findAndModify(
                any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(GlobalCertificationExam.class));
    }

    @Test
    void toStatusAllowsRebuildWhenRunningLockIsStale() {
        GlobalCertificationExam stale = cert("c1", CertificationBankBuildStatus.RUNNING, 1, 55);
        stale.setBankBuildStartedAt(LocalDateTime.now().minusHours(3));

        CertificationBankStatusDTO status = service.toStatus(stale);

        assertTrue(status.isRebuildAllowed());
        assertEquals(CertificationQuestionBankService.BANK_MULTIPLIER, status.getBankMultiplier());
    }

    @Test
    void toStatusBlocksRebuildWhileFreshRunning() {
        GlobalCertificationExam running = cert("c1", CertificationBankBuildStatus.RUNNING, 1, 55);
        running.setBankBuildStartedAt(LocalDateTime.now().minusMinutes(30));

        CertificationBankStatusDTO status = service.toStatus(running);

        assertFalse(status.isRebuildAllowed());
    }

    @Test
    void targetBankSizeIsFiveTimesExamQuestionCount() {
        when(certService.targetQuestionCount(any())).thenReturn(55);
        assertEquals(275, service.targetBankSize(CertificationExamMeta.builder()
                .questionCountMin(50).questionCountMax(60).build()));
    }

    @Test
    void normalizeStemCollapsesCaseAndWhitespace() {
        assertEquals(
                "what is iam?",
                CertificationQuestionBankService.normalizeStem("  What   is IAM?  "));
    }

    @Test
    void listBankQuestionsReturnsEmptyWhenNoVersion() {
        when(certService.require("c1")).thenReturn(cert("c1", CertificationBankBuildStatus.IDLE, 0, 0));
        assertTrue(service.listBankQuestions("c1").isEmpty());
    }
}
