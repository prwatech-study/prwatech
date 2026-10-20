package com.prwatech.skillama.service;

import com.prwatech.common.exception.NotFoundException;
import com.prwatech.common.exception.UnProcessableEntityException;
import com.prwatech.skillama.dto.SkillamaCertificateDetailsDTO;
import com.prwatech.skillama.model.Course;
import com.prwatech.skillama.model.Organization;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.model.UserCourseProgress;
import com.prwatech.skillama.repository.CourseRepository;
import com.prwatech.skillama.repository.OrganizationRepository;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import com.prwatech.skillama.repository.UserCourseProgressRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

/**
 * Course completion certificates for Skillama LMS courses ({@code user_course_progress}).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SkillamaCertificateService {

    private static final int COMPLETE_THRESHOLD = 100;

    private final SkillamaUserRepository userRepository;
    private final CourseRepository courseRepository;
    private final UserCourseProgressRepository progressRepository;
    private final OrganizationRepository organizationRepository;
    private final SpringTemplateEngine templateEngine;

    public SkillamaCertificateDetailsDTO getDetails(String userId, String courseId) {
        User user = requireUser(userId);
        Course course = requireCourse(courseId);
        UserCourseProgress progress = requireCompleteProgress(userId, courseId);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.ENGLISH);
        String completedAt = progress.getUpdatedAt() != null
                ? progress.getUpdatedAt().format(formatter)
                : "";

        String logoUrl = null;
        if (user.getOrganizationId() != null) {
            logoUrl = organizationRepository.findById(user.getOrganizationId())
                    .map(Organization::getBranding)
                    .map(b -> b != null ? b.getCertificateLogoUrl() : null)
                    .orElse(null);
        }

        return SkillamaCertificateDetailsDTO.builder()
                .learnerName(user.getName() != null ? user.getName() : user.getEmail())
                .courseName(course.getName())
                .courseId(courseId)
                .completedAt(completedAt)
                .organizationLogoUrl(logoUrl)
                .progressPercent(progress.getProgress())
                .build();
    }

    public ByteArrayInputStream generatePdf(String userId, String courseId) {
        SkillamaCertificateDetailsDTO details = getDetails(userId, courseId);
        Context context = new Context();
        context.setVariable("name", details.getLearnerName());
        context.setVariable("courseName", details.getCourseName());
        context.setVariable("dateOfCompletion", details.getCompletedAt());
        String html = templateEngine.process("certificate", context);
        return renderPdf(html);
    }

    private ByteArrayInputStream renderPdf(String html) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ITextRenderer renderer = new ITextRenderer();
            renderer.setDocumentFromString(html);
            renderer.layout();
            renderer.createPDF(outputStream);
            outputStream.close();
            return new ByteArrayInputStream(outputStream.toByteArray());
        } catch (Exception e) {
            log.error("Failed to render Skillama certificate PDF", e);
            throw new UnProcessableEntityException("Could not create certificate PDF. Please try again.");
        }
    }

    private User requireUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    private Course requireCourse(String courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new NotFoundException("Course not found"));
    }

    private UserCourseProgress requireCompleteProgress(String userId, String courseId) {
        Optional<UserCourseProgress> opt = progressRepository.findByUserIdAndCourseId(userId, courseId);
        if (opt.isEmpty()) {
            throw new UnProcessableEntityException("You must complete the course to get a certificate.");
        }
        UserCourseProgress progress = opt.get();
        Integer pct = progress.getProgress();
        if (pct == null || pct < COMPLETE_THRESHOLD) {
            throw new UnProcessableEntityException("You must complete the course to get a certificate.");
        }
        return progress;
    }
}
