package com.prwatech.skillama.service;

import com.prwatech.skillama.model.Course;
import com.prwatech.skillama.notification.NotificationEventType;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.CourseRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CourseAssignmentNotificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CourseAssignmentNotificationService.class);

    private final CourseRepository courseRepository;
    private final NotificationSettingsService notificationSettingsService;

    public void notifyCoursesAssigned(User user, List<String> courseIds) {
        if (courseIds == null || courseIds.isEmpty()) {
            return;
        }
        String courseList = formatCourseList(courseIds);
        String studentSubject = "New course(s) assigned on Skillama";
        String studentBody = "Hello " + safeName(user) + ",\n\n"
                + "The following course(s) have been assigned to your account:\n"
                + courseList + "\n\n"
                + "Sign in to Skillama LMS to start learning.\n\n"
                + "Thank you,\nSkillama Team";

        String teamSubject = "Course assignment — " + user.getEmail();
        String teamBody = "Courses were assigned to a learner.\n\n"
                + "User: " + safeName(user) + " (" + user.getEmail() + ")\n"
                + "Assigned courses:\n" + courseList;

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

        String studentSubject = "Course access removed — Skillama";
        String studentBody = "Hello " + safeName(user) + ",\n\n"
                + "Access to the following course has been removed from your account:\n"
                + "• " + courseName + "\n\n"
                + "If you believe this is a mistake, please contact your administrator.\n\n"
                + "Thank you,\nSkillama Team";

        String teamSubject = "Course unassigned — " + user.getEmail();
        String teamBody = "A course was removed from a learner.\n\n"
                + "User: " + safeName(user) + " (" + user.getEmail() + ")\n"
                + "Course: " + courseName + " (" + courseId + ")";

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
        return user.getName() != null ? user.getName() : "Learner";
    }
}
