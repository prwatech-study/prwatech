package com.prwatech.skillama.service;

import com.prwatech.skillama.config.CertificationBankAsyncConfig;
import com.prwatech.skillama.dto.CertificationBankQuestionDTO;
import com.prwatech.skillama.dto.CertificationBankStatusDTO;
import com.prwatech.skillama.dto.GeneratedCertificationExamDTO;
import com.prwatech.skillama.dto.GlobalCertificationExamDTO;
import com.prwatech.skillama.dto.ModuleQuizOptionDTO;
import com.prwatech.skillama.dto.ModuleQuizQuestionDTO;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.CertificationBankBuildStatus;
import com.prwatech.skillama.model.CertificationBankQuestion;
import com.prwatech.skillama.model.CertificationExamMeta;
import com.prwatech.skillama.model.ExamQuestionType;
import com.prwatech.skillama.model.ExamSession;
import com.prwatech.skillama.model.GlobalCertificationExam;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.CertificationBankQuestionRepository;
import com.prwatech.skillama.repository.GlobalCertificationExamRepository;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import com.prwatech.skillama.util.IndiaTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * Builds and serves 5× certification question banks.
 * Rebuild is concurrency-safe: only one RUNNING build per cert at a time.
 */
@Service
@Slf4j
public class CertificationQuestionBankService {

    public static final int BANK_MULTIPLIER = 5;
    public static final String ALREADY_RUNNING_MESSAGE =
            "A question-bank rebuild is already running for this certification.";
    public static final String BANK_NOT_READY_MESSAGE =
            "The question bank is not ready yet. Ask an admin to rebuild it, or try again shortly.";

    /** If a rebuild stays RUNNING longer than this, treat the lock as stale (crash / killed JVM). */
    public static final Duration STALE_RUNNING_TIMEOUT = Duration.ofHours(2);

    private static final int AI_CHUNK_SIZE = 40;
    private static final int MAX_BUILD_ROUNDS = 20;

    private final GlobalCertificationExamRepository certRepository;
    private final CertificationBankQuestionRepository bankQuestionRepository;
    private final GlobalCertificationExamService certService;
    private final SkillamaAiClient skillamaAiClient;
    private final SkillamaUserRepository userRepository;
    private final MongoTemplate skillamaMongoTemplate;
    private final Executor bankExecutor;

    public CertificationQuestionBankService(
            GlobalCertificationExamRepository certRepository,
            CertificationBankQuestionRepository bankQuestionRepository,
            GlobalCertificationExamService certService,
            SkillamaAiClient skillamaAiClient,
            SkillamaUserRepository userRepository,
            @Qualifier("skillamaMongoTemplate") MongoTemplate skillamaMongoTemplate,
            @Qualifier(CertificationBankAsyncConfig.EXECUTOR_NAME) Executor bankExecutor) {
        this.certRepository = certRepository;
        this.bankQuestionRepository = bankQuestionRepository;
        this.certService = certService;
        this.skillamaAiClient = skillamaAiClient;
        this.userRepository = userRepository;
        this.skillamaMongoTemplate = skillamaMongoTemplate;
        this.bankExecutor = bankExecutor;
    }

    public CertificationBankStatusDTO getBankStatus(String certificationExamId) {
        recoverStaleRunningLocks();
        GlobalCertificationExam cert = certService.require(certificationExamId);
        return toStatus(cert);
    }

    /**
     * Marks RUNNING banks older than {@link #STALE_RUNNING_TIMEOUT} as FAILED and
     * deletes any partial build-version questions so Rebuild can be clicked again.
     */
    public int recoverStaleRunningLocks() {
        LocalDateTime cutoff = IndiaTime.now().minus(STALE_RUNNING_TIMEOUT);
        Query query = new Query(new Criteria().andOperator(
                Criteria.where("bankStatus").is(CertificationBankBuildStatus.RUNNING),
                new Criteria().orOperator(
                        Criteria.where("bankBuildStartedAt").lt(cutoff),
                        Criteria.where("bankBuildStartedAt").is(null))));
        List<GlobalCertificationExam> stale = skillamaMongoTemplate.find(query, GlobalCertificationExam.class);
        int recovered = 0;
        for (GlobalCertificationExam cert : stale) {
            // markFailed clears the lock and deletes partial build-version questions.
            markFailed(cert.getId(),
                    "Bank rebuild timed out (no progress for " + STALE_RUNNING_TIMEOUT.toHours()
                            + "h). You can rebuild again.");
            recovered++;
            log.warn("Recovered stale RUNNING bank lock for cert {}", cert.getId());
        }
        return recovered;
    }

    public List<CertificationBankQuestionDTO> listBankQuestions(String certificationExamId) {
        GlobalCertificationExam cert = certService.require(certificationExamId);
        int version = cert.getBankVersion() != null ? cert.getBankVersion() : 0;
        if (version <= 0) {
            return List.of();
        }
        return bankQuestionRepository
                .findByCertificationExamIdAndBankVersionAndActiveTrueOrderByCreatedAtAsc(
                        certificationExamId, version)
                .stream()
                .map(this::toQuestionDto)
                .collect(Collectors.toList());
    }

    /**
     * Claims the rebuild lock (if free) and starts an async bank build.
     * @throws IllegalStateException if a rebuild is already RUNNING
     */
    public GlobalCertificationExamDTO requestRebuild(String certificationExamId, String actorId) {
        recoverStaleRunningLocks();
        GlobalCertificationExam claimed = claimRebuildLock(certificationExamId, actorId);
        User actor = StringUtils.hasText(actorId) ? userRepository.findById(actorId).orElse(null) : null;
        final String certId = claimed.getId();
        final int buildVersion = claimed.getBankBuildVersion() != null
                ? claimed.getBankBuildVersion()
                : ((claimed.getBankVersion() != null ? claimed.getBankVersion() : 0) + 1);
        bankExecutor.execute(() -> {
            try {
                runRebuild(certId, buildVersion, actor);
            } catch (Exception e) {
                log.error("Certification bank rebuild failed for {}", certId, e);
                markFailed(certId, e.getMessage());
            }
        });
        return certService.getById(certId);
    }

    /**
     * Assembles an exam paper from the current bank (no live AI generation).
     */
    public List<ModuleQuizQuestionDTO> assemblePaper(GlobalCertificationExam cert, int examQuestionCount) {
        int version = cert.getBankVersion() != null ? cert.getBankVersion() : 0;
        int available = cert.getBankQuestionCount() != null ? cert.getBankQuestionCount() : 0;
        // Prefer READY banks; allow a READY-sized pool even if status briefly lags.
        // Serve from last READY version even while a rebuild is RUNNING.
        boolean usable = version > 0 && available >= Math.min(examQuestionCount, 20);
        if (!usable) {
            throw new IllegalStateException(BANK_NOT_READY_MESSAGE);
        }

        List<CertificationBankQuestion> pool = bankQuestionRepository
                .findByCertificationExamIdAndBankVersionAndActiveTrueOrderByCreatedAtAsc(
                        cert.getId(), version);
        if (pool.size() < Math.min(examQuestionCount, 10)) {
            throw new IllegalStateException(BANK_NOT_READY_MESSAGE);
        }

        int take = Math.min(examQuestionCount, pool.size());
        List<CertificationBankQuestion> selected = selectBalanced(pool, take);
        List<ModuleQuizQuestionDTO> out = new ArrayList<>();
        int id = 1;
        for (CertificationBankQuestion q : selected) {
            out.add(ModuleQuizQuestionDTO.builder()
                    .id(id++)
                    .question(q.getQuestion())
                    .questionType(q.getQuestionType() != null ? q.getQuestionType() : ExamQuestionType.SINGLE)
                    .options(q.getOptions() == null ? List.of() : q.getOptions().stream()
                            .map(o -> ModuleQuizOptionDTO.builder().key(o.getKey()).text(o.getText()).build())
                            .collect(Collectors.toList()))
                    .correctKey(q.getCorrectKey())
                    .correctKeys(q.getCorrectKeys())
                    .explanation(q.getExplanation())
                    .domain(q.getDomain())
                    .build());
        }
        return out;
    }

    public int targetBankSize(CertificationExamMeta meta) {
        return Math.max(50, certService.targetQuestionCount(meta) * BANK_MULTIPLIER);
    }

    /** Monthly / batch entry: skip certs already RUNNING. */
    public void rebuildIfIdle(String certificationExamId, String actorId) {
        recoverStaleRunningLocks();
        GlobalCertificationExam cert = certService.require(certificationExamId);
        if (cert.getBankStatus() == CertificationBankBuildStatus.RUNNING) {
            log.info("Skipping monthly rebuild for {} — already RUNNING", certificationExamId);
            return;
        }
        try {
            requestRebuild(certificationExamId, actorId);
        } catch (IllegalStateException e) {
            if (ALREADY_RUNNING_MESSAGE.equals(e.getMessage())) {
                log.info("Skipping rebuild for {} — {}", certificationExamId, e.getMessage());
            } else {
                throw e;
            }
        }
    }

    private GlobalCertificationExam claimRebuildLock(String certificationExamId, String actorId) {
        GlobalCertificationExam existing = certService.require(certificationExamId);
        // If another node left a stale RUNNING, clear it before claiming.
        if (existing.getBankStatus() == CertificationBankBuildStatus.RUNNING
                && isStaleRunning(existing)) {
            recoverStaleRunningLocks();
            existing = certService.require(certificationExamId);
        }
        int nextBuildVersion = (existing.getBankVersion() != null ? existing.getBankVersion() : 0) + 1;
        int target = targetBankSize(existing.getParsedMeta());
        LocalDateTime now = IndiaTime.now();
        LocalDateTime staleBefore = now.minus(STALE_RUNNING_TIMEOUT);
        // Allow claim when idle/ready/failed, OR when RUNNING but stale (race-safe reclaim).
        Query query = new Query(new Criteria().andOperator(
                Criteria.where("id").is(certificationExamId),
                new Criteria().orOperator(
                        Criteria.where("bankStatus").ne(CertificationBankBuildStatus.RUNNING),
                        new Criteria().andOperator(
                                Criteria.where("bankStatus").is(CertificationBankBuildStatus.RUNNING),
                                new Criteria().orOperator(
                                        Criteria.where("bankBuildStartedAt").lt(staleBefore),
                                        Criteria.where("bankBuildStartedAt").is(null))))));
        Update update = new Update()
                .set("bankStatus", CertificationBankBuildStatus.RUNNING)
                .set("bankBuildVersion", nextBuildVersion)
                .set("bankTargetSize", target)
                .set("bankBuildStartedAt", now)
                .set("bankBuildFinishedAt", null)
                .set("bankBuildError", null)
                .set("bankBuildTriggeredBy", actorId)
                .set("updatedAt", now)
                .set("updatedBy", actorId);
        // Do NOT bump bankVersion here — learners keep using the last READY version.
        GlobalCertificationExam claimed = skillamaMongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                GlobalCertificationExam.class);
        if (claimed == null) {
            throw new IllegalStateException(ALREADY_RUNNING_MESSAGE);
        }
        return claimed;
    }

    private static boolean isStaleRunning(GlobalCertificationExam cert) {
        if (cert == null || cert.getBankStatus() != CertificationBankBuildStatus.RUNNING) {
            return false;
        }
        if (cert.getBankBuildStartedAt() == null) {
            return true;
        }
        return cert.getBankBuildStartedAt().isBefore(IndiaTime.now().minus(STALE_RUNNING_TIMEOUT));
    }

    private void runRebuild(String certId, int newVersion, User actor) {
        GlobalCertificationExam cert = certService.require(certId);
        cert = certService.ensureFreshGuidelines(cert);
        if (!StringUtils.hasText(cert.getGuidelinesSnapshot())) {
            throw new IllegalStateException("Guidelines snapshot is missing; refresh guidelines first.");
        }

        int examQ = certService.targetQuestionCount(cert.getParsedMeta());
        int target = targetBankSize(cert.getParsedMeta());
        int durationMinutes = cert.getParsedMeta() != null && cert.getParsedMeta().getDurationMinutes() != null
                ? cert.getParsedMeta().getDurationMinutes() : 90;
        List<String> domains = cert.getParsedMeta() != null && cert.getParsedMeta().getDomains() != null
                ? cert.getParsedMeta().getDomains() : List.of();
        String formatNotes = cert.getParsedMeta() != null ? cert.getParsedMeta().getFormatNotes() : null;
        boolean allowMulti = formatNotes == null
                || formatNotes.toLowerCase(Locale.ROOT).contains("select")
                || formatNotes.toLowerCase(Locale.ROOT).contains("multi");

        Set<String> seenNorms = new HashSet<>();
        List<String> excludeStems = new ArrayList<>();
        List<CertificationBankQuestion> saved = new ArrayList<>();
        String usageCourseId = "cert-bank:" + certId;

        for (int round = 0; round < MAX_BUILD_ROUNDS && saved.size() < target; round++) {
            int need = target - saved.size();
            int chunk = Math.min(AI_CHUNK_SIZE, Math.max(10, need));
            GeneratedCertificationExamDTO generated = skillamaAiClient.generateCertificationExam(
                    actor,
                    usageCourseId,
                    cert.getProvider(),
                    cert.getName(),
                    cert.getTier() != null ? cert.getTier().name() : null,
                    cert.getGuidelinesSnapshot(),
                    domains,
                    chunk,
                    durationMinutes,
                    allowMulti,
                    excludeStems);
            if (generated.getQuestions() == null || generated.getQuestions().isEmpty()) {
                log.warn("Empty AI chunk for cert {} round {}", certId, round);
                continue;
            }
            LocalDateTime now = IndiaTime.now();
            for (ModuleQuizQuestionDTO dto : generated.getQuestions()) {
                if (!StringUtils.hasText(dto.getQuestion())) {
                    continue;
                }
                String norm = normalizeStem(dto.getQuestion());
                if (!seenNorms.add(norm)) {
                    continue;
                }
                ExamQuestionType type = dto.getQuestionType() != null
                        ? dto.getQuestionType() : ExamQuestionType.SINGLE;
                List<String> correctKeys = dto.getCorrectKeys() != null && !dto.getCorrectKeys().isEmpty()
                        ? dto.getCorrectKeys()
                        : (StringUtils.hasText(dto.getCorrectKey())
                                ? List.of(dto.getCorrectKey().toUpperCase(Locale.ROOT))
                                : List.of());
                List<ExamSession.ExamOption> options = dto.getOptions() == null ? List.of()
                        : dto.getOptions().stream()
                                .map(o -> ExamSession.ExamOption.builder().key(o.getKey()).text(o.getText()).build())
                                .collect(Collectors.toList());

                CertificationBankQuestion row = CertificationBankQuestion.builder()
                        .certificationExamId(certId)
                        .bankVersion(newVersion)
                        .active(true)
                        .questionId(saved.size() + 1)
                        .question(dto.getQuestion().trim())
                        .questionNorm(norm)
                        .questionType(type)
                        .options(options)
                        .correctKey(correctKeys.isEmpty() ? dto.getCorrectKey() : correctKeys.get(0))
                        .correctKeys(correctKeys)
                        .explanation(dto.getExplanation())
                        .domain(dto.getDomain())
                        .createdAt(now)
                        .build();
                saved.add(bankQuestionRepository.save(row));
                if (excludeStems.size() < 80) {
                    excludeStems.add(trimStem(dto.getQuestion()));
                }
                if (saved.size() >= target) {
                    break;
                }
            }
            // Progress heartbeat for admin UI
            skillamaMongoTemplate.updateFirst(
                    new Query(Criteria.where("id").is(certId)),
                    new Update().set("bankQuestionCount", saved.size()).set("updatedAt", IndiaTime.now()),
                    GlobalCertificationExam.class);
        }

        if (saved.size() < Math.max(examQ, 20)) {
            throw new IllegalStateException(
                    "Bank rebuild produced only " + saved.size() + " unique questions (need ~" + target + ").");
        }

        LocalDateTime finished = IndiaTime.now();
        // Promote build version to live bankVersion, then drop older versions.
        skillamaMongoTemplate.updateFirst(
                new Query(Criteria.where("id").is(certId)),
                new Update()
                        .set("bankStatus", CertificationBankBuildStatus.READY)
                        .set("bankVersion", newVersion)
                        .set("bankBuildVersion", null)
                        .set("bankQuestionCount", saved.size())
                        .set("bankTargetSize", target)
                        .set("bankBuildFinishedAt", finished)
                        .set("bankBuildError", null)
                        .set("updatedAt", finished),
                GlobalCertificationExam.class);
        bankQuestionRepository.deleteByCertificationExamIdAndBankVersionLessThan(certId, newVersion);
        log.info("Certification bank READY for {} version {} size {}", certId, newVersion, saved.size());
    }

    private void markFailed(String certId, String message) {
        String err = message != null ? message : "Bank rebuild failed";
        if (err.length() > 500) {
            err = err.substring(0, 500);
        }
        GlobalCertificationExam before = certRepository.findById(certId).orElse(null);
        Integer buildVersion = before != null ? before.getBankBuildVersion() : null;
        skillamaMongoTemplate.updateFirst(
                new Query(Criteria.where("id").is(certId)),
                new Update()
                        .set("bankStatus", CertificationBankBuildStatus.FAILED)
                        .set("bankBuildVersion", null)
                        .set("bankBuildFinishedAt", IndiaTime.now())
                        .set("bankBuildError", err)
                        .set("updatedAt", IndiaTime.now()),
                GlobalCertificationExam.class);
        // Drop partial questions for the failed build version; keep last READY version intact.
        if (buildVersion != null && buildVersion > 0) {
            bankQuestionRepository.deleteByCertificationExamIdAndBankVersion(certId, buildVersion);
        }
    }

    /**
     * Prefer spreading across domains, then shuffle remainder.
     */
    private List<CertificationBankQuestion> selectBalanced(List<CertificationBankQuestion> pool, int take) {
        Map<String, List<CertificationBankQuestion>> byDomain = new LinkedHashMap<>();
        for (CertificationBankQuestion q : pool) {
            String d = StringUtils.hasText(q.getDomain()) ? q.getDomain() : "_general";
            byDomain.computeIfAbsent(d, k -> new ArrayList<>()).add(q);
        }
        for (List<CertificationBankQuestion> list : byDomain.values()) {
            Collections.shuffle(list);
        }
        List<CertificationBankQuestion> picked = new ArrayList<>();
        boolean added;
        do {
            added = false;
            for (List<CertificationBankQuestion> list : byDomain.values()) {
                if (picked.size() >= take) {
                    break;
                }
                if (!list.isEmpty()) {
                    picked.add(list.remove(0));
                    added = true;
                }
            }
        } while (added && picked.size() < take);
        Collections.shuffle(picked);
        return picked;
    }

    static String normalizeStem(String question) {
        return question.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static String trimStem(String question) {
        String t = question.trim();
        return t.length() > 160 ? t.substring(0, 160) : t;
    }

    public CertificationBankStatusDTO toStatus(GlobalCertificationExam cert) {
        CertificationBankBuildStatus status = cert.getBankStatus() != null
                ? cert.getBankStatus() : CertificationBankBuildStatus.IDLE;
        int examQ = certService.targetQuestionCount(cert.getParsedMeta());
        return CertificationBankStatusDTO.builder()
                .certificationExamId(cert.getId())
                .bankStatus(status)
                .bankVersion(cert.getBankVersion())
                .bankTargetSize(cert.getBankTargetSize() != null
                        ? cert.getBankTargetSize()
                        : targetBankSize(cert.getParsedMeta()))
                .bankQuestionCount(cert.getBankQuestionCount() != null ? cert.getBankQuestionCount() : 0)
                .bankMultiplier(BANK_MULTIPLIER)
                .rebuildAllowed(status != CertificationBankBuildStatus.RUNNING || isStaleRunning(cert))
                .bankReady(GlobalCertificationExamService.isBankReady(cert, examQ))
                .bankBuildStartedAt(cert.getBankBuildStartedAt())
                .bankBuildFinishedAt(cert.getBankBuildFinishedAt())
                .bankBuildTriggeredBy(cert.getBankBuildTriggeredBy())
                .bankBuildError(cert.getBankBuildError())
                .build();
    }

    private CertificationBankQuestionDTO toQuestionDto(CertificationBankQuestion q) {
        return CertificationBankQuestionDTO.builder()
                .id(q.getId())
                .certificationExamId(q.getCertificationExamId())
                .bankVersion(q.getBankVersion())
                .questionId(q.getQuestionId())
                .question(q.getQuestion())
                .questionType(q.getQuestionType())
                .options(q.getOptions() == null ? List.of() : q.getOptions().stream()
                        .map(o -> ModuleQuizOptionDTO.builder().key(o.getKey()).text(o.getText()).build())
                        .collect(Collectors.toList()))
                .correctKey(q.getCorrectKey())
                .correctKeys(q.getCorrectKeys())
                .explanation(q.getExplanation())
                .domain(q.getDomain())
                .createdAt(q.getCreatedAt())
                .build();
    }
}
