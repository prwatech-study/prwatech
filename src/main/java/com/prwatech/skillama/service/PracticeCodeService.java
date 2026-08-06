package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.GeneratedPracticeCodeDTO;
import com.prwatech.skillama.dto.PracticeCodeRequestDTO;
import com.prwatech.skillama.dto.PracticeCodeResponseDTO;
import com.prwatech.skillama.model.Course;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.CourseRepository;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Practice-code generation (the in-lecture "Will learn, ..." practice snippet) — server-
 * mediated so the AI wallet budget is enforced BEFORE the (real-cost) ai-tutor call runs.
 * Previously this went straight from the browser to ai-tutor (see
 * SkillamaAiClient#generatePracticeCode), so nothing server-side ever gated it. The
 * budget-check + usage-recording pair itself lives in SkillamaAiClient's metered call
 * wrapper, not here — see that class.
 */
@Service
@RequiredArgsConstructor
public class PracticeCodeService {

    private static final String DEFAULT_COURSE_NAME = "Python";

    private final CourseRepository courseRepository;
    private final SkillamaUserRepository userRepository;
    private final SkillamaAiClient skillamaAiClient;

    public PracticeCodeResponseDTO generate(String userId, PracticeCodeRequestDTO request) {
        if (request == null || request.getQuery() == null || request.getQuery().isBlank()) {
            throw new IllegalArgumentException("query is required");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        String courseName = request.getCourseId() != null
                ? courseRepository.findById(request.getCourseId()).map(Course::getName).orElse(DEFAULT_COURSE_NAME)
                : DEFAULT_COURSE_NAME;

        GeneratedPracticeCodeDTO generated = skillamaAiClient.generatePracticeCode(
                user, request.getCourseId(), request.getQuery(), request.getCodeInstruction(), courseName);

        return PracticeCodeResponseDTO.builder()
                .codeResult(generated.getCodeResult())
                .audioUrl(generated.getAudioUrl())
                .subtitlePath(generated.getSubtitlePath())
                .build();
    }
}
