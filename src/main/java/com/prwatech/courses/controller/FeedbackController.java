package com.prwatech.courses.controller;

import com.prwatech.courses.model.Feedback;
import com.prwatech.courses.service.FeedbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/feedbacks")
@RequiredArgsConstructor
public class FeedbackController {
    private final FeedbackService feedbackService;

    @PostMapping
    public ResponseEntity<Feedback> createFeedback(@RequestBody Feedback feedback) {
        return ResponseEntity.ok(feedbackService.saveFeedback(feedback));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Feedback> getFeedbackById(@PathVariable String id) {
        return feedbackService.getFeedbackById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/course/{courseId}")
    public ResponseEntity<List<Feedback>> getFeedbacksByCourse(@PathVariable String courseId) {
        return ResponseEntity.ok(feedbackService.getFeedbacksByCourseId(courseId));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Feedback>> getFeedbacksByUser(@PathVariable String userId) {
        return ResponseEntity.ok(feedbackService.getFeedbacksByUserId(userId));
    }

    @GetMapping
    public ResponseEntity<Page<Feedback>> getAllFeedbacks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String order
    ) {
        boolean desc = order.equalsIgnoreCase("desc");
        return ResponseEntity.ok(feedbackService.getAllFeedbacks(page, size, sortBy, desc));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Feedback> updateFeedback(@PathVariable String id, @RequestBody Feedback feedback) {
        Feedback updated = feedbackService.updateFeedback(id, feedback);
        if (updated == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFeedback(@PathVariable String id) {
        feedbackService.deleteFeedback(id);
        return ResponseEntity.noContent().build();
    }
}
