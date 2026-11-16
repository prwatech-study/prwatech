package com.prwatech.skillama.service.outreach;

import com.prwatech.skillama.dto.OutreachCampaignUpdateDTO;
import com.prwatech.skillama.dto.OutreachDailyRowDTO;
import com.prwatech.skillama.dto.OutreachSummaryDTO;
import com.prwatech.skillama.dto.OutreachTemplatePreviewDTO;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.model.outreach.OutreachCampaign;
import com.prwatech.skillama.model.outreach.OutreachDailyRollup;
import com.prwatech.skillama.model.outreach.OutreachRecipient;
import com.prwatech.skillama.model.outreach.OutreachWaveDelivery;
import com.prwatech.skillama.repository.OutreachCampaignRepository;
import com.prwatech.skillama.repository.OutreachDailyRollupRepository;
import com.prwatech.skillama.repository.OutreachRecipientRepository;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import com.prwatech.skillama.service.SkillamaHtmlMailer.MailSendResult;
import com.prwatech.skillama.service.UserContactService;
import com.prwatech.skillama.config.OutreachAsyncConfig;
import com.prwatech.skillama.util.EmailValidation;
import com.prwatech.skillama.util.IndiaTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import java.util.concurrent.Executor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Service
@Slf4j
public class PrwatechOutreachService {

    private static final int MAX_SMTP_ATTEMPTS = 3;
    private static final int MIN_HOURS_BETWEEN_SENDS = 24;
    private static final String FAILURE_MAX = "MAX_ATTEMPTS_EXCEEDED";

    /** Prevents overlapping Run batch / cron jobs on one JVM instance. */
    private final Object batchLock = new Object();

    private final OutreachCampaignRepository campaignRepository;
    private final OutreachRecipientRepository recipientRepository;
    private final OutreachDailyRollupRepository rollupRepository;
    private final SkillamaUserRepository userRepository;
    private final UserContactService userContactService;
    private final OutreachInviteMailer inviteMailer;
    private final MongoTemplate skillamaMongoTemplate;
    private final MongoTemplate mainMongoTemplate;
    private final Executor outreachBatchExecutor;

    public PrwatechOutreachService(
            OutreachCampaignRepository campaignRepository,
            OutreachRecipientRepository recipientRepository,
            OutreachDailyRollupRepository rollupRepository,
            SkillamaUserRepository userRepository,
            UserContactService userContactService,
            OutreachInviteMailer inviteMailer,
            @Qualifier("skillamaMongoTemplate") MongoTemplate skillamaMongoTemplate,
            @Qualifier("mongoTemplate") MongoTemplate mainMongoTemplate,
            @Qualifier(OutreachAsyncConfig.OUTREACH_BATCH_EXECUTOR) Executor outreachBatchExecutor) {
        this.campaignRepository = campaignRepository;
        this.recipientRepository = recipientRepository;
        this.rollupRepository = rollupRepository;
        this.userRepository = userRepository;
        this.userContactService = userContactService;
        this.inviteMailer = inviteMailer;
        this.skillamaMongoTemplate = skillamaMongoTemplate;
        this.mainMongoTemplate = mainMongoTemplate;
        this.outreachBatchExecutor = outreachBatchExecutor;
    }

    @Value("${skillama.app.public-url:https://skillama.co.in}")
    private String publicSiteUrl;

    @Value("${skillama.base.url:https://prwatech.xyz}")
    private String apiBaseUrl;

    public OutreachCampaign ensureCampaign() {
        return campaignRepository.findById(OutreachCampaign.SINGLETON_ID).orElseGet(() -> {
            OutreachCampaign c = OutreachCampaign.builder()
                    .id(OutreachCampaign.SINGLETON_ID)
                    .status(OutreachCampaign.Status.DRAFT)
                    .currentWave(1)
                    .dailyBatchSize(500)
                    .signupBonusUsd(1.0)
                    .updatedAt(IndiaTime.now())
                    .build();
            return campaignRepository.save(c);
        });
    }

    @Transactional
    public SyncResult syncLegacyRecipients() {
        OutreachCampaign campaign = ensureCampaign();
        requireOutreachEnabled(campaign);
        long eligible = 0, onSkillama = 0, disabled = 0, invalid = 0, duplicate = 0, queuedNew = 0;
        Query legacyQuery = new Query();
        legacyQuery.addCriteria(new Criteria().orOperator(
                Criteria.where("Disable").is(null),
                Criteria.where("Disable").is(false)));
        List<com.prwatech.user.model.User> legacyUsers =
                mainMongoTemplate.find(legacyQuery, com.prwatech.user.model.User.class);

        Set<String> seenHashes = new HashSet<>();
        for (com.prwatech.user.model.User legacy : legacyUsers) {
            String rawEmail = legacy.getEmail();
            if (rawEmail == null || rawEmail.isBlank()) {
                invalid++;
                continue;
            }
            try {
                EmailValidation.assertValidFormat(rawEmail);
            } catch (IllegalArgumentException e) {
                invalid++;
                continue;
            }
            if (Boolean.TRUE.equals(legacy.getDisable())) {
                disabled++;
                continue;
            }
            eligible++;
            String norm = userContactService.normalizeEmail(rawEmail);
            if (userRepository.findByEmail(norm).isPresent()
                    || userRepository.findByEmailIgnoreCase(norm).isPresent()) {
                onSkillama++;
                continue;
            }
            String hash = sha256(norm);
            if (!seenHashes.add(hash)) {
                duplicate++;
                continue;
            }
            if (recipientRepository.existsByEmailHash(hash)) {
                duplicate++;
                continue;
            }
            OutreachRecipient recipient = OutreachRecipient.builder()
                    .email(norm)
                    .emailHash(hash)
                    .legacyPrwatechUserId(legacy.getId())
                    .displayName(legacy.getName())
                    .trackingToken(UUID.randomUUID().toString().replace("-", ""))
                    .lifecycle(OutreachRecipient.Lifecycle.ACTIVE)
                    .createdAt(IndiaTime.now())
                    .updatedAt(IndiaTime.now())
                    .build();
            OutreachWaveDelivery w1 = recipient.wave(1);
            w1.setStatus(OutreachWaveDelivery.Status.PENDING);
            recipient.wave(2).setStatus(OutreachWaveDelivery.Status.IDLE);
            recipient.wave(3).setStatus(OutreachWaveDelivery.Status.IDLE);
            recipientRepository.save(recipient);
            queuedNew++;
        }
        campaign.setLastSyncAt(IndiaTime.now());
        campaign.setSyncEligibleLegacy(eligible);
        campaign.setSyncExcludedOnSkillama(onSkillama);
        campaign.setSyncExcludedDisabled(disabled);
        campaign.setSyncExcludedInvalid(invalid);
        campaign.setSyncExcludedDuplicate(duplicate);
        campaign.setSyncQueuedNew(queuedNew);
        campaign.setUpdatedAt(IndiaTime.now());
        campaignRepository.save(campaign);
        return new SyncResult(queuedNew, eligible);
    }

    public record SyncResult(long queuedNew, long eligibleLegacy) {}

    public OutreachSummaryDTO getSummary() {
        OutreachCampaign campaign = ensureCampaign();
        long queuedTotal = recipientRepository.count();
        long optedOut = recipientRepository.countByLifecycle(OutreachRecipient.Lifecycle.OPTED_OUT);
        long registered = recipientRepository.countByLifecycle(OutreachRecipient.Lifecycle.REGISTERED);
        long suppressed = recipientRepository.countByLifecycle(OutreachRecipient.Lifecycle.SUPPRESSED);

        long wave1Submitted = countWaveSubmitted(1);
        long wave2Submitted = countWaveSubmitted(2);
        long wave3Submitted = countWaveSubmitted(3);
        long submitted = wave1Submitted + wave2Submitted + wave3Submitted;

        long pendingInWave = countPendingForWave(campaign.getCurrentWave());
        long retryPending = countRetryPendingForWave(campaign.getCurrentWave());
        long uniqueOpens = countUniqueOpens();
        long uniqueClicks = countUniqueClicks();
        long landings = skillamaMongoTemplate.count(
                new Query(Criteria.where("landingHits").gt(0)), OutreachRecipient.class);

        int submittedToday = submittedTodayForDisplay(campaign);
        int cap = campaign.getDailyBatchSize();
        int remainingCap = Math.max(0, cap - submittedToday);

        Double openRate = submitted > 0 ? uniqueOpens / (double) submitted : null;
        Double clickRate = submitted > 0 ? uniqueClicks / (double) submitted : null;
        Double regRate = submitted > 0 ? registered / (double) submitted : null;

        LocalDate todayIst = IndiaTime.today();
        LocalDate from = todayIst.minusDays(30);
        List<OutreachDailyRowDTO> daily = rollupRepository.findByDateBetweenOrderByDateAsc(from, todayIst)
                .stream()
                .map(r -> OutreachDailyRowDTO.builder()
                        .date(r.getDate())
                        .wave(r.getWave())
                        .attempted(r.getAttempted())
                        .submitted(r.getSubmitted())
                        .failed(r.getFailed())
                        .uniqueOpens(r.getUniqueOpens())
                        .uniqueClicks(r.getUniqueClicks())
                        .registrations(r.getRegistrations())
                        .bonusesGranted(r.getBonusesGranted())
                        .unsubscribes(r.getUnsubscribes())
                        .build())
                .toList();

        String samplePlain = OutreachInviteMailer.buildPlainBody(
                1, "Alex", publicSiteUrl + "/welcome/prwatech?ocid=sample",
                apiBaseUrl + "/skillama/public/outreach/unsubscribe/sample",
                trimBase(publicSiteUrl));

        return OutreachSummaryDTO.builder()
                .campaignStatus(campaign.getStatus().name())
                .outreachEnabled(campaign.isOutreachEnabled())
                .currentWave(campaign.getCurrentWave())
                .maxWaves(campaign.getMaxWaves())
                .dailyCap(cap)
                .submittedToday(submittedToday)
                .remainingTodayCapacity(remainingCap)
                .queuedTotal(queuedTotal)
                .pendingInCurrentWave(pendingInWave)
                .retryPending(retryPending)
                .submitted(submitted)
                .sendFailed(countFailedPermanent())
                .exhaustedRetries(countByFailureReason(FAILURE_MAX))
                .suppressed(suppressed)
                .optedOut(optedOut)
                .registrationsAttributed(registered)
                .bonusGranted(countBonusGranted())
                .uniqueOpens(uniqueOpens)
                .uniqueClicks(uniqueClicks)
                .unsubscribes(optedOut)
                .landings(landings)
                .wave1Submitted(wave1Submitted)
                .wave2Submitted(wave2Submitted)
                .wave3Submitted(wave3Submitted)
                .wave2EligibleSnapshot(campaign.getWave2EligibleSnapshot())
                .wave3EligibleSnapshot(campaign.getWave3EligibleSnapshot())
                .eligibleLegacyCount(campaign.getSyncEligibleLegacy())
                .excludedAlreadyOnSkillama(campaign.getSyncExcludedOnSkillama())
                .excludedDisabled(campaign.getSyncExcludedDisabled())
                .excludedInvalidEmail(campaign.getSyncExcludedInvalid())
                .lastBatchSubmitted(campaign.getLastJobSubmitted())
                .lastBatchFailed(campaign.getLastJobFailed())
                .lastBatchDurationSec(campaign.getLastJobDurationSec())
                .batchInProgress(campaign.isBatchInProgress())
                .batchStartedAt(campaign.getBatchStartedAt())
                .openRate(openRate)
                .clickRate(clickRate)
                .registrationRate(regRate)
                .estimatedDaysRemainingCurrentWave(pendingInWave > 0 && cap > 0
                        ? (int) Math.ceil(pendingInWave / (double) cap) : 0)
                .failureReasonBuckets(failureBuckets())
                .dailySeries(daily)
                .templatePreview(OutreachTemplatePreviewDTO.builder()
                        .subjectWave1(OutreachInviteMailer.subjectForWave(1))
                        .subjectWave2(OutreachInviteMailer.subjectForWave(2))
                        .subjectWave3(OutreachInviteMailer.subjectForWave(3))
                        .plainBodySample(samplePlain)
                        .build())
                .build();
    }

    public OutreachCampaign updateCampaign(OutreachCampaignUpdateDTO dto) {
        OutreachCampaign campaign = ensureCampaign();
        if (dto.getOutreachEnabled() != null) {
            campaign.setOutreachEnabled(dto.getOutreachEnabled());
            if (!dto.getOutreachEnabled() && campaign.getStatus() == OutreachCampaign.Status.RUNNING) {
                campaign.setStatus(OutreachCampaign.Status.PAUSED);
            }
        }
        if (dto.getStatus() != null && !dto.getStatus().isBlank()) {
            OutreachCampaign.Status next = OutreachCampaign.Status.valueOf(dto.getStatus().trim().toUpperCase());
            if (next == OutreachCampaign.Status.RUNNING && !campaign.isOutreachEnabled()) {
                throw new IllegalStateException("Enable outreach before setting status to RUNNING");
            }
            campaign.setStatus(next);
            if (next == OutreachCampaign.Status.RUNNING && campaign.getStartedAt() == null) {
                campaign.setStartedAt(IndiaTime.now());
            }
            if (next == OutreachCampaign.Status.COMPLETED) {
                campaign.setCompletedAt(IndiaTime.now());
            }
        }
        if (dto.getDailyBatchSize() != null && dto.getDailyBatchSize() > 0) {
            campaign.setDailyBatchSize(dto.getDailyBatchSize());
        }
        campaign.setUpdatedAt(IndiaTime.now());
        return campaignRepository.save(campaign);
    }

    public void sendTestToOwner(String ownerEmail) {
        requireOutreachEnabled(ensureCampaign());
        String click = clickUrl("test-token-not-stored");
        String unsub = unsubscribeUrl("test-token-not-stored");
        String open = openPixelUrl("test-token-not-stored");
        String subject = OutreachInviteMailer.subjectForWave(1);
        String plain = OutreachInviteMailer.buildPlainBody(1, "there", click, unsub, trimBase(publicSiteUrl));
        String html = OutreachInviteMailer.buildHtmlBody(1, "there", click, open, unsub, trimBase(publicSiteUrl));
        MailSendResult result = inviteMailer.send(ownerEmail, subject, plain, html, unsub);
        if (!result.isDelivered()) {
            String err = result.getError() != null ? result.getError() : "SMTP send failed";
            throw new IllegalStateException(err);
        }
    }

    public record BatchRunResult(
            int submitted,
            int failed,
            int picked,
            boolean campaignPaused,
            String note) {}

    public enum BatchStartOutcome {
        STARTED,
        ALREADY_RUNNING,
        NOT_RUNNING,
        DISABLED
    }

    public record BatchStartResult(BatchStartOutcome outcome, String message) {}

    /**
     * Queue the daily batch on a background thread so the HTTP request returns before Tomcat times out.
     */
    public BatchStartResult requestDailyBatchStart() {
        OutreachCampaign campaign = ensureCampaign();
        if (!campaign.isOutreachEnabled()) {
            return new BatchStartResult(BatchStartOutcome.DISABLED, "Outreach is disabled in admin");
        }
        if (!tryMarkBatchInProgress()) {
            return new BatchStartResult(BatchStartOutcome.ALREADY_RUNNING, "A batch is already running");
        }
        if (campaign.getStatus() != OutreachCampaign.Status.RUNNING) {
            clearBatchInProgress();
            return new BatchStartResult(BatchStartOutcome.NOT_RUNNING, "Campaign is not RUNNING");
        }
        outreachBatchExecutor.execute(() -> {
            try {
                BatchRunResult result = processDailyBatch();
                log.info(
                        "Outreach async batch finished: submitted={}, failed={}, picked={}",
                        result.submitted(),
                        result.failed(),
                        result.picked());
            } catch (Exception e) {
                log.warn("Outreach async batch failed: {}", e.getMessage(), e);
            } finally {
                clearBatchInProgress();
            }
        });
        return new BatchStartResult(
                BatchStartOutcome.STARTED,
                "Batch started — refresh metrics periodically until batchInProgress is false");
    }

    public BatchRunResult processDailyBatch() {
        synchronized (batchLock) {
            return processDailyBatchUnderLock();
        }
    }

    private BatchRunResult processDailyBatchUnderLock() {
        OutreachCampaign campaign = ensureCampaign();
        if (!campaign.isOutreachEnabled()) {
            return new BatchRunResult(0, 0, 0, false, "Outreach is disabled in admin");
        }
        if (campaign.getStatus() != OutreachCampaign.Status.RUNNING) {
            return new BatchRunResult(0, 0, 0, false, "Campaign is not RUNNING");
        }
        long start = System.currentTimeMillis();
        resetSubmittedTodayIfNewDay(campaign);
        int capacity = campaign.getDailyBatchSize() - campaign.getSubmittedToday();
        if (capacity <= 0) {
            campaignRepository.save(campaign);
            return new BatchRunResult(0, 0, 0, false, "Daily cap already reached for today (IST)");
        }
        int wave = campaign.getCurrentWave();
        List<OutreachRecipient> batch = findSendableBatch(wave, capacity);
        int picked = batch.size();
        int submitted = 0;
        int failed = 0;
        int skippedDuplicate = 0;
        boolean stopBatchForRate = false;

        for (OutreachRecipient recipient : batch) {
            if (stopBatchForRate) {
                break;
            }
            OutreachRecipient claimed = tryClaimWaveSend(recipient.getId(), wave);
            if (claimed == null) {
                skippedDuplicate++;
                continue;
            }
            if (!precheckRecipient(claimed, wave)) {
                recipientRepository.save(claimed);
                continue;
            }
            SendOutcome outcome = sendOne(campaign, claimed, wave);
            if (outcome.skipped) {
                skippedDuplicate++;
            }
            if (outcome.submitted) {
                submitted++;
                campaign.setSubmittedToday(campaign.getSubmittedToday() + 1);
                if (submitted % 25 == 0) {
                    campaign.setUpdatedAt(IndiaTime.now());
                    campaignRepository.save(campaign);
                }
            }
            if (outcome.failed) {
                failed++;
            }
            if (outcome.pauseCampaign) {
                campaign.setStatus(OutreachCampaign.Status.PAUSED);
                campaign.setLastJobAt(IndiaTime.now());
                campaign.setLastJobSubmitted(submitted);
                campaign.setLastJobFailed(failed);
                campaignRepository.save(campaign);
                log.warn("Outreach campaign paused: SMTP auth/configuration failure");
                return new BatchRunResult(submitted, failed, picked, true, "Paused: SMTP auth failure");
            }
            if (outcome.stopBatchForRate) {
                stopBatchForRate = true;
            }
            recipientRepository.save(claimed);
            sleepJitter();
        }

        tryAdvanceWave(campaign);
        campaign.setLastJobAt(IndiaTime.now());
        campaign.setLastJobSubmitted(submitted);
        campaign.setLastJobFailed(failed);
        campaign.setLastJobDurationSec((System.currentTimeMillis() - start) / 1000);
        campaign.setUpdatedAt(IndiaTime.now());
        campaignRepository.save(campaign);
        bumpRollup(wave, submitted, failed, 0, 0, 0, 0, 0);
        String note = null;
        if (picked == 0) {
            note = "No pending recipients in current wave";
        } else if (skippedDuplicate > 0 && submitted == 0 && failed == 0) {
            note = "No sends — recipients in 24h cooldown or already claimed";
        }
        return new BatchRunResult(submitted, failed, picked, false, note);
    }

    public Optional<String> recordClickRedirect(String token) {
        return recipientRepository.findByTrackingToken(token).map(r -> {
            if (r.getLifecycle() == OutreachRecipient.Lifecycle.OPTED_OUT) {
                return landingUrl(token);
            }
            for (int w = 1; w <= 3; w++) {
                OutreachWaveDelivery d = r.wave(w);
                if (d.getStatus() == OutreachWaveDelivery.Status.SUBMITTED) {
                    d.setClickCount(d.getClickCount() + 1);
                    if (!d.isUniqueClick()) {
                        d.setUniqueClick(true);
                    }
                }
            }
            r.setUpdatedAt(IndiaTime.now());
            recipientRepository.save(r);
            return landingUrl(token);
        });
    }

    public void recordOpen(String token) {
        recipientRepository.findByTrackingToken(token).ifPresent(r -> {
            for (int w = 1; w <= 3; w++) {
                OutreachWaveDelivery d = r.wave(w);
                if (d.getStatus() == OutreachWaveDelivery.Status.SUBMITTED) {
                    d.setOpenCount(d.getOpenCount() + 1);
                    if (!d.isUniqueOpen()) {
                        d.setUniqueOpen(true);
                    }
                }
            }
            r.setUpdatedAt(IndiaTime.now());
            recipientRepository.save(r);
        });
    }

    public void recordLanding(String token) {
        recipientRepository.findByTrackingToken(token).ifPresent(r -> {
            r.setLandingHits(r.getLandingHits() + 1);
            r.setUpdatedAt(IndiaTime.now());
            recipientRepository.save(r);
        });
    }

    public boolean unsubscribe(String token) {
        Optional<OutreachRecipient> opt = recipientRepository.findByTrackingToken(token);
        if (opt.isEmpty()) {
            return false;
        }
        OutreachRecipient r = opt.get();
        r.setLifecycle(OutreachRecipient.Lifecycle.OPTED_OUT);
        r.setOptedOutAt(IndiaTime.now());
        r.setUpdatedAt(IndiaTime.now());
        recipientRepository.save(r);
        bumpRollup(ensureCampaign().getCurrentWave(), 0, 0, 0, 0, 0, 0, 1);
        return true;
    }

  /**
     * After freemium signup: grant bonus if token valid, not opted out, email matches.
     */
    @Transactional
    public void applySignupAttribution(User user, String outreachToken) {
        if (user == null || outreachToken == null || outreachToken.isBlank()) {
            return;
        }
        recipientRepository.findByTrackingToken(outreachToken.trim()).ifPresent(recipient -> {
            if (recipient.getLifecycle() == OutreachRecipient.Lifecycle.OPTED_OUT) {
                return;
            }
            String norm = userContactService.normalizeEmail(user.getEmail());
            if (!recipient.getEmailHash().equals(sha256(norm))) {
                return;
            }
            OutreachCampaign campaign = ensureCampaign();
            if (user.getOutreachBonusUsd() == null || user.getOutreachBonusUsd() <= 0) {
                user.setOutreachBonusUsd(campaign.getSignupBonusUsd());
                userRepository.save(user);
            }
            recipient.setLifecycle(OutreachRecipient.Lifecycle.REGISTERED);
            recipient.setRegisteredAt(IndiaTime.now());
            recipient.setSkillamaUserId(user.getId());
            recipient.setBonusGranted(true);
            recipient.setUpdatedAt(IndiaTime.now());
            recipientRepository.save(recipient);
            bumpRollup(campaign.getCurrentWave(), 0, 0, 0, 0, 1, 1, 0);
        });
    }

    private record SendOutcome(
            boolean submitted, boolean failed, boolean pauseCampaign, boolean stopBatchForRate, boolean skipped) {}

    private SendOutcome sendOne(OutreachCampaign campaign, OutreachRecipient recipient, int wave) {
        OutreachWaveDelivery d = recipient.wave(wave);
        if (d.getStatus() == OutreachWaveDelivery.Status.SUBMITTED) {
            return new SendOutcome(false, false, false, false, true);
        }
        if (d.getSubmittedAt() != null
                && d.getSubmittedAt().isAfter(IndiaTime.now().minusHours(MIN_HOURS_BETWEEN_SENDS))) {
            return new SendOutcome(false, false, false, false, true);
        }
        d.setAttemptCount(d.getAttemptCount() + 1);

        String click = clickUrl(recipient.getTrackingToken());
        String unsub = unsubscribeUrl(recipient.getTrackingToken());
        String open = openPixelUrl(recipient.getTrackingToken());
        String subject = OutreachInviteMailer.subjectForWave(wave);
        String firstName = recipient.getDisplayName();
        String plain = OutreachInviteMailer.buildPlainBody(wave, firstName, click, unsub, trimBase(publicSiteUrl));
        String html = OutreachInviteMailer.buildHtmlBody(wave, firstName, click, open, unsub, trimBase(publicSiteUrl));

        MailSendResult result = inviteMailer.send(recipient.getEmail(), subject, plain, html, unsub);
        if (result.isDelivered()) {
            d.setStatus(OutreachWaveDelivery.Status.SUBMITTED);
            d.setSubmittedAt(IndiaTime.now());
            d.setFailureReason(null);
            d.setLastError(null);
            return new SendOutcome(true, false, false, false, false);
        }
        FailureClass failure = classifyFailure(result.getError());
        d.setLastError(result.getError());
        d.setFailureReason(failure.reason);
        if (failure.reason.equals("SMTP_AUTH")) {
            return new SendOutcome(false, true, true, false, false);
        }
        if (failure.reason.equals("RATE_LIMIT")) {
            markRetryOrFail(d, failure);
            return new SendOutcome(false, true, false, true, false);
        }
        if (!failure.retryable || d.getAttemptCount() >= MAX_SMTP_ATTEMPTS) {
            d.setStatus(OutreachWaveDelivery.Status.FAILED_PERMANENT);
            if (d.getAttemptCount() >= MAX_SMTP_ATTEMPTS) {
                d.setFailureReason(FAILURE_MAX);
            }
            return new SendOutcome(false, true, false, false, false);
        }
        d.setStatus(OutreachWaveDelivery.Status.RETRY_PENDING);
        d.setNextAttemptAt(scheduleNextAttempt(d.getAttemptCount()));
        return new SendOutcome(false, true, false, false, false);
    }

    private void markRetryOrFail(OutreachWaveDelivery d, FailureClass failure) {
        if (d.getAttemptCount() >= MAX_SMTP_ATTEMPTS) {
            d.setStatus(OutreachWaveDelivery.Status.FAILED_PERMANENT);
            d.setFailureReason(FAILURE_MAX);
        } else {
            d.setStatus(OutreachWaveDelivery.Status.RETRY_PENDING);
            d.setFailureReason(failure.reason);
            d.setNextAttemptAt(IndiaTime.now().plusDays(1));
        }
    }

    private LocalDateTime scheduleNextAttempt(int attemptCount) {
        LocalDateTime scheduled = attemptCount <= 1 ? IndiaTime.now().plusDays(1) : IndiaTime.now().plusDays(2);
        LocalDateTime minGap = IndiaTime.now().plusHours(MIN_HOURS_BETWEEN_SENDS);
        return scheduled.isAfter(minGap) ? scheduled : minGap;
    }

    private record FailureClass(String reason, boolean retryable) {}

    private FailureClass classifyFailure(String error) {
        if (error == null) {
            return new FailureClass("UNKNOWN", true);
        }
        String e = error.toLowerCase(Locale.ROOT);
        if (e.contains("auth") || e.contains("535") || e.contains("credentials")) {
            return new FailureClass("SMTP_AUTH", false);
        }
        if (e.contains("invalid") || e.contains("address")) {
            return new FailureClass("INVALID_RECIPIENT", false);
        }
        if (e.contains("timeout")) {
            return new FailureClass("SMTP_TIMEOUT", true);
        }
        if (e.contains("rate") || e.contains("throttl")) {
            return new FailureClass("RATE_LIMIT", true);
        }
        if (e.contains("policy") || e.contains("rejected")) {
            return new FailureClass("POLICY_REJECT", false);
        }
        if (e.contains("connection") || e.contains("io")) {
            return new FailureClass("SMTP_IO", true);
        }
        return new FailureClass("UNKNOWN", true);
    }

    private boolean precheckRecipient(OutreachRecipient recipient, int wave) {
        if (recipient.getLifecycle() == OutreachRecipient.Lifecycle.OPTED_OUT
                || recipient.getLifecycle() == OutreachRecipient.Lifecycle.REGISTERED) {
            return false;
        }
        String norm = recipient.getEmail();
        if (userRepository.findByEmail(norm).isPresent()
                || userRepository.findByEmailIgnoreCase(norm).isPresent()) {
            recipient.setLifecycle(OutreachRecipient.Lifecycle.SUPPRESSED);
            recipient.setSuppressReason("JOINED_SKILLAMA");
            OutreachWaveDelivery d = recipient.wave(wave);
            d.setStatus(OutreachWaveDelivery.Status.SKIPPED);
            return false;
        }
        if (wave > 1) {
            OutreachWaveDelivery prior = recipient.wave(wave - 1);
            if (prior.getStatus() != OutreachWaveDelivery.Status.SUBMITTED) {
                OutreachWaveDelivery d = recipient.wave(wave);
                d.setStatus(OutreachWaveDelivery.Status.SKIPPED);
                return false;
            }
        }
        return true;
    }

    private void tryAdvanceWave(OutreachCampaign campaign) {
        int wave = campaign.getCurrentWave();
        if (countPendingForWave(wave) > 0 || countRetryPendingForWave(wave) > 0) {
            return;
        }
        if (wave >= campaign.getMaxWaves()) {
            campaign.setStatus(OutreachCampaign.Status.COMPLETED);
            campaign.setCompletedAt(IndiaTime.now());
            return;
        }
        int nextWave = wave + 1;
        long eligible = queueNextWave(nextWave);
        if (nextWave == 2) {
            campaign.setWave2EligibleSnapshot(eligible);
        } else if (nextWave == 3) {
            campaign.setWave3EligibleSnapshot(eligible);
        }
        campaign.setCurrentWave(nextWave);
        log.info("Outreach advanced to wave {} — {} eligible", nextWave, eligible);
    }

    private long queueNextWave(int nextWave) {
        Query q = new Query(Criteria.where("lifecycle").is(OutreachRecipient.Lifecycle.ACTIVE));
        String priorKey = "waves." + (nextWave - 1) + ".status";
        q.addCriteria(Criteria.where(priorKey).is(OutreachWaveDelivery.Status.SUBMITTED.name()));
        List<OutreachRecipient> list = skillamaMongoTemplate.find(q, OutreachRecipient.class);
        long count = 0;
        for (OutreachRecipient r : list) {
            OutreachWaveDelivery d = r.wave(nextWave);
            if (d.getStatus() == OutreachWaveDelivery.Status.SUBMITTED) {
                continue;
            }
            d.setStatus(OutreachWaveDelivery.Status.PENDING);
            d.setAttemptCount(0);
            d.setNextAttemptAt(null);
            r.setUpdatedAt(IndiaTime.now());
            recipientRepository.save(r);
            count++;
        }
        return count;
    }

    private List<OutreachRecipient> findSendableBatch(int wave, int limit) {
        String statusKey = "waves." + wave + ".status";
        String nextKey = "waves." + wave + ".nextAttemptAt";
        String lastAttemptKey = "waves." + wave + ".lastAttemptAt";
        LocalDateTime now = IndiaTime.now();
        LocalDateTime attemptCutoff = now.minusHours(MIN_HOURS_BETWEEN_SENDS);
        Criteria pending = Criteria.where(statusKey).is(OutreachWaveDelivery.Status.PENDING.name());
        Criteria retry = new Criteria().andOperator(
                Criteria.where(statusKey).is(OutreachWaveDelivery.Status.RETRY_PENDING.name()),
                new Criteria().orOperator(
                        Criteria.where(nextKey).is(null),
                        Criteria.where(nextKey).lte(now)));
        Criteria notYetSubmitted = Criteria.where(statusKey).ne(OutreachWaveDelivery.Status.SUBMITTED.name());
        Criteria outside24hWindow = new Criteria().orOperator(
                Criteria.where(lastAttemptKey).is(null),
                Criteria.where(lastAttemptKey).lte(attemptCutoff));
        Query q = new Query(new Criteria().andOperator(
                Criteria.where("lifecycle").is(OutreachRecipient.Lifecycle.ACTIVE),
                notYetSubmitted,
                outside24hWindow,
                new Criteria().orOperator(pending, retry)));
        q.limit(limit);
        return skillamaMongoTemplate.find(q, OutreachRecipient.class);
    }

    /**
     * Atomically marks a wave send as in-flight so two batch jobs cannot SMTP the same pending row.
     * Honours {@link #MIN_HOURS_BETWEEN_SENDS} via lastAttemptAt on the wave delivery.
     */
    private OutreachRecipient tryClaimWaveSend(String recipientId, int wave) {
        String statusKey = "waves." + wave + ".status";
        String lastAttemptKey = "waves." + wave + ".lastAttemptAt";
        String nextKey = "waves." + wave + ".nextAttemptAt";
        LocalDateTime now = IndiaTime.now();
        LocalDateTime attemptCutoff = now.minusHours(MIN_HOURS_BETWEEN_SENDS);

        Criteria pending = Criteria.where(statusKey).is(OutreachWaveDelivery.Status.PENDING.name());
        Criteria retry = new Criteria().andOperator(
                Criteria.where(statusKey).is(OutreachWaveDelivery.Status.RETRY_PENDING.name()),
                new Criteria().orOperator(
                        Criteria.where(nextKey).is(null),
                        Criteria.where(nextKey).lte(now)));
        Criteria claimable = new Criteria().andOperator(
                Criteria.where("_id").is(recipientId),
                Criteria.where(statusKey).ne(OutreachWaveDelivery.Status.SUBMITTED.name()),
                new Criteria().orOperator(
                        Criteria.where(lastAttemptKey).is(null),
                        Criteria.where(lastAttemptKey).lte(attemptCutoff)),
                new Criteria().orOperator(pending, retry));

        Update update = new Update()
                .set(lastAttemptKey, now)
                .set("updatedAt", now);
        Query query = new Query(claimable);
        return skillamaMongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                OutreachRecipient.class);
    }

    private long countPendingForWave(int wave) {
        String statusKey = "waves." + wave + ".status";
        Query q = new Query(Criteria.where("lifecycle").is(OutreachRecipient.Lifecycle.ACTIVE)
                .and(statusKey).is(OutreachWaveDelivery.Status.PENDING.name()));
        return skillamaMongoTemplate.count(q, OutreachRecipient.class);
    }

    private long countRetryPendingForWave(int wave) {
        String statusKey = "waves." + wave + ".status";
        Query q = new Query(Criteria.where("lifecycle").is(OutreachRecipient.Lifecycle.ACTIVE)
                .and(statusKey).is(OutreachWaveDelivery.Status.RETRY_PENDING.name()));
        return skillamaMongoTemplate.count(q, OutreachRecipient.class);
    }

    private long countWaveSubmitted(int wave) {
        String statusKey = "waves." + wave + ".status";
        Query q = new Query(Criteria.where(statusKey).is(OutreachWaveDelivery.Status.SUBMITTED.name()));
        return skillamaMongoTemplate.count(q, OutreachRecipient.class);
    }

    private long countFailedPermanent() {
        Query q = new Query();
        q.addCriteria(new Criteria().orOperator(
                Criteria.where("waves.1.status").is(OutreachWaveDelivery.Status.FAILED_PERMANENT.name()),
                Criteria.where("waves.2.status").is(OutreachWaveDelivery.Status.FAILED_PERMANENT.name()),
                Criteria.where("waves.3.status").is(OutreachWaveDelivery.Status.FAILED_PERMANENT.name())));
        return skillamaMongoTemplate.count(q, OutreachRecipient.class);
    }

    private long countUniqueOpens() {
        return skillamaMongoTemplate.count(
                new Query(new Criteria().orOperator(
                        Criteria.where("waves.1.uniqueOpen").is(true),
                        Criteria.where("waves.2.uniqueOpen").is(true),
                        Criteria.where("waves.3.uniqueOpen").is(true))),
                OutreachRecipient.class);
    }

    private long countUniqueClicks() {
        return skillamaMongoTemplate.count(
                new Query(new Criteria().orOperator(
                        Criteria.where("waves.1.uniqueClick").is(true),
                        Criteria.where("waves.2.uniqueClick").is(true),
                        Criteria.where("waves.3.uniqueClick").is(true))),
                OutreachRecipient.class);
    }

    private long countBonusGranted() {
        return skillamaMongoTemplate.count(new Query(Criteria.where("bonusGranted").is(true)), OutreachRecipient.class);
    }

    private long countByFailureReason(String reason) {
        Query q = new Query(new Criteria().orOperator(
                Criteria.where("waves.1.failureReason").is(reason),
                Criteria.where("waves.2.failureReason").is(reason),
                Criteria.where("waves.3.failureReason").is(reason)));
        return skillamaMongoTemplate.count(q, OutreachRecipient.class);
    }

    private Map<String, Long> failureBuckets() {
        Map<String, Long> m = new LinkedHashMap<>();
        for (String r : List.of("INVALID_RECIPIENT", "SMTP_AUTH", "SMTP_TIMEOUT", "RATE_LIMIT", "SMTP_IO", "POLICY_REJECT", "UNKNOWN", FAILURE_MAX)) {
            m.put(r, countByFailureReason(r));
        }
        return m;
    }

    /** IST calendar day counter for daily cap — read-only for admin summary (do not mutate entity). */
    private int submittedTodayForDisplay(OutreachCampaign campaign) {
        int count = Math.max(0, campaign.getSubmittedToday());
        if (count == 0) {
            return 0;
        }
        LocalDate todayIst = IndiaTime.today();
        LocalDate stored = campaign.getSubmittedCountDate();
        if (stored == null) {
            return count;
        }
        if (stored.equals(todayIst) || stored.equals(LocalDate.now())) {
            return count;
        }
        return 0;
    }

    private void requireOutreachEnabled(OutreachCampaign campaign) {
        if (!campaign.isOutreachEnabled()) {
            throw new IllegalStateException("Outreach is disabled in admin");
        }
    }

    private boolean tryMarkBatchInProgress() {
        LocalDateTime now = IndiaTime.now();
        Criteria notRunning = new Criteria().orOperator(
                Criteria.where("batchInProgress").is(null),
                Criteria.where("batchInProgress").is(false));
        Query query = new Query(new Criteria().andOperator(
                Criteria.where("_id").is(OutreachCampaign.SINGLETON_ID), notRunning));
        Update update = new Update()
                .set("batchInProgress", true)
                .set("batchStartedAt", now)
                .set("updatedAt", now);
        OutreachCampaign updated = skillamaMongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                OutreachCampaign.class);
        return updated != null;
    }

    private void clearBatchInProgress() {
        Query query = new Query(Criteria.where("_id").is(OutreachCampaign.SINGLETON_ID));
        Update update = new Update()
                .set("batchInProgress", false)
                .set("updatedAt", IndiaTime.now());
        skillamaMongoTemplate.updateFirst(query, update, OutreachCampaign.class);
    }

    private void resetSubmittedTodayIfNewDay(OutreachCampaign campaign) {
        LocalDate today = IndiaTime.today();
        if (campaign.getSubmittedCountDate() == null || !campaign.getSubmittedCountDate().equals(today)) {
            campaign.setSubmittedCountDate(today);
            campaign.setSubmittedToday(0);
        }
    }

    private void bumpRollup(int wave, int submitted, int failed, int opens, int clicks, int regs, int bonuses, int unsubs) {
        LocalDate today = IndiaTime.today();
        OutreachDailyRollup rollup = rollupRepository.findByDateAndWave(today, wave)
                .orElseGet(() -> OutreachDailyRollup.builder()
                        .id(today + "-w" + wave)
                        .date(today)
                        .wave(wave)
                        .build());
        rollup.setAttempted(rollup.getAttempted() + submitted + failed);
        rollup.setSubmitted(rollup.getSubmitted() + submitted);
        rollup.setFailed(rollup.getFailed() + failed);
        rollup.setUniqueOpens(rollup.getUniqueOpens() + opens);
        rollup.setUniqueClicks(rollup.getUniqueClicks() + clicks);
        rollup.setRegistrations(rollup.getRegistrations() + regs);
        rollup.setBonusesGranted(rollup.getBonusesGranted() + bonuses);
        rollup.setUnsubscribes(rollup.getUnsubscribes() + unsubs);
        rollupRepository.save(rollup);
    }

    private String clickUrl(String token) {
        return trimBase(apiBaseUrl) + "/skillama/public/outreach/c/" + token;
    }

    private String openPixelUrl(String token) {
        return trimBase(apiBaseUrl) + "/skillama/public/outreach/o/" + token + ".gif";
    }

    private String unsubscribeUrl(String token) {
        return trimBase(apiBaseUrl) + "/skillama/public/outreach/unsubscribe/" + token;
    }

    private String landingUrl(String token) {
        return trimBase(publicSiteUrl) + "/welcome/prwatech?ocid=" + token;
    }

    private static String trimBase(String url) {
        if (url == null) {
            return "";
        }
        String u = url.trim();
        if (u.endsWith("/")) {
            return u.substring(0, u.length() - 1);
        }
        return u;
    }

    private static String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : dig) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void sleepJitter() {
        try {
            Thread.sleep(1500 + ThreadLocalRandom.current().nextInt(1000));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}