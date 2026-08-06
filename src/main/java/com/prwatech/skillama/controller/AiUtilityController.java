package com.prwatech.skillama.controller;

import com.prwatech.skillama.dto.LectureStartInstructionRequestDTO;
import com.prwatech.skillama.dto.TextToAudioRequestDTO;
import com.prwatech.skillama.dto.TutorIntroRequestDTO;
import com.prwatech.skillama.model.Course;
import com.prwatech.skillama.repository.CourseRepository;
import com.prwatech.skillama.service.SkillamaAiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * Zero-cost ai-tutor utility calls — proxied here purely to close the browser-direct-to-
 * ai-tutor bypass (Phase 1 internal-key lockdown), not budget-gated since none of these are
 * separately-billable learner actions: the first three are templated TTS (no LLM at all),
 * and the multipart three (audio_to_text/confirmation/get_user_name) support an
 * already-billed subsequent action (the question they lead into) or are one-time onboarding
 * steps — metering them too would double-charge a single user action. No login check:
 * introduce-tutor and the onboarding trio are also used by anonymous demo-mode visitors
 * (see /lms/introduction), and none of these expose user-specific data. See
 * SkillamaAiClient for the ai-tutor contracts.
 */
@RestController
@RequestMapping("/skillama/ai-utility")
@RequiredArgsConstructor
public class AiUtilityController {

    private static final String DEFAULT_COURSE_NAME = "Python";

    private final SkillamaAiClient skillamaAiClient;
    private final CourseRepository courseRepository;

    @PostMapping("/introduce-tutor")
    public ResponseEntity<?> introduceTutor(@RequestBody(required = false) TutorIntroRequestDTO request) {
        try {
            String courseId = request != null ? request.getCourseId() : null;
            return ResponseEntity.ok(skillamaAiClient.introduceTutor(resolveCourseName(courseId)));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(502).body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/lecture-start-instruction")
    public ResponseEntity<?> lectureStartInstruction(@RequestBody LectureStartInstructionRequestDTO request) {
        if (request == null || request.getTopics() == null || request.getTopics().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "topics is required"));
        }
        try {
            return ResponseEntity.ok(skillamaAiClient.lectureStartInstruction(request.getTopics()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(502).body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/text-to-audio")
    public ResponseEntity<?> textToAudio(@RequestBody TextToAudioRequestDTO request) {
        if (request == null || request.getText() == null || request.getText().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "text is required"));
        }
        try {
            return ResponseEntity.ok(skillamaAiClient.textToAudio(request.getText()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(502).body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/audio-to-text")
    public ResponseEntity<?> audioToText(@RequestParam("audio") MultipartFile audio) {
        try {
            String transcript = skillamaAiClient.transcribeAudio(audio.getBytes(), audio.getOriginalFilename());
            return ResponseEntity.ok(Map.of("transcript", transcript));
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Invalid audio file"));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(502).body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/confirmation")
    public ResponseEntity<?> confirmation(@RequestParam("audio") MultipartFile audio) {
        try {
            return ResponseEntity.ok(skillamaAiClient.confirmAgreement(audio.getBytes(), audio.getOriginalFilename()));
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Invalid audio file"));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(502).body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/get-user-name")
    public ResponseEntity<?> getUserName(@RequestParam("audio") MultipartFile audio) {
        try {
            return ResponseEntity.ok(skillamaAiClient.extractUserName(audio.getBytes(), audio.getOriginalFilename()));
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Invalid audio file"));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(502).body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    private String resolveCourseName(String courseId) {
        if (courseId == null || courseId.isBlank()) {
            return DEFAULT_COURSE_NAME;
        }
        return courseRepository.findById(courseId).map(Course::getName).orElse(DEFAULT_COURSE_NAME);
    }
}
