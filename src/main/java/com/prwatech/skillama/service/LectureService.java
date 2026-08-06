package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.GeneratedLectureDTO;
import com.prwatech.skillama.dto.LectureRequestDTO;
import com.prwatech.skillama.dto.LectureResponseDTO;
import com.prwatech.skillama.model.Course;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.CourseRepository;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Lecture generation — server-mediated so the AI wallet budget is enforced BEFORE the
 * (real-cost) ai-tutor call runs. Previously this went straight from the browser to
 * ai-tutor (see SkillamaAiClient#generateLecture), so nothing server-side ever gated it.
 * The budget-check + usage-recording pair itself lives in SkillamaAiClient's metered
 * call wrapper, not here — see that class.
 */
@Service
@RequiredArgsConstructor
public class LectureService {

    private static final String DEFAULT_COURSE_NAME = "Python";

    private final CourseRepository courseRepository;
    private final SkillamaUserRepository userRepository;
    private final SkillamaAiClient skillamaAiClient;

    public LectureResponseDTO generate(String userId, LectureRequestDTO request) {
        if (request == null || request.getSection() == null || request.getSection().isBlank()) {
            throw new IllegalArgumentException("section is required");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        String courseName = request.getCourseId() != null
                ? courseRepository.findById(request.getCourseId()).map(Course::getName).orElse(DEFAULT_COURSE_NAME)
                : DEFAULT_COURSE_NAME;

        GeneratedLectureDTO generated = skillamaAiClient.generateLecture(
                user, request.getCourseId(), request.getSection(), courseName);

        return LectureResponseDTO.builder()
                .lectureText(generated.getLectureText())
                .audioUrl(generated.getAudioUrl())
                .subtitlePath(generated.getSubtitlePath())
                .build();
    }
}
