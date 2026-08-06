package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.GeneratedPracticalCodeDTO;
import com.prwatech.skillama.dto.PracticalExecutionResponseDTO;
import com.prwatech.skillama.model.PracticalExecutionLog;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.PracticalExecutionLogRepository;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import com.prwatech.skillama.util.IndiaTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Orchestrates the practical-exercise flow: resolve the dataset (ownership-checked), ask
 * ai-tutor to generate code against it, run that code in the sandbox, and log the attempt.
 * Neither ai-tutor nor the sandbox ever see the S3 storage key — only
 * {@link PracticalDatasetService} resolves it, and only {@link PracticalSandboxService}'s
 * invoke payload carries it, never an HTTP response.
 */
@Service
@RequiredArgsConstructor
public class PracticalExecutionService {

    private final PracticalDatasetService datasetService;
    private final SkillamaAiClient skillamaAiClient;
    private final PracticalSandboxService sandboxService;
    private final PracticalExecutionLogRepository logRepository;
    private final SkillamaUserRepository userRepository;

    public PracticalExecutionResponseDTO execute(String userId, String datasetId, String task) {
        PracticalDatasetService.ExecutionContext ctx = datasetService.resolveForExecution(userId, datasetId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        LocalDateTime startedAt = IndiaTime.now();
        GeneratedPracticalCodeDTO generated = skillamaAiClient.generatePracticalCode(
                user, ctx.courseId(), datasetId, ctx.displayName(), task);

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
}
