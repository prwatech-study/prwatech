package com.prwatech.skillama.controller;

import com.prwatech.skillama.model.CourseDiscussionPost;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.CourseDiscussionPostRepository;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import com.prwatech.skillama.service.SkillamaAuthSupport;
import com.prwatech.skillama.util.IndiaTime;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/skillama/courses/{courseId}/discussion")
@RequiredArgsConstructor
public class CourseDiscussionController {

    private final CourseDiscussionPostRepository postRepository;
    private final SkillamaUserRepository userRepository;
    private final SkillamaAuthSupport skillamaAuthSupport;

    @GetMapping
    public ResponseEntity<List<CourseDiscussionPost>> list(@PathVariable String courseId) {
        return ResponseEntity.ok(
                postRepository.findByCourseIdAndActiveTrueOrderByCreatedAtDesc(courseId).stream()
                        .limit(50)
                        .toList());
    }

    @PostMapping
    public ResponseEntity<CourseDiscussionPost> create(
            @PathVariable String courseId,
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {
        String userId = skillamaAuthSupport.resolveUserIdFromRequest(request);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        String text = body != null ? body.get("body") : null;
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("body is required");
        }
        if (text.length() > 2000) {
            throw new IllegalArgumentException("body too long (max 2000)");
        }
        CourseDiscussionPost post = CourseDiscussionPost.builder()
                .id(UUID.randomUUID().toString())
                .courseId(courseId)
                .authorUserId(userId)
                .authorName(user.getName() != null ? user.getName() : user.getEmail())
                .body(text.trim())
                .active(true)
                .createdAt(IndiaTime.now())
                .build();
        return ResponseEntity.ok(postRepository.save(post));
    }
}
