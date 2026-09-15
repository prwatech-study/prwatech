package com.prwatech.skillama.controller;

import com.prwatech.skillama.dto.ConfirmationResponseDTO;
import com.prwatech.skillama.dto.LectureStartInstructionRequestDTO;
import com.prwatech.skillama.dto.LectureStartInstructionResponseDTO;
import com.prwatech.skillama.dto.TextToAudioRequestDTO;
import com.prwatech.skillama.dto.TranscribedAudioDTO;
import com.prwatech.skillama.dto.TutorIntroRequestDTO;
import com.prwatech.skillama.dto.TutorIntroResponseDTO;
import com.prwatech.skillama.dto.UserNameResponseDTO;
import com.prwatech.skillama.exception.AiBudgetLimitException;
import com.prwatech.skillama.exception.SkillamaAuthException;
import com.prwatech.skillama.model.Course;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.CourseRepository;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import com.prwatech.skillama.service.AiUsageService;
import com.prwatech.skillama.service.SkillamaAiClient;
import com.prwatech.skillama.service.SkillamaAuthSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Map;

/**
 * Utility ai-tutor calls (TTS/STT/onboarding). Guests stay unmetered. Logged-in learners
 * are budget-gated and charged for Amazon Transcribe / Polly — those are real AWS costs
 * on top of any later LLM call, not a second LLM charge.
 */
@RestController
@RequestMapping("/skillama/ai-utility")
@RequiredArgsConstructor
public class AiUtilityController {

    private static final String DEFAULT_COURSE_NAME = "Python";

    private final SkillamaAiClient skillamaAiClient;
    private final CourseRepository courseRepository;
    private final SkillamaAuthSupport skillamaAuthSupport;
    private final SkillamaUserRepository userRepository;
    private final AiUsageService aiUsageService;

    @PostMapping("/introduce-tutor")
    public ResponseEntity<?> introduceTutor(
            @RequestBody(required = false) TutorIntroRequestDTO request,
            HttpServletRequest httpRequest) {
        try {
            User user = optionalLearner(httpRequest);
            assertMediaBudget(user);
            String courseId = request != null ? request.getCourseId() : null;
            TutorIntroResponseDTO dto = skillamaAiClient.introduceTutor(resolveCourseName(courseId));
            aiUsageService.recordSpeechUsage(user, courseId, dto.getIntroductionText());
            return ResponseEntity.ok(dto);
        } catch (SkillamaAuthException e) {
            return unauthorized(e);
        } catch (AiBudgetLimitException e) {
            return budgetLimit(e);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(502).body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/lecture-start-instruction")
    public ResponseEntity<?> lectureStartInstruction(
            @RequestBody LectureStartInstructionRequestDTO request,
            HttpServletRequest httpRequest) {
        if (request == null || request.getTopics() == null || request.getTopics().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "topics is required"));
        }
        try {
            User user = optionalLearner(httpRequest);
            assertMediaBudget(user);
            LectureStartInstructionResponseDTO dto =
                    skillamaAiClient.lectureStartInstruction(request.getTopics());
            aiUsageService.recordSpeechUsage(user, null, dto.getLectureIntroductionText());
            return ResponseEntity.ok(dto);
        } catch (SkillamaAuthException e) {
            return unauthorized(e);
        } catch (AiBudgetLimitException e) {
            return budgetLimit(e);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(502).body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/text-to-audio")
    public ResponseEntity<?> textToAudio(
            @RequestBody TextToAudioRequestDTO request,
            HttpServletRequest httpRequest) {
        if (request == null || request.getText() == null || request.getText().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "text is required"));
        }
        try {
            User user = optionalLearner(httpRequest);
            assertMediaBudget(user);
            var dto = skillamaAiClient.textToAudio(request.getText());
            aiUsageService.recordSpeechUsage(user, null, request.getText());
            return ResponseEntity.ok(dto);
        } catch (SkillamaAuthException e) {
            return unauthorized(e);
        } catch (AiBudgetLimitException e) {
            return budgetLimit(e);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(502).body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/audio-to-text")
    public ResponseEntity<?> audioToText(
            @RequestParam("audio") MultipartFile audio,
            HttpServletRequest httpRequest) {
        try {
            User user = optionalLearner(httpRequest);
            assertMediaBudget(user);
            TranscribedAudioDTO result =
                    skillamaAiClient.transcribeAudio(audio.getBytes(), audio.getOriginalFilename());
            aiUsageService.recordTranscribeUsage(user, null, result.getAudioSeconds());
            return ResponseEntity.ok(Map.of("transcript", result.getTranscript() != null ? result.getTranscript() : ""));
        } catch (SkillamaAuthException e) {
            return unauthorized(e);
        } catch (AiBudgetLimitException e) {
            return budgetLimit(e);
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Invalid audio file"));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(502).body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/confirmation")
    public ResponseEntity<?> confirmation(
            @RequestParam("audio") MultipartFile audio,
            HttpServletRequest httpRequest) {
        try {
            User user = optionalLearner(httpRequest);
            assertMediaBudget(user);
            ConfirmationResponseDTO dto =
                    skillamaAiClient.confirmAgreement(audio.getBytes(), audio.getOriginalFilename());
            aiUsageService.recordTranscribeUsage(user, null, dto.getAudioSeconds());
            if (dto.getPollyCharacters() != null && dto.getPollyCharacters() > 0) {
                aiUsageService.recordSpeechUsage(user, null, dto.getPollyCharacters());
            }
            return ResponseEntity.ok(dto);
        } catch (SkillamaAuthException e) {
            return unauthorized(e);
        } catch (AiBudgetLimitException e) {
            return budgetLimit(e);
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Invalid audio file"));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(502).body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/get-user-name")
    public ResponseEntity<?> getUserName(
            @RequestParam("audio") MultipartFile audio,
            HttpServletRequest httpRequest) {
        try {
            User user = optionalLearner(httpRequest);
            assertMediaBudget(user);
            UserNameResponseDTO dto =
                    skillamaAiClient.extractUserName(audio.getBytes(), audio.getOriginalFilename());
            aiUsageService.recordTranscribeUsage(user, null, dto.getAudioSeconds());
            if (dto.getPollyCharacters() != null && dto.getPollyCharacters() > 0) {
                aiUsageService.recordSpeechUsage(user, null, dto.getPollyCharacters());
            } else {
                aiUsageService.recordSpeechUsage(user, null, dto.getWelcomeText());
            }
            return ResponseEntity.ok(dto);
        } catch (SkillamaAuthException e) {
            return unauthorized(e);
        } catch (AiBudgetLimitException e) {
            return budgetLimit(e);
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Invalid audio file"));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(502).body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    private User optionalLearner(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return null;
        }
        String userId = skillamaAuthSupport.resolveUserIdFromRequest(request);
        return userRepository.findById(userId).orElse(null);
    }

    private void assertMediaBudget(User user) {
        if (user != null) {
            aiUsageService.assertWithinBudget(user);
        }
    }

    private static ResponseEntity<Map<String, Object>> unauthorized(SkillamaAuthException e) {
        return ResponseEntity.status(401).body(Map.of("status", "error", "message", e.getMessage()));
    }

    private static ResponseEntity<Map<String, Object>> budgetLimit(AiBudgetLimitException e) {
        return ResponseEntity.status(429).body(Map.of(
                "status", "error",
                "message", e.getMessage(),
                "aiBudgetLimitReached", true,
                "aiCostUsedUsd", e.getAiCostUsedUsd(),
                "aiCostLimitUsd", e.getAiCostLimitUsd()));
    }

    private String resolveCourseName(String courseId) {
        if (courseId == null || courseId.isBlank()) {
            return DEFAULT_COURSE_NAME;
        }
        return courseRepository.findById(courseId).map(Course::getName).orElse(DEFAULT_COURSE_NAME);
    }
}
