package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.GeneratedCertificationExamDTO;
import com.prwatech.skillama.dto.ModuleQuizOptionDTO;
import com.prwatech.skillama.dto.ModuleQuizQuestionDTO;
import com.prwatech.skillama.model.CertificationBankBuildStatus;
import com.prwatech.skillama.model.CertificationBankQuestion;
import com.prwatech.skillama.model.CertificationExamMeta;
import com.prwatech.skillama.model.ExamQuestionType;
import com.prwatech.skillama.model.GlobalCertificationExam;
import com.prwatech.skillama.repository.CertificationBankQuestionRepository;
import com.prwatech.skillama.repository.GlobalCertificationExamRepository;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Drives a full bank rebuild through {@link CertificationQuestionBankService#requestRebuild}
 * with a scripted fake AI, then asserts what the bank ends up with.
 * Target bank size here is 50 (exam size 10 × 5).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CertificationBankRebuildLoopTest {

    private static final String CERT_ID = "c1";
    private static final List<String> DOMAINS = List.of("Cloud Concepts", "Security");

    @Mock private GlobalCertificationExamRepository certRepository;
    @Mock private CertificationBankQuestionRepository bankQuestionRepository;
    @Mock private GlobalCertificationExamService certService;
    @Mock private SkillamaAiClient skillamaAiClient;
    @Mock private SkillamaUserRepository userRepository;
    @Mock private AiUsageService aiUsageService;
    @Mock private MongoTemplate skillamaMongoTemplate;

    private final Executor syncExecutor = Runnable::run;
    private final List<Call> calls = new ArrayList<>();
    private CertificationQuestionBankService service;
    private GlobalCertificationExam cert;

    record Call(int requested, List<String> exclude, String focusDomain, String focusAngle, int diversity) {
    }

    @FunctionalInterface
    interface Script {
        List<ModuleQuizQuestionDTO> respond(int callIndex, Call call);
    }

    @BeforeEach
    void setUp() {
        service = new CertificationQuestionBankService(
                certRepository, bankQuestionRepository, certService, skillamaAiClient,
                userRepository, aiUsageService, skillamaMongoTemplate, syncExecutor);
        service.sleeper = ms -> { };

        cert = GlobalCertificationExam.builder()
                .id(CERT_ID)
                .provider("GCP")
                .name("Cloud Digital Leader")
                .active(true)
                .bankStatus(CertificationBankBuildStatus.READY)
                .bankVersion(0)
                .bankBuildVersion(1)
                .guidelinesSnapshot("Official guide text")
                .parsedMeta(CertificationExamMeta.builder().durationMinutes(90).domains(DOMAINS).build())
                .build();
        when(certService.targetQuestionCount(any())).thenReturn(10);
        when(certService.require(CERT_ID)).thenReturn(cert);
        when(certService.ensureFreshGuidelines(any())).thenAnswer(inv -> inv.getArgument(0));
        when(certRepository.findById(CERT_ID)).thenReturn(Optional.of(cert));
        when(skillamaMongoTemplate.find(any(Query.class), eq(GlobalCertificationExam.class))).thenReturn(List.of());
        when(skillamaMongoTemplate.exists(any(Query.class), eq(GlobalCertificationExam.class))).thenReturn(false);
        when(skillamaMongoTemplate.findAndModify(
                any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(GlobalCertificationExam.class)))
                .thenReturn(cert);
        when(bankQuestionRepository.save(any(CertificationBankQuestion.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @SuppressWarnings("unchecked")
    private void aiScript(Script script) {
        when(skillamaAiClient.generateCertificationExam(
                any(), any(), any(), any(), any(), any(), any(),
                anyInt(), anyInt(), anyBoolean(), any(), any(), any(), anyInt()))
                .thenAnswer(inv -> {
                    Call call = new Call(
                            inv.getArgument(7),
                            List.copyOf((List<String>) inv.getArgument(10)),
                            inv.getArgument(11),
                            inv.getArgument(12),
                            inv.getArgument(13));
                    int index = calls.size();
                    calls.add(call);
                    return GeneratedCertificationExamDTO.builder()
                            .questions(script.respond(index, call))
                            .build();
                });
    }

    private static ModuleQuizQuestionDTO question(String stem, String domain) {
        return ModuleQuizQuestionDTO.builder()
                .question(stem)
                .questionType(ExamQuestionType.SINGLE)
                .domain(domain)
                .options(List.of(
                        ModuleQuizOptionDTO.builder().key("A").text("a").build(),
                        ModuleQuizOptionDTO.builder().key("B").text("b").build()))
                .correctKey("A")
                .build();
    }

    /** {@code count} brand-new stems tagged with the requested focus domain. */
    private static List<ModuleQuizQuestionDTO> unique(int callIndex, int count, String domain) {
        return IntStream.range(0, count)
                .mapToObj(i -> question("Q" + callIndex + "-" + i + " about " + domain + "?", domain))
                .collect(Collectors.toList());
    }

    private Document finalSet() {
        ArgumentCaptor<Update> captor = ArgumentCaptor.forClass(Update.class);
        verify(skillamaMongoTemplate, atLeastOnce())
                .updateFirst(any(Query.class), captor.capture(), eq(GlobalCertificationExam.class));
        List<Update> all = captor.getAllValues();
        return (Document) all.get(all.size() - 1).getUpdateObject().get("$set");
    }

    @Test
    void fillsTheWholeBankWhenAiKeepsProducingUniqueQuestions() {
        aiScript((i, call) -> unique(i, call.requested(), call.focusDomain()));

        service.requestRebuild(CERT_ID, "admin-1");

        Document set = finalSet();
        assertEquals(CertificationBankBuildStatus.READY, set.get("bankStatus"));
        assertEquals(50, set.get("bankQuestionCount"));
        assertNull(set.get("bankBuildError"));
        verify(bankQuestionRepository).deleteByCertificationExamIdAndBankVersionLessThan(CERT_ID, 1);
    }

    @Test
    void rotatesFocusDomainAndAngleBetweenChunks() {
        aiScript((i, call) -> unique(i, call.requested(), call.focusDomain()));

        service.requestRebuild(CERT_ID, "admin-1");

        assertEquals("Cloud Concepts", calls.get(0).focusDomain());
        assertEquals("Security", calls.get(1).focusDomain());
        assertEquals("Cloud Concepts", calls.get(2).focusDomain());
        for (int i = 1; i < calls.size(); i++) {
            assertNotEquals(calls.get(i - 1).focusAngle(), calls.get(i).focusAngle(), "angle repeated at call " + i);
        }
    }

    @Test
    void excludeListIsFreshAndNotCappedAtTheOldFortyStemLimit() {
        aiScript((i, call) -> unique(i, call.requested(), call.focusDomain()));

        service.requestRebuild(CERT_ID, "admin-1");

        assertTrue(calls.get(0).exclude().isEmpty());
        Call last = calls.get(calls.size() - 1);
        assertEquals(48, last.exclude().size(), "every saved stem should be excluded");
        // Call 6 focuses Cloud Concepts: newest same-domain stem (from call 4) comes first.
        assertTrue(last.exclude().get(0).startsWith("Q4-7 about Cloud Concepts"), last.exclude().get(0));
    }

    @Test
    void duplicateChunksRaiseDiversityThenStopAndFailAsIncomplete() {
        aiScript((i, call) -> i < 3
                ? unique(i, call.requested(), call.focusDomain())
                : unique(0, 8, "Cloud Concepts")); // exact repeats of the first chunk

        service.requestRebuild(CERT_ID, "admin-1");

        assertEquals(8, calls.size(), "3 good chunks + 5 duplicate chunks, then stop");
        assertEquals(List.of(0, 1, 2, 3, 3),
                calls.subList(3, 8).stream().map(Call::diversity).collect(Collectors.toList()));
        Document set = finalSet();
        assertEquals(CertificationBankBuildStatus.FAILED, set.get("bankStatus"));
        String error = (String) set.get("bankBuildError");
        assertTrue(error.startsWith("INCOMPLETE_BANK: generated 24/50 questions"), error);
        assertTrue(error.contains("STOPPED_AFTER_5_CONSECUTIVE_FAILURES"), error);
        assertTrue(error.contains("NO_UNIQUE_QUESTIONS: AI returned 8 questions"), error);
        assertTrue(error.contains("diversity=3"), error);
    }

    @Test
    void incompleteBuildIsDiscardedAndPreviousCompleteBankStaysLive() {
        cert.setBankVersion(3);
        cert.setBankBuildVersion(4);
        when(bankQuestionRepository.countByCertificationExamIdAndBankVersionAndActiveTrue(CERT_ID, 3))
                .thenReturn(50L);
        aiScript((i, call) -> i < 2
                ? unique(i, call.requested(), call.focusDomain())
                : unique(0, 8, "Cloud Concepts"));

        service.requestRebuild(CERT_ID, "admin-1");

        Document set = finalSet();
        assertEquals(CertificationBankBuildStatus.FAILED, set.get("bankStatus"));
        assertEquals(50, set.get("bankQuestionCount"), "live count is the previous complete version");
        assertTrue(set.containsKey("bankBuildQuestionCount"));
        assertNull(set.get("bankBuildQuestionCount"));
        assertTrue(!set.containsKey("bankVersion"), "failed build must not change the live version");
        verify(bankQuestionRepository).deleteByCertificationExamIdAndBankVersion(CERT_ID, 4);
        verify(bankQuestionRepository, never()).deleteByCertificationExamIdAndBankVersionLessThan(any(), anyInt());
    }

    @Test
    void slowButSteadyBuildThatRunsOutOfRoundsFailsAsIncomplete() {
        // One new question every third call: never 5 dead rounds in a row, but too slow to finish.
        aiScript((i, call) -> i % 3 == 0
                ? unique(i, 1, call.focusDomain() != null ? call.focusDomain() : "Cloud Concepts")
                : List.of(question("Q0-0 about Cloud Concepts?", "Cloud Concepts")));

        service.requestRebuild(CERT_ID, "admin-1");

        assertEquals(CertificationQuestionBankService.MAX_BUILD_ROUNDS, calls.size());
        String error = (String) finalSet().get("bankBuildError");
        assertTrue(error.startsWith("INCOMPLETE_BANK: generated 30/50 questions"), error);
        assertTrue(error.contains("reached the " + CertificationQuestionBankService.MAX_BUILD_ROUNDS + "-round limit"), error);
        assertEquals(CertificationBankBuildStatus.FAILED, finalSet().get("bankStatus"));
    }

    @Test
    void progressHeartbeatsNeverTouchTheLiveQuestionCount() {
        aiScript((i, call) -> unique(i, call.requested(), call.focusDomain()));

        service.requestRebuild(CERT_ID, "admin-1");

        ArgumentCaptor<Update> captor = ArgumentCaptor.forClass(Update.class);
        verify(skillamaMongoTemplate, atLeastOnce())
                .updateFirst(any(Query.class), captor.capture(), eq(GlobalCertificationExam.class));
        List<Update> updates = captor.getAllValues();
        List<Document> heartbeats = updates.subList(0, updates.size() - 1).stream()
                .map(u -> (Document) u.getUpdateObject().get("$set"))
                .collect(Collectors.toList());
        assertTrue(heartbeats.size() >= 6);
        assertTrue(heartbeats.stream().noneMatch(s -> s.containsKey("bankQuestionCount")));
        assertEquals(50, heartbeats.get(heartbeats.size() - 1).get("bankBuildQuestionCount"));
        Document done = (Document) updates.get(updates.size() - 1).getUpdateObject().get("$set");
        assertEquals(50, done.get("bankQuestionCount"));
        assertNull(done.get("bankBuildQuestionCount"));
    }

    @Test
    void mostlyDuplicateChunkRaisesDiversityWithoutCountingAsFailure() {
        aiScript((i, call) -> {
            if (i == 1) {
                List<ModuleQuizQuestionDTO> mixed = new ArrayList<>(unique(0, 6, "Cloud Concepts"));
                mixed.addAll(unique(1, 2, call.focusDomain()));
                return mixed;
            }
            return unique(i, call.requested(), call.focusDomain());
        });

        service.requestRebuild(CERT_ID, "admin-1");

        assertEquals(1, calls.get(2).diversity(), "2/8 unique should raise diversity");
        assertEquals(0, calls.get(3).diversity(), "a clean chunk should bring it back down");
        assertEquals(CertificationBankBuildStatus.READY, finalSet().get("bankStatus"));
        assertEquals(50, finalSet().get("bankQuestionCount"));
    }

    @Test
    void aiErrorsStopTheRebuildAndKeepTheFullUntruncatedReason() {
        String longDetail = "BEDROCK_THROTTLE: ThrottlingException: " + "x".repeat(700);
        aiScript((i, call) -> {
            throw new IllegalStateException(longDetail);
        });

        service.requestRebuild(CERT_ID, "admin-1");

        assertEquals(15, calls.size(), "5 rounds × 3 attempts each");
        assertEquals(8, calls.get(0).requested());
        assertEquals(5, calls.get(1).requested(), "retry halves the chunk (floor 5)");
        Document set = finalSet();
        assertEquals(CertificationBankBuildStatus.FAILED, set.get("bankStatus"));
        String error = (String) set.get("bankBuildError");
        assertTrue(error.startsWith("INCOMPLETE_BANK: generated 0/50"), error);
        assertTrue(error.contains("Cause: STOPPED_AFTER_5_CONSECUTIVE_FAILURES"), error);
        assertTrue(error.contains(longDetail), "error must not be truncated");
    }

    @Test
    void exhaustedOrBogusDomainIsRetiredInsteadOfStallingTheRebuild() {
        String junk = "Prepare with Certification Prep webinars Watch Cloud OnAir";
        cert.getParsedMeta().setDomains(List.of("Cloud Concepts", junk, "Security"));
        aiScript((i, call) -> junk.equals(call.focusDomain())
                ? unique(0, 8, "Cloud Concepts") // AI can't invent new questions for a non-domain
                : unique(i, call.requested(), call.focusDomain()));

        service.requestRebuild(CERT_ID, "admin-1");

        long junkCalls = calls.stream().filter(c -> junk.equals(c.focusDomain())).count();
        assertEquals(CertificationQuestionBankService.DOMAIN_RETIRE_STRIKES, junkCalls);
        Document set = finalSet();
        assertEquals(CertificationBankBuildStatus.READY, set.get("bankStatus"));
        assertEquals(50, set.get("bankQuestionCount"));
    }

    @Test
    void retiredDomainsAreNamedInTheFailureReasonWhenTheBankEndsShort() {
        String junk = "Watch Cloud OnAir";
        cert.getParsedMeta().setDomains(List.of("Cloud Concepts", junk));
        aiScript((i, call) -> i < 3 && !junk.equals(call.focusDomain())
                ? unique(i, call.requested(), call.focusDomain())
                : unique(0, 8, "Cloud Concepts"));

        service.requestRebuild(CERT_ID, "admin-1");

        String note = (String) finalSet().get("bankBuildError");
        assertTrue(note.contains("Domains skipped after repeated duplicates: Watch Cloud OnAir"), note);
    }

    @Test
    void recoversWhenAChunkFailsOnceThenSucceeds() {
        aiScript((i, call) -> {
            if (i == 0) {
                throw new IllegalStateException("BEDROCK_TIMEOUT: Read timed out");
            }
            return unique(i, call.requested(), call.focusDomain());
        });

        service.requestRebuild(CERT_ID, "admin-1");

        Document set = finalSet();
        assertEquals(CertificationBankBuildStatus.READY, set.get("bankStatus"));
        assertEquals(50, set.get("bankQuestionCount"));
        assertNull(set.get("bankBuildError"));
    }
}
