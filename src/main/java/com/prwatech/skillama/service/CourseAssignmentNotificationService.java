package com.prwatech.skillama.service;

import com.prwatech.skillama.model.Course;
import com.prwatech.skillama.notification.NotificationEventType;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.CourseRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CourseAssignmentNotificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CourseAssignmentNotificationService.class);

    private final CourseRepository courseRepository;
    private final NotificationSettingsService notificationSettingsService;

    @Value("${skillama.app.public-url:https://skillama.co.in}")
    private String publicUrl;

    public void notifyCoursesAssigned(User user, List<String> courseIds) {
        if (courseIds == null || courseIds.isEmpty()) {
            return;
        }
        String courseList = formatCourseList(courseIds);
        String studentSubject = "New courses are on your Skillama account";
        String studentBody = "Hi " + safeName(user) + ",\n\n"
                + "These courses are ready for you:\n\n"
                + courseList + "\n"
                + "Start with the first lesson whenever you have a few minutes.\n\n"
                + appUrl() + "/lms\n\n"
                + "— Skillama";

        String teamSubject = "Courses assigned to " + user.getEmail();
        String teamBody = "Courses were added to a learner account.\n\n"
                + "Learner: " + safeName(user) + "\n"
                + "Email: " + user.getEmail() + "\n\n"
                + "Courses\n"
                + courseList + "\n"
                + "— Skillama";

        sendToStudentAndTeam(
                user.getEmail(),
                studentSubject,
                studentBody,
                NotificationEventType.COURSE_ASSIGNED_LEARNER,
                teamSubject,
                teamBody,
                NotificationEventType.COURSE_ASSIGNED_TEAM);
    }

    public void notifyCourseUnassigned(User user, String courseId) {
        String courseName = courseRepository.findById(courseId)
                .map(Course::getName)
                .orElse(courseId);

        String studentSubject = "A course was removed from your account";
        String studentBody = "Hi " + safeName(user) + ",\n\n"
                + "You no longer have access to this course:\n\n"
                + "• " + courseName + "\n\n"
                + "If that was a mistake, ask the person who manages your Skillama access.\n\n"
                + "— Skillama";

        String teamSubject = "Course removed for " + user.getEmail();
        String teamBody = "A course was removed from a learner account.\n\n"
                + "Learner: " + safeName(user) + "\n"
                + "Email: " + user.getEmail() + "\n"
                + "Course: " + courseName + "\n\n"
                + "— Skillama";

        sendToStudentAndTeam(
                user.getEmail(),
                studentSubject,
                studentBody,
                NotificationEventType.COURSE_UNASSIGNED_LEARNER,
                teamSubject,
                teamBody,
                NotificationEventType.COURSE_UNASSIGNED_TEAM);
    }

    private void sendToStudentAndTeam(
            String studentEmail,
            String studentSubject,
            String studentBody,
            NotificationEventType learnerType,
            String teamSubject,
            String teamBody,
            NotificationEventType teamType) {
        try {
            notificationSettingsService.sendLearnerNotification(
                    learnerType, studentEmail, studentSubject, studentBody);
            notificationSettingsService.sendTeamNotification(teamType, teamSubject, teamBody);
        } catch (Exception e) {
            LOGGER.error("Failed to send course assignment notification", e);
        }
    }

    private String formatCourseList(List<String> courseIds) {
        StringBuilder sb = new StringBuilder();
        for (String courseId : courseIds) {
            String name = courseRepository.findById(courseId)
                    .map(Course::getName)
                    .orElse(courseId);
            sb.append("• ").append(name).append("\n");
        }
        return sb.toString();
    }

    private static String safeName(User user) {
        return user.getName() != null ? user.getName() : "there";
    }

    private String appUrl() {
        String base = publicUrl == null || publicUrl.isBlank() ? "https://skillama.co.in" : publicUrl.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }
}
