package com.prwatech.skillama.service;

import com.prwatech.common.exception.NotFoundException;
import com.prwatech.skillama.dto.AdminCodeAssistInteractionDTO;
import com.prwatech.skillama.dto.CodeAssistRequestDTO;
import com.prwatech.skillama.dto.CodeAssistResponseDTO;
import com.prwatech.skillama.dto.GeneratedCodeAssistDTO;
import com.prwatech.skillama.dto.ProxiedAudioDTO;
import com.prwatech.skillama.model.Course;
import com.prwatech.skillama.model.CodeAssistFeature;
import com.prwatech.skillama.model.CodeAssistInteraction;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.CodeAssistInteractionRepository;
import com.prwatech.skillama.repository.CourseRepository;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import com.prwatech.skillama.util.IndiaTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Debug / Code Execution — server-mediated so the backend can persist what learners
 * submit and what the AI returns (previously this call went straight from the browser
 * to ai-tutor, so there was zero content-level admin visibility into these two features,
 * unlike Chat/AI Mentor/AI Exam which already route through the backend).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CodeAssistService {

    private final CodeAssistInteractionRepository interactionRepository;
    private final CourseRepository courseRepository;
    private final SkillamaUserRepository userRepository;
    private final SkillamaAiClient skillamaAiClient;
    private final PracticalSandboxService practicalSandboxService;
    private final PracticalDatasetService practicalDatasetService;

    public CodeAssistResponseDTO runDebug(String userId, CodeAssistRequestDTO request) {
        return run(CodeAssistFeature.DEBUG, userId, request);
    }

    public CodeAssistResponseDTO runCodeExecution(String userId, CodeAssistRequestDTO request) {
        return run(CodeAssistFeature.CODE_EXECUTION, userId, request);
    }

    private CodeAssistResponseDTO run(CodeAssistFeature feature, String userId, CodeAssistRequestDTO request) {
        if (request == null || request.getCode() == null || request.getCode().isBlank()) {
            throw new IllegalArgumentException("code is required");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        String courseName = request.getCourseId() != null
                ? courseRepository.findById(request.getCourseId()).map(Course::getName).orElse("Python")
                : "Python";

        String endpoint = feature == CodeAssistFeature.DEBUG ? "debug_assist" : "code_execution_assist";

        // Python courses: actually run the code in the sandbox and hand ai-tutor the real
        // result instead of letting it hallucinate one. Every other language is untouched —
        // there's no sandbox for them yet, so they keep today's AI-simulated behavior exactly
        // as before.
        String realOutput = null;
        String realError = null;
        String datasetFilename = null;
        List<String> datasetColumns = List.of();
        // True whenever we got a real verdict back from the sandbox — success, a rejected
        // (blocked) run, or a genuine execution error all count, since all three are real
        // signals rather than an ai-tutor guess. Only false for non-Python courses and for
        // the exception-fallback case below, where nothing real was ever obtained.
        boolean sandboxVerified = false;
        if (isPythonCourse(courseName)) {
            try {
                SandboxRunResult run = resolveSandboxResult(userId, request);
                sandboxVerified = true;
                PracticalSandboxService.SandboxResult sandboxResult = run.result();
                datasetFilename = run.datasetFilename();
                datasetColumns = run.datasetColumns();
                if (sandboxResult.isOk()) {
                    realOutput = sandboxResult.getStdout() != null ? sandboxResult.getStdout() : "";
                } else if ("rejected".equals(sandboxResult.getStatus())) {
                    // "rejected" = the sandbox *policy* refuses this code (os/open()/input()/...),
                    // not that the code is wrong. Some lectures legitimately teach exactly those
                    // constructs (the os module, file handling), so:
                    //  - CODE_EXECUTION (AI-generated lecture code the learner cannot edit):
                    //    degrade to the AI-simulated path — the learner sees plausible output
                    //    with the "AI-simulated" badge instead of a policy error they can't act on.
                    //  - DEBUG (learner-authored code): keep the real rejection, but phrase it as
                    //    tutor guidance rather than raw policy violations.
                    if (feature == CodeAssistFeature.CODE_EXECUTION) {
                        log.info("Sandbox rejected generated lecture code ({}), using AI-simulated output",
                                String.join("; ", sandboxResult.getViolations()));
                        sandboxVerified = false;
                    } else {
                        realError = friendlySandboxRejection(sandboxResult.getViolations());
                    }
                } else {
                    realError = sandboxResult.getError() != null ? sandboxResult.getError() : "Execution failed";
                }
            } catch (Exception e) {
                // Sandbox unavailable — degrade to the existing AI-simulated behavior rather
                // than failing a widely-used feature over an infra hiccup. ERROR, not WARN: this
                // silently degrades to hallucinated output with no other visible symptom, so it
                // must not be easy to miss in logs — an AccessDenied here once went unnoticed
                // through several rounds of testing because this was logged at WARN.
                log.error("Real sandbox execution unavailable ({}), falling back to AI-simulated output",
                        e.getClass().getSimpleName(), e);
            }
        }

        GeneratedCodeAssistDTO generated = skillamaAiClient.runCodeAssist(
                user, endpoint, request.getCourseId(), request.getCode(), courseName,
                realOutput, realError, datasetFilename, datasetColumns);

        CodeAssistInteraction interaction = CodeAssistInteraction.builder()
                .userId(userId)
                .courseId(request.getCourseId())
                .feature(feature)
                .code(request.getCode())
                .codeOutput(generated.getCodeOutput())
                .correctedCode(generated.getCorrectedCode())
                .responseText(generated.getResponseText())
                .audioUrl(generated.getAudioUrl())
                .modelId(generated.getModelId())
                .inputTokens(generated.getInputTokens())
                .outputTokens(generated.getOutputTokens())
                .totalTokens(generated.getTotalTokens())
                .sandboxVerified(sandboxVerified)
                .createdAt(IndiaTime.now())
                .build();
        interaction = interactionRepository.save(interaction);

        return CodeAssistResponseDTO.builder()
                .interactionId(interaction.getId())
                .codeOutput(generated.getCodeOutput())
                .correctedCode(generated.getCorrectedCode())
                .responseText(generated.getResponseText())
                .hasAudio(generated.getAudioUrl() != null && !generated.getAudioUrl().isBlank())
                .subtitlePath(generated.getSubtitlePath())
                .sandboxVerified(sandboxVerified)
                .build();
    }

    /** Same free-text course-name heuristic ai-tutor's own prompt already relies on ({@code "You
     * are a {course} interpreter"}) — there is no structured language field on Course, only this
     * display name. */
    private boolean isPythonCourse(String courseName) {
        return courseName != null && courseName.toLowerCase().contains("python");
    }

    /**
     * Learner-facing wording for a sandbox policy rejection of Debug-tab code. The raw
     * violation list ("import 'os' is not allowed") reads like a firewall log; this frames it
     * as what the practice sandbox supports and what to try instead. ai-tutor prefixes the
     * final display with "Error: ", so this must read naturally after that prefix.
     */
    private String friendlySandboxRejection(List<String> violations) {
        return "The practice sandbox couldn't run this code — "
                + String.join("; ", violations)
                + ". The sandbox supports pandas, numpy, matplotlib, math, statistics, "
                + "collections and datetime, and blocks system access (like the os module), "
                + "file I/O and input(). Try a sandbox-friendly alternative, e.g. hardcode "
                + "sample values instead of input() or system lookups.";
    }

    /**
     * When the frontend sends a datasetId (the learner is on a lesson with a practical dataset
     * attached), resolves it — ownership-checked, same as the Practical Exercise tab — and runs
     * against it, so code that does e.g. {@code pd.read_csv('sales.csv')} directly actually
     * works instead of always hitting FileNotFoundError. Falls back to plain ad-hoc execution
     * (still real, just without file access) if the dataset can't be resolved for any reason —
     * a bad/stale datasetId shouldn't break Debug/Execute entirely, only lose the file access.
     */
    private SandboxRunResult resolveSandboxResult(String userId, CodeAssistRequestDTO request) {
        String datasetId = request.getDatasetId();
        if (datasetId != null && !datasetId.isBlank()) {
            try {
                PracticalDatasetService.ExecutionContext ctx =
                        practicalDatasetService.resolveForExecution(userId, datasetId);
                PracticalSandboxService.SandboxResult result = practicalSandboxService.executeWithDataset(
                        datasetId, ctx.storageKey(), ctx.displayName(), request.getCode());
                List<String> columns = practicalDatasetService.resolveColumnHint(ctx.storageKey());
                return new SandboxRunResult(result, ctx.displayName(), columns);
            } catch (Exception e) {
                log.warn("Could not resolve datasetId={} for code-assist, falling back to ad-hoc execution",
                        datasetId, e);
            }
        }
        return new SandboxRunResult(practicalSandboxService.executeAdHoc(request.getCode()), null, List.of());
    }

    /**
     * @param datasetFilename the real filename the sandbox wrote the dataset under, when one was attached.
     * @param datasetColumns  the dataset's real header row, when one was attached — lets ai-tutor correct
     *                        wrong/guessed column names the same way it corrects a wrong filename.
     */
    private record SandboxRunResult(
            PracticalSandboxService.SandboxResult result, String datasetFilename, List<String> datasetColumns) {}

    /**
     * Proxies an interaction's spoken-explanation audio — the raw ai-tutor URL never
     * reaches the client (see SkillamaAiClient#fetchAudioBytes), only the authenticated,
     * ownership-checked audio bytes.
     */
    public ProxiedAudioDTO getInteractionAudio(String userId, String interactionId) {
        CodeAssistInteraction interaction = interactionRepository.findById(interactionId)
                .orElseThrow(() -> new NotFoundException("Interaction not found"));
        if (userId == null || !userId.equals(interaction.getUserId())) {
            throw new NotFoundException("Interaction not found");
        }
        if (interaction.getAudioUrl() == null || interaction.getAudioUrl().isBlank()) {
            throw new NotFoundException("No audio available for this interaction");
        }
        return skillamaAiClient.fetchAudioBytes(interaction.getAudioUrl());
    }

    /** Admin monitor: paginated Debug/Code Execution interactions across all learners. */
    public Page<AdminCodeAssistInteractionDTO> listAdminInteractions(
            int page, int size, String userId, String courseId, String email, CodeAssistFeature feature) {
        int limit = Math.min(Math.max(size, 1), 100);
        int pageNum = Math.max(page, 0);

        String emailFilter = email != null ? email.trim().toLowerCase() : null;
        Set<String> allowedUserIds = null;
        if (emailFilter != null && !emailFilter.isBlank()) {
            allowedUserIds = userRepository.findAll().stream()
                    .filter(u -> u.getEmail() != null && u.getEmail().toLowerCase().contains(emailFilter))
                    .map(User::getId)
                    .collect(Collectors.toSet());
            if (allowedUserIds.isEmpty()) {
                return new PageImpl<>(List.of(), PageRequest.of(pageNum, limit), 0);
            }
        }

        Map<String, User> userCache = new HashMap<>();
        Map<String, String> courseNameCache = new HashMap<>();
        Set<String> allowedUserIdsFinal = allowedUserIds;

        List<AdminCodeAssistInteractionDTO> rows = interactionRepository.findAll().stream()
                .filter(i -> userId == null || userId.isBlank() || userId.equals(i.getUserId()))
                .filter(i -> courseId == null || courseId.isBlank() || courseId.equals(i.getCourseId()))
                .filter(i -> feature == null || feature == i.getFeature())
                .filter(i -> allowedUserIdsFinal == null
                        || (i.getUserId() != null && allowedUserIdsFinal.contains(i.getUserId())))
                .map(i -> {
                    User user = i.getUserId() != null
                            ? userCache.computeIfAbsent(i.getUserId(), id -> userRepository.findById(id).orElse(null))
                            : null;
                    String courseName = i.getCourseId() != null
                            ? courseNameCache.computeIfAbsent(i.getCourseId(),
                                    cid -> courseRepository.findById(cid).map(Course::getName).orElse(null))
                            : null;
                    return AdminCodeAssistInteractionDTO.builder()
                            .id(i.getId())
                            .userId(i.getUserId())
                            .userName(user != null ? user.getName() : null)
                            .userEmail(user != null ? user.getEmail() : null)
                            .courseId(i.getCourseId())
                            .courseName(courseName)
                            .feature(i.getFeature())
                            .code(i.getCode())
                            .codeOutput(i.getCodeOutput())
                            .correctedCode(i.getCorrectedCode())
                            .responseText(i.getResponseText())
                            .hasAudio(i.getAudioUrl() != null && !i.getAudioUrl().isBlank())
                            .sandboxVerified(i.getSandboxVerified())
                            .createdAt(i.getCreatedAt())
                            .build();
                })
                .collect(Collectors.toList());

        rows.sort(Comparator.comparing(
                AdminCodeAssistInteractionDTO::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())));

        int total = rows.size();
        int from = pageNum * limit;
        if (from >= total) {
            return new PageImpl<>(List.of(), PageRequest.of(pageNum, limit), total);
        }
        int to = Math.min(from + limit, total);
        return new PageImpl<>(rows.subList(from, to), PageRequest.of(pageNum, limit), total);
    }
}
