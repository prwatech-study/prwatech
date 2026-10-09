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
    public static final String GLOBAL_REBUILD_BUSY_MESSAGE =
            "Only one certification bank rebuild can run at a time. Wait for the current rebuild to finish, then try again.";
    public static final String BANK_NOT_READY_MESSAGE =
            "The question bank is not ready yet. Ask an admin to rebuild it, or try again shortly.";

    /** If a rebuild stays RUNNING longer than this, treat the lock as stale (crash / killed JVM). */
    public static final Duration STALE_RUNNING_TIMEOUT = Duration.ofHours(2);

    /**
     * Keep chunks small — ai-tutor generates ~8 Q per Bedrock call inside one HTTP request.
     * Large chunks (e.g. 40) amplify timeout/failure risk and used to abort the whole rebuild.
     */
    private static final int AI_CHUNK_SIZE = 8;
    private static final int MAX_BUILD_ROUNDS = 60;
    private static final int CHUNK_ATTEMPTS = 3;
    private static final int MAX_CONSECUTIVE_CHUNK_FAILURES = 5;
    /** Same floor learners use in {@link #assemblePaper} / {@link GlobalCertificationExamService#isBankReady}. */
    static final int MIN_READY_QUESTIONS = 20;

    private final GlobalCertificationExamRepository certRepository;
    private final CertificationBankQuestionRepository bankQuestionRepository;
    private final GlobalCertificationExamService certService;
    private final SkillamaAiClient skillamaAiClient;
    private final SkillamaUserRepository userRepository;
    private final AiUsageService aiUsageService;
    private final MongoTemplate skillamaMongoTemplate;
    private final Executor bankExecutor;

    public CertificationQuestionBankService(
            GlobalCertificationExamRepository certRepository,
            CertificationBankQuestionRepository bankQuestionRepository,
            GlobalCertificationExamService certService,
            SkillamaAiClient skillamaAiClient,
            SkillamaUserRepository userRepository,
            AiUsageService aiUsageService,
            @Qualifier("skillamaMongoTemplate") MongoTemplate skillamaMongoTemplate,
            @Qualifier(CertificationBankAsyncConfig.EXECUTOR_NAME) Executor bankExecutor) {
        this.certRepository = certRepository;
        this.bankQuestionRepository = bankQuestionRepository;
        this.certService = certService;
        this.skillamaAiClient = skillamaAiClient;
        this.userRepository = userRepository;
        this.aiUsageService = aiUsageService;
        this.skillamaMongoTemplate = skillamaMongoTemplate;
        this.bankExecutor = bankExecutor;
    }

    /** Usage courseId used when metering bank rebuild AI calls. */
    public static String bankUsageCourseId(String certificationExamId) {
        return "cert-bank:" + certificationExamId;
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

    /**
     * Cancels every RUNNING rebuild immediately (e.g. after JVM redeploy).
     * In-flight async workers die with the process; without this, Mongo stays
     * RUNNING for up to {@link #STALE_RUNNING_TIMEOUT}.
     */
    public int cancelAllRunningRebuilds(String reason) {
        String message = StringUtils.hasText(reason)
                ? reason
                : "Bank rebuild cancelled. Click Rebuild to start again.";
        Query query = new Query(Criteria.where("bankStatus").is(CertificationBankBuildStatus.RUNNING));
        List<GlobalCertificationExam> running = skillamaMongoTemplate.find(query, GlobalCertificationExam.class);
        int cancelled = 0;
        for (GlobalCertificationExam cert : running) {
            markFailed(cert.getId(), message);
            cancelled++;
            log.warn("Cancelled RUNNING bank rebuild for cert {} ({})", cert.getId(), message);
        }
        return cancelled;
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
            if (ALREADY_RUNNING_MESSAGE.equals(e.getMessage())
                    || GLOBAL_REBUILD_BUSY_MESSAGE.equals(e.getMessage())) {
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
        LocalDateTime now = IndiaTime.now();
        LocalDateTime staleBefore = now.minus(STALE_RUNNING_TIMEOUT);
        // Global single-flight: only one fresh RUNNING rebuild across the whole catalog.
        if (hasFreshRunningRebuildExcluding(certificationExamId, staleBefore)) {
            throw new IllegalStateException(GLOBAL_REBUILD_BUSY_MESSAGE);
        }
        int nextBuildVersion = (existing.getBankVersion() != null ? existing.getBankVersion() : 0) + 1;
        int target = targetBankSize(existing.getParsedMeta());
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
            // Lost the race: this cert or another became RUNNING.
            if (hasFreshRunningRebuildExcluding(certificationExamId, staleBefore)) {
                throw new IllegalStateException(GLOBAL_REBUILD_BUSY_MESSAGE);
            }
            throw new IllegalStateException(ALREADY_RUNNING_MESSAGE);
        }
        return claimed;
    }

    /** True when any catalog row (optionally excluding one id) has a non-stale RUNNING rebuild. */
    public boolean hasFreshRunningRebuild() {
        return hasFreshRunningRebuildExcluding(null, IndiaTime.now().minus(STALE_RUNNING_TIMEOUT));
    }

    private boolean hasFreshRunningRebuildExcluding(String excludeId, LocalDateTime staleBefore) {
        Criteria criteria = new Criteria().andOperator(
                Criteria.where("bankStatus").is(CertificationBankBuildStatus.RUNNING),
                Criteria.where("bankBuildStartedAt").gte(staleBefore));
        if (StringUtils.hasText(excludeId)) {
            criteria = new Criteria().andOperator(
                    Criteria.where("id").ne(excludeId),
                    Criteria.where("bankStatus").is(CertificationBankBuildStatus.RUNNING),
                    Criteria.where("bankBuildStartedAt").gte(staleBefore));
        }
        return skillamaMongoTemplate.exists(new Query(criteria), GlobalCertificationExam.class);
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
        String usageCourseId = bankUsageCourseId(certId);
        LocalDateTime rebuildStartedAt = cert.getBankBuildStartedAt() != null
                ? cert.getBankBuildStartedAt()
                : IndiaTime.now();
        int consecutiveFailures = 0;
        String lastChunkError = null;

        for (int round = 0; round < MAX_BUILD_ROUNDS && saved.size() < target; round++) {
            int need = target - saved.size();
            int chunk = Math.min(AI_CHUNK_SIZE, Math.max(5, need));
            GeneratedCertificationExamDTO generated;
            try {
                generated = generateChunkWithRetry(
                        actor,
                        usageCourseId,
                        cert,
                        domains,
                        chunk,
                        durationMinutes,
                        allowMulti,
                        excludeStems);
                consecutiveFailures = 0;
            } catch (RuntimeException e) {
                consecutiveFailures++;
                lastChunkError = e.getMessage();
                log.warn("Cert bank chunk failed for {} round {} ({}/{}): {}",
                        certId, round, consecutiveFailures, MAX_CONSECUTIVE_CHUNK_FAILURES, e.toString());
                if (consecutiveFailures >= MAX_CONSECUTIVE_CHUNK_FAILURES) {
                    log.error("Stopping bank rebuild for {} after {} consecutive AI failures",
                            certId, consecutiveFailures);
                    break;
                }
                sleepQuietly(750L * consecutiveFailures);
                continue;
            }
            if (generated.getQuestions() == null || generated.getQuestions().isEmpty()) {
                log.warn("Empty AI chunk for cert {} round {}", certId, round);
                consecutiveFailures++;
                if (consecutiveFailures >= MAX_CONSECUTIVE_CHUNK_FAILURES) {
                    break;
                }
                continue;
            }
            LocalDateTime now = IndiaTime.now();
            int addedThisRound = 0;
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
                addedThisRound++;
                if (excludeStems.size() < 80) {
                    excludeStems.add(trimStem(dto.getQuestion()));
                }
                if (saved.size() >= target) {
                    break;
                }
            }
            if (addedThisRound == 0) {
                consecutiveFailures++;
                if (consecutiveFailures >= MAX_CONSECUTIVE_CHUNK_FAILURES) {
                    break;
                }
            }
            // Progress heartbeat for admin UI (+ live rebuild cost)
            refreshBankCosts(certId, rebuildStartedAt, saved.size());
        }

        // Promote once we have a usable practice pool; full exam size / 5× are aspirational.
        if (saved.size() < MIN_READY_QUESTIONS) {
            refreshBankCosts(certId, rebuildStartedAt, saved.size());
            String detail = lastChunkError != null
                    ? lastChunkError
                    : ("Bank rebuild produced only " + saved.size()
                            + " unique questions (need at least " + MIN_READY_QUESTIONS + ").");
            throw new IllegalStateException(detail);
        }

        LocalDateTime finished = IndiaTime.now();
        String softNote = null;
        if (saved.size() < target) {
            softNote = "Bank ready with " + saved.size() + "/" + target
                    + " questions (practice papers use available questions"
                    + (saved.size() < examQ ? "; under full exam size of " + examQ : "")
                    + "; rebuild later to grow toward 5×).";
        }
        double lifetimeCost = aiUsageService.sumCostUsdForCourse(usageCourseId);
        double lastRebuildCost = aiUsageService.sumCostUsdForCourseSince(usageCourseId, rebuildStartedAt);
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
                        .set("bankBuildError", softNote)
                        .set("bankLifetimeCostUsd", lifetimeCost)
                        .set("bankLastRebuildCostUsd", lastRebuildCost)
                        .set("updatedAt", finished),
                GlobalCertificationExam.class);
        bankQuestionRepository.deleteByCertificationExamIdAndBankVersionLessThan(certId, newVersion);
        log.info("Certification bank READY for {} version {} size {} (target {}) lifetime=${} last=${}",
                certId, newVersion, saved.size(), target, lifetimeCost, lastRebuildCost);
    }

    /**
     * Persists lifetime + current-rebuild cost from {@link AiUsageEvent} rows tagged
     * {@code cert-bank:{id}}. Safe to call during RUNNING heartbeats and on failure.
     */
    private void refreshBankCosts(String certId, LocalDateTime rebuildStartedAt, Integer questionCount) {
        String usageCourseId = bankUsageCourseId(certId);
        double lifetimeCost = aiUsageService.sumCostUsdForCourse(usageCourseId);
        double lastRebuildCost = aiUsageService.sumCostUsdForCourseSince(usageCourseId, rebuildStartedAt);
        Update update = new Update()
                .set("bankLifetimeCostUsd", lifetimeCost)
                .set("bankLastRebuildCostUsd", lastRebuildCost)
                .set("updatedAt", IndiaTime.now());
        if (questionCount != null) {
            update.set("bankQuestionCount", questionCount);
        }
        skillamaMongoTemplate.updateFirst(
                new Query(Criteria.where("id").is(certId)),
                update,
                GlobalCertificationExam.class);
    }

    private GeneratedCertificationExamDTO generateChunkWithRetry(
            User actor,
            String usageCourseId,
            GlobalCertificationExam cert,
            List<String> domains,
            int requestedChunk,
            int durationMinutes,
            boolean allowMulti,
            List<String> excludeStems) {
        int chunk = Math.max(5, requestedChunk);
        RuntimeException last = null;
        for (int attempt = 1; attempt <= CHUNK_ATTEMPTS; attempt++) {
            try {
                return skillamaAiClient.generateCertificationExam(
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
            } catch (RuntimeException e) {
                last = e;
                log.warn("AI cert chunk attempt {}/{} failed for {} (chunk={}): {}",
                        attempt, CHUNK_ATTEMPTS, cert.getId(), chunk, e.getMessage());
                chunk = Math.max(5, chunk / 2);
                if (attempt < CHUNK_ATTEMPTS) {
                    sleepQuietly(500L * attempt);
                }
            }
        }
        throw last != null ? last : new IllegalStateException("Certification bank chunk generation failed");
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(Math.max(0, ms));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private void markFailed(String certId, String message) {
        String err = message != null ? message : "Bank rebuild failed";
        if (err.length() > 500) {
            err = err.substring(0, 500);
        }
        GlobalCertificationExam before = certRepository.findById(certId).orElse(null);
        Integer buildVersion = before != null ? before.getBankBuildVersion() : null;
        int readyVersion = before != null && before.getBankVersion() != null ? before.getBankVersion() : 0;
        long readyCount = readyVersion > 0
                ? bankQuestionRepository.countByCertificationExamIdAndBankVersionAndActiveTrue(certId, readyVersion)
                : 0L;
        String usageCourseId = bankUsageCourseId(certId);
        LocalDateTime rebuildStartedAt = before != null ? before.getBankBuildStartedAt() : null;
        double lifetimeCost = aiUsageService.sumCostUsdForCourse(usageCourseId);
        double lastRebuildCost = aiUsageService.sumCostUsdForCourseSince(usageCourseId, rebuildStartedAt);
        skillamaMongoTemplate.updateFirst(
                new Query(Criteria.where("id").is(certId)),
                new Update()
                        .set("bankStatus", CertificationBankBuildStatus.FAILED)
                        .set("bankBuildVersion", null)
                        .set("bankQuestionCount", (int) readyCount)
                        .set("bankBuildFinishedAt", IndiaTime.now())
                        .set("bankBuildError", err)
                        .set("bankLifetimeCostUsd", lifetimeCost)
                        .set("bankLastRebuildCostUsd", lastRebuildCost)
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
                .rebuildAllowed(!hasFreshRunningRebuild()
                        || (status == CertificationBankBuildStatus.RUNNING && isStaleRunning(cert)))
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
