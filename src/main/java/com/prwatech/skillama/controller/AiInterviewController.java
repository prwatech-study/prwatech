package com.prwatech.skillama.controller;

import com.prwatech.skillama.service.AiInterviewService;
import com.prwatech.skillama.service.PlatformFeatureRolloutService;
import com.prwatech.skillama.service.SkillamaAuthSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/** Org AI Interview. Invite and session routes are guest-accessible via an opaque token. */
@RestController
@RequestMapping("/skillama/ai-interview")
@RequiredArgsConstructor
public class AiInterviewController {

    public static final String SESSION_HEADER = "X-Interview-Session";

    private final AiInterviewService aiInterviewService;
    private final SkillamaAuthSupport skillamaAuthSupport;
    private final PlatformFeatureRolloutService platformFeatureRolloutService;

    @GetMapping("/questions")
    public ResponseEntity<?> listQuestions(
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String q,
            HttpServletRequest request) {
        String userId = requireLearnerFeature(request);
        if (userId == null) {
            return unauthorized();
        }
        return ResponseEntity.ok(aiInterviewService.listQuestions(userId, tag, q));
    }

    @PostMapping("/questions")
    public ResponseEntity<?> createQuestion(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String userId = requireLearnerFeature(request);
        if (userId == null) {
            return unauthorized();
        }
        return ResponseEntity.ok(aiInterviewService.createQuestion(userId, body));
    }

    @PutMapping("/questions/{questionId}")
    public ResponseEntity<?> updateQuestion(
            @PathVariable String questionId,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        String userId = requireLearnerFeature(request);
        if (userId == null) {
            return unauthorized();
        }
        return ResponseEntity.ok(aiInterviewService.updateQuestion(userId, questionId, body));
    }

    @DeleteMapping("/questions/{questionId}")
    public ResponseEntity<?> deleteQuestion(@PathVariable String questionId, HttpServletRequest request) {
        String userId = requireLearnerFeature(request);
        if (userId == null) {
            return unauthorized();
        }
        return ResponseEntity.ok(aiInterviewService.deleteQuestion(userId, questionId));
    }

    @GetMapping("/schedules")
    public ResponseEntity<?> listSchedules(
            @RequestParam(required = false) String status, HttpServletRequest request) {
        String userId = requireLearnerFeature(request);
        if (userId == null) {
            return unauthorized();
        }
        return ResponseEntity.ok(aiInterviewService.listSchedules(userId, status));
    }

    @PostMapping("/schedules")
    public ResponseEntity<?> createSchedule(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String userId = requireLearnerFeature(request);
        if (userId == null) {
            return unauthorized();
        }
        return ResponseEntity.ok(aiInterviewService.createSchedule(userId, body));
    }

    @PostMapping("/schedules/{scheduleId}/reschedule")
    public ResponseEntity<?> reschedule(
            @PathVariable String scheduleId,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        String userId = requireLearnerFeature(request);
        if (userId == null) {
            return unauthorized();
        }
        return ResponseEntity.ok(aiInterviewService.reschedule(userId, scheduleId, body));
    }

    @GetMapping("/schedules/{scheduleId}/admin-detail")
    public ResponseEntity<?> adminDetail(@PathVariable String scheduleId, HttpServletRequest request) {
        String userId = requireLearnerFeature(request);
        if (userId == null) {
            return unauthorized();
        }
        return ResponseEntity.ok(aiInterviewService.adminDetail(userId, scheduleId));
    }

    @GetMapping("/usage")
    public ResponseEntity<?> usage(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            HttpServletRequest request) {
        String userId = requireLearnerFeature(request);
        if (userId == null) {
            return unauthorized();
        }
        return ResponseEntity.ok(aiInterviewService.usage(userId, from, to));
    }

    @GetMapping("/my")
    public ResponseEntity<?> mine(HttpServletRequest request) {
        String userId = requireLearnerFeature(request);
        if (userId == null) {
            return unauthorized();
        }
        return ResponseEntity.ok(aiInterviewService.listMine(userId));
    }

    @GetMapping("/invite/{token}")
    public ResponseEntity<?> preview(@PathVariable String token) {
        platformFeatureRolloutService.assertPubliclyLive(PlatformFeatureRolloutService.AI_INTERVIEW);
        return ResponseEntity.ok(aiInterviewService.preview(token));
    }

    @GetMapping("/invite/{token}/calendar.ics")
    public ResponseEntity<byte[]> calendarInvite(@PathVariable String token) {
        platformFeatureRolloutService.assertPubliclyLive(PlatformFeatureRolloutService.AI_INTERVIEW);
        byte[] ics = aiInterviewService.calendarInviteIcs(token);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"interview.ics\"")
                .contentType(MediaType.parseMediaType("text/calendar;charset=UTF-8"))
                .body(ics);
    }

    @PostMapping("/invite/{token}/join")
    public ResponseEntity<?> join(@PathVariable String token, @RequestBody Map<String, Object> body) {
        platformFeatureRolloutService.assertPubliclyLive(PlatformFeatureRolloutService.AI_INTERVIEW);
        return ResponseEntity.ok(aiInterviewService.join(token, body == null ? Map.of() : body));
    }

    @PostMapping("/session/heartbeat")
    public ResponseEntity<?> heartbeat(
            @RequestHeader(value = SESSION_HEADER, required = false) String sessionToken) {
        return ResponseEntity.ok(aiInterviewService.heartbeat(sessionToken));
    }

    @PostMapping("/session/turns")
    public ResponseEntity<?> turns(
            @RequestHeader(value = SESSION_HEADER, required = false) String sessionToken,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(aiInterviewService.addTurn(sessionToken, body == null ? Map.of() : body));
    }

    @PostMapping("/session/next")
    public ResponseEntity<?> next(
            @RequestHeader(value = SESSION_HEADER, required = false) String sessionToken) {
        return ResponseEntity.ok(aiInterviewService.next(sessionToken));
    }

    @PostMapping("/session/end")
    public ResponseEntity<?> end(
            @RequestHeader(value = SESSION_HEADER, required = false) String sessionToken) {
        return ResponseEntity.ok(aiInterviewService.end(sessionToken));
    }

    @PostMapping("/session/snapshots")
    public ResponseEntity<?> snapshots(
            @RequestHeader(value = SESSION_HEADER, required = false) String sessionToken,
            @RequestParam("file") MultipartFile file,
            @RequestParam("offsetMinutes") int offsetMinutes) {
        return ResponseEntity.ok(aiInterviewService.addSnapshot(sessionToken, file, offsetMinutes));
    }

    private String requireLearnerFeature(HttpServletRequest request) {
        try {
            String userId = skillamaAuthSupport.resolveUserIdFromRequest(request);
            platformFeatureRolloutService.assertAccessible(
                    PlatformFeatureRolloutService.AI_INTERVIEW, userId);
            return userId;
        } catch (RuntimeException ex) {
            if (ex instanceof com.prwatech.skillama.exception.FeatureNotLiveException) {
                throw ex;
            }
            return null;
        }
    }

    private ResponseEntity<Map<String, Object>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("status", 401, "error", "Unauthorized", "message", "Unauthorized"));
    }
}
