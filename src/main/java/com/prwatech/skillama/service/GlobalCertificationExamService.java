package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.GlobalCertificationExamDTO;
import com.prwatech.skillama.dto.GlobalCertificationExamRequestDTO;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.CertificationBankBuildStatus;
import com.prwatech.skillama.model.CertificationExamMeta;
import com.prwatech.skillama.model.CertificationTier;
import com.prwatech.skillama.model.GlobalCertificationExam;
import com.prwatech.skillama.repository.CertificationBankQuestionRepository;
import com.prwatech.skillama.repository.GlobalCertificationExamRepository;
import com.prwatech.skillama.util.IndiaTime;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GlobalCertificationExamService {

    public static final String DUPLICATE_MESSAGE = "This certification exam is already configured.";
    public static final Duration SNAPSHOT_MAX_AGE = Duration.ofDays(30);

    private final GlobalCertificationExamRepository repository;
    private final CertificationBankQuestionRepository bankQuestionRepository;
    private final CertificationGuidelinesFetcher guidelinesFetcher;

    // Bank multiplier mirrored for DTO; source of truth is CertificationQuestionBankService.BANK_MULTIPLIER
    private static final int BANK_MULTIPLIER = CertificationQuestionBankService.BANK_MULTIPLIER;

    public List<GlobalCertificationExamDTO> listAll(boolean activeOnly) {
        List<GlobalCertificationExam> rows = activeOnly
                ? repository.findByActiveTrue()
                : repository.findAll();
        return rows.stream()
                .map(this::toDto)
                .sorted(Comparator
                        .comparing(GlobalCertificationExamDTO::getProvider, Comparator.nullsLast(String::compareToIgnoreCase))
                        .thenComparing(dto -> dto.getTier() == null ? "" : dto.getTier().name())
                        .thenComparing(dto -> dto.getName() == null ? "" : dto.getName()))
                .collect(Collectors.toList());
    }

    public GlobalCertificationExamDTO getById(String id) {
        return toDto(require(id));
    }

    public GlobalCertificationExam require(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Certification exam not found"));
    }

    public GlobalCertificationExamDTO create(GlobalCertificationExamRequestDTO request, String actorId) {
        ValidatedFields fields = validateRequest(request, true);
        assertUnique(fields.provider(), fields.nameKey(), null);

        CertificationGuidelinesFetcher.FetchResult fetched = guidelinesFetcher.fetch(fields.guidelinesUrl());

        GlobalCertificationExam row = GlobalCertificationExam.builder()
                .provider(fields.provider())
                .tier(fields.tier())
                .name(fields.name())
                .nameKey(fields.nameKey())
                .guidelinesUrl(fields.guidelinesUrl())
                .description(fields.description())
                .active(fields.active())
                .guidelinesSnapshot(fetched.snapshot())
                .parsedMeta(fetched.meta())
                .createdAt(IndiaTime.now())
                .createdBy(actorId)
                .updatedAt(IndiaTime.now())
                .updatedBy(actorId)
                .build();
        return toDto(saveUnique(row));
    }

    public GlobalCertificationExamDTO update(String id, GlobalCertificationExamRequestDTO request, String actorId) {
        GlobalCertificationExam row = require(id);
        ValidatedFields fields = validateRequest(request, false);
        String provider = StringUtils.hasText(fields.provider()) ? fields.provider() : row.getProvider();
        String name = StringUtils.hasText(fields.name()) ? fields.name() : row.getName();
        String nameKey = nameKey(name);
        assertUnique(provider, nameKey, row.getId());

        boolean urlChanged = StringUtils.hasText(fields.guidelinesUrl())
                && !fields.guidelinesUrl().equals(row.getGuidelinesUrl());

        row.setProvider(provider);
        row.setTier(fields.tier() != null ? fields.tier() : row.getTier());
        row.setName(name);
        row.setNameKey(nameKey);
        if (StringUtils.hasText(fields.guidelinesUrl())) {
            row.setGuidelinesUrl(fields.guidelinesUrl());
        }
        if (fields.description() != null) {
            row.setDescription(trimToNull(fields.description()));
        }
        if (fields.active() != null) {
            row.setActive(fields.active());
        }
        row.setUpdatedAt(IndiaTime.now());
        row.setUpdatedBy(actorId);

        if (urlChanged) {
            applyFetch(row, guidelinesFetcher.fetch(row.getGuidelinesUrl()));
        }
        return toDto(saveUnique(row));
    }

    public GlobalCertificationExamDTO refreshGuidelines(String id, String actorId) {
        GlobalCertificationExam row = require(id);
        if (!StringUtils.hasText(row.getGuidelinesUrl())) {
            throw new IllegalArgumentException("guidelinesUrl is missing for this certification");
        }
        applyFetch(row, guidelinesFetcher.fetch(row.getGuidelinesUrl()));
        row.setUpdatedAt(IndiaTime.now());
        row.setUpdatedBy(actorId);
        return toDto(repository.save(row));
    }

    public void delete(String id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Certification exam not found");
        }
        // Drop the question bank with the catalog row so orphans don't linger.
        bankQuestionRepository.deleteByCertificationExamId(id);
        repository.deleteById(id);
    }

    /**
     * Ensures a usable guidelines snapshot exists (refresh if missing or stale).
     * Called before AI exam generation.
     */
    public GlobalCertificationExam ensureFreshGuidelines(GlobalCertificationExam row) {
        boolean needsFetch = !StringUtils.hasText(row.getGuidelinesSnapshot())
                || CertificationGuidelinesFetcher.isStale(row.getParsedMeta(), SNAPSHOT_MAX_AGE);
        if (!needsFetch) {
            return row;
        }
        if (!StringUtils.hasText(row.getGuidelinesUrl())) {
            throw new IllegalStateException("Certification guidelines URL is missing");
        }
        applyFetch(row, guidelinesFetcher.fetch(row.getGuidelinesUrl()));
        row.setUpdatedAt(IndiaTime.now());
        return repository.save(row);
    }

    public int targetQuestionCount(CertificationExamMeta meta) {
        int min = meta != null && meta.getQuestionCountMin() != null ? meta.getQuestionCountMin() : 50;
        int max = meta != null && meta.getQuestionCountMax() != null ? meta.getQuestionCountMax() : 60;
        if (max < min) {
            int tmp = min;
            min = max;
            max = tmp;
        }
        return Math.max(10, (min + max) / 2);
    }

    public int timeLimitSeconds(CertificationExamMeta meta) {
        int minutes = meta != null && meta.getDurationMinutes() != null ? meta.getDurationMinutes() : 90;
        return Math.max(15, minutes) * 60;
    }

    private void applyFetch(GlobalCertificationExam row, CertificationGuidelinesFetcher.FetchResult fetched) {
        row.setGuidelinesSnapshot(fetched.snapshot());
        row.setParsedMeta(fetched.meta());
    }

    private ValidatedFields validateRequest(GlobalCertificationExamRequestDTO request, boolean creating) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        String provider = trimToNull(request.getProvider());
        String name = trimToNull(request.getName());
        String url = trimToNull(request.getGuidelinesUrl());
        CertificationTier tier = request.getTier();
        String description = request.getDescription();
        Boolean active = request.getActive();

        if (creating) {
            if (!StringUtils.hasText(provider)) {
                throw new IllegalArgumentException("provider is required");
            }
            if (tier == null) {
                throw new IllegalArgumentException("tier is required");
            }
            if (!StringUtils.hasText(name)) {
                throw new IllegalArgumentException("name is required");
            }
            if (!StringUtils.hasText(url)) {
                throw new IllegalArgumentException("guidelinesUrl is required");
            }
        }
        if (StringUtils.hasText(provider)) {
            provider = provider.trim().toUpperCase(Locale.ROOT);
        }
        return new ValidatedFields(
                provider,
                tier,
                name,
                name != null ? nameKey(name) : null,
                url,
                description,
                active != null ? active : (creating ? Boolean.TRUE : null));
    }

    private void assertUnique(String provider, String nameKey, String excludeId) {
        if (!StringUtils.hasText(provider) || !StringUtils.hasText(nameKey)) {
            return;
        }
        repository.findByProviderIgnoreCaseAndNameKey(provider, nameKey).ifPresent(existing -> {
            if (excludeId == null || !excludeId.equals(existing.getId())) {
                throw new IllegalStateException(DUPLICATE_MESSAGE);
            }
        });
    }

    private GlobalCertificationExam saveUnique(GlobalCertificationExam row) {
        try {
            return repository.save(row);
        } catch (DuplicateKeyException e) {
            throw new IllegalStateException(DUPLICATE_MESSAGE, e);
        }
    }

    private GlobalCertificationExamDTO toDto(GlobalCertificationExam row) {
        CertificationBankBuildStatus bankStatus = row.getBankStatus() != null
                ? row.getBankStatus()
                : CertificationBankBuildStatus.IDLE;
        int examQ = targetQuestionCount(row.getParsedMeta());
        int target = row.getBankTargetSize() != null ? row.getBankTargetSize() : examQ * BANK_MULTIPLIER;
        return GlobalCertificationExamDTO.builder()
                .id(row.getId())
                .provider(row.getProvider())
                .tier(row.getTier())
                .name(row.getName())
                .guidelinesUrl(row.getGuidelinesUrl())
                .description(row.getDescription())
                .active(row.isActive())
                .guidelinesReady(StringUtils.hasText(row.getGuidelinesSnapshot()))
                .parsedMeta(row.getParsedMeta())
                .bankStatus(bankStatus)
                .bankVersion(row.getBankVersion())
                .bankTargetSize(target)
                .bankQuestionCount(row.getBankQuestionCount() != null ? row.getBankQuestionCount() : 0)
                .bankMultiplier(BANK_MULTIPLIER)
                .rebuildAllowed(bankStatus != CertificationBankBuildStatus.RUNNING)
                .bankReady(isBankReady(row, examQ))
                .bankBuildStartedAt(row.getBankBuildStartedAt())
                .bankBuildFinishedAt(row.getBankBuildFinishedAt())
                .bankBuildTriggeredBy(row.getBankBuildTriggeredBy())
                .bankBuildError(row.getBankBuildError())
                .bankLifetimeCostUsd(row.getBankLifetimeCostUsd())
                .bankLastRebuildCostUsd(row.getBankLastRebuildCostUsd())
                .createdAt(row.getCreatedAt())
                .createdBy(row.getCreatedBy())
                .updatedAt(row.getUpdatedAt())
                .updatedBy(row.getUpdatedBy())
                .build();
    }

    /** Same readiness bar learners use when assembling a paper. */
    public static boolean isBankReady(GlobalCertificationExam row, int examQuestionCount) {
        if (row == null) {
            return false;
        }
        int version = row.getBankVersion() != null ? row.getBankVersion() : 0;
        int available = row.getBankQuestionCount() != null ? row.getBankQuestionCount() : 0;
        int need = Math.max(1, examQuestionCount);
        return version > 0 && available >= Math.min(need, 20);
    }

    public static String nameKey(String name) {
        return name.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    private record ValidatedFields(
            String provider,
            CertificationTier tier,
            String name,
            String nameKey,
            String guidelinesUrl,
            String description,
            Boolean active) {}
}
