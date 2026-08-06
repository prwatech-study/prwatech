package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.GeneratedPracticalCodeDTO;
import com.prwatech.skillama.dto.PracticalExecutionResponseDTO;
import com.prwatech.skillama.model.PracticalExecutionLog;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.PracticalExecutionLogRepository;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import com.prwatech.skillama.util.IndiaTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Orchestrates the practical-exercise flow: resolve the dataset (ownership-checked), ask
 * ai-tutor to generate code against it, run that code in the sandbox, and log the attempt.
 * Neither ai-tutor nor the sandbox ever see the S3 storage key — only
 * {@link PracticalDatasetService} resolves it, and only {@link PracticalSandboxService}'s
 * invoke payload carries it, never an HTTP response.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PracticalExecutionService {

    private final PracticalDatasetService datasetService;
    private final SkillamaAiClient skillamaAiClient;
    private final PracticalSandboxService sandboxService;
    private final PracticalExecutionLogRepository logRepository;
    private final SkillamaUserRepository userRepository;
    private final FileStorageService fileStorageService;

    public PracticalExecutionResponseDTO execute(String userId, String datasetId, String task) {
        PracticalDatasetService.ExecutionContext ctx = datasetService.resolveForExecution(userId, datasetId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        List<String> columns = resolveColumns(ctx.storageKey());

        LocalDateTime startedAt = IndiaTime.now();
        GeneratedPracticalCodeDTO generated = skillamaAiClient.generatePracticalCode(
                user, ctx.courseId(), datasetId, ctx.displayName(), columns, task);

        PracticalSandboxService.SandboxResult sandboxResult =
                sandboxService.executeWithDataset(datasetId, ctx.storageKey(), generated.getCode());

        LocalDateTime finishedAt = IndiaTime.now();
        logRepository.save(PracticalExecutionLog.builder()
                .userId(userId)
                .courseId(ctx.courseId())
                .datasetId(datasetId)
                .startedAt(startedAt)
                .finishedAt(finishedAt)
                .generatedCode(generated.getCode())
                .status(sandboxResult.getStatus())
                .errorDetail(sandboxResult.getError())
                .build());

        return PracticalExecutionResponseDTO.builder()
                .status(sandboxResult.getStatus())
                .stdout(sandboxResult.getStdout())
                .result(sandboxResult.getResult())
                .figures(sandboxResult.getFigures())
                .violations(sandboxResult.getViolations())
                .error(sandboxResult.getError())
                .build();
    }

    /**
     * Best-effort column-name hint for the AI — just the header row, never the data itself.
     * Without this, the AI has to guess column names from the task's wording alone (e.g. a task
     * mentioning "revenue" when the real column is unit_price/units_sold), which produces
     * confidently-wrong code. If this fails for any reason, code generation still proceeds; the
     * AI just falls back to guessing, same as before this existed.
     */
    private List<String> resolveColumns(String storageKey) {
        try {
            byte[] bytes = fileStorageService.downloadCsvDataset(storageKey);
            String text = new String(bytes, StandardCharsets.UTF_8);
            int newlineIdx = text.indexOf('\n');
            String headerLine = (newlineIdx >= 0 ? text.substring(0, newlineIdx) : text).strip();
            return Arrays.stream(headerLine.split(","))
                    .map(String::strip)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Could not read dataset header for column hints; AI will guess column names", e);
            return List.of();
        }
    }
}
