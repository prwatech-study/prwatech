package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.TeamCourseProgressDTO;
import com.prwatech.skillama.dto.TeamMemberCourseProgressDTO;
import com.prwatech.skillama.dto.TeamMemberDTO;
import com.prwatech.skillama.dto.TeamProgressSummaryDTO;
import com.prwatech.skillama.model.Course;
import com.prwatech.skillama.model.OrgRole;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.model.UserCourseProgress;
import com.prwatech.skillama.repository.CourseRepository;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import com.prwatech.skillama.repository.UserCourseProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
@RequiredArgsConstructor
public class OrgAnalyticsService {

    private final OrgHierarchyService orgHierarchyService;
    private final SkillamaUserRepository userRepository;
    private final UserCourseProgressRepository progressRepository;
    private final CourseRepository courseRepository;
    private final TenantSecurityService tenantSecurityService;

    public TeamProgressSummaryDTO getTeamProgress(User actor) {
        tenantSecurityService.requireActiveOrganization(actor.getOrganizationId());
        Set<String> visibleIds = orgHierarchyService.getVisibleUserIds(actor);
        if (visibleIds.isEmpty()) {
            return TeamProgressSummaryDTO.builder()
                    .totalMembers(0)
                    .averageProgress(0)
                    .activeMembers(0)
                    .members(List.of())
                    .build();
        }

        List<User> users = StreamSupport.stream(userRepository.findAllById(visibleIds).spliterator(), false)
                .collect(Collectors.toList());
        Map<String, User> managerById = userRepository.findByOrganizationId(actor.getOrganizationId()).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        List<TeamMemberDTO> members = new ArrayList<>();
        double progressSum = 0;
        int progressCount = 0;
        int activeCount = 0;

        for (User user : users) {
            if (!user.isActive()) {
                continue;
            }
            activeCount++;
            double avg = averageProgressForUser(user.getId());
            progressSum += avg;
            progressCount++;

            String managerName = null;
            if (user.getManagerUserId() != null) {
                User manager = managerById.get(user.getManagerUserId());
                managerName = manager != null ? manager.getName() : null;
            }

            TeamMemberDTO dto = TeamMemberDTO.builder()
                    .userId(user.getId())
                    .name(user.getName())
                    .email(user.getEmail())
                    .orgRole(user.getOrgRole() != null ? user.getOrgRole() : OrgRole.LEARNER)
                    .managerUserId(user.getManagerUserId())
                    .managerName(managerName)
                    .department(user.getDepartment())
                    .averageProgress(avg)
                    .lastLoginAt(user.getLastLoginAt())
                    .build();
            members.add(dto);
        }

        double average = progressCount > 0 ? progressSum / progressCount : 0;
        return TeamProgressSummaryDTO.builder()
                .totalMembers(members.size())
                .averageProgress(Math.round(average * 10.0) / 10.0)
                .activeMembers(activeCount)
                .members(members)
                .build();
    }

    public List<TeamCourseProgressDTO> getTeamProgressByCourse(User actor) {
        tenantSecurityService.requireActiveOrganization(actor.getOrganizationId());
        Set<String> visibleIds = orgHierarchyService.getVisibleUserIds(actor);
        if (visibleIds.isEmpty()) {
            return List.of();
        }

        Map<String, List<UserCourseProgress>> byCourse = new java.util.HashMap<>();
        for (String userId : visibleIds) {
            for (UserCourseProgress row : progressRepository.findByUserId(userId)) {
                if (row.getCourseId() == null) {
                    continue;
                }
                byCourse.computeIfAbsent(row.getCourseId(), k -> new ArrayList<>()).add(row);
            }
        }

        List<TeamCourseProgressDTO> result = new ArrayList<>();
        for (Map.Entry<String, List<UserCourseProgress>> entry : byCourse.entrySet()) {
            String courseId = entry.getKey();
            List<UserCourseProgress> rows = entry.getValue();
            double avg = rows.stream()
                    .mapToDouble(r -> r.getProgress() != null ? r.getProgress() : 0)
                    .average()
                    .orElse(0);
            int completed = (int) rows.stream()
                    .filter(r -> r.getProgress() != null && r.getProgress() >= 100)
                    .count();
            result.add(TeamCourseProgressDTO.builder()
                    .courseId(courseId)
                    .courseName(resolveCourseName(courseId))
                    .enrolledLearners(rows.size())
                    .averageProgress(Math.round(avg * 10.0) / 10.0)
                    .completedLearners(completed)
                    .build());
        }
        result.sort(java.util.Comparator.comparing(
                TeamCourseProgressDTO::getCourseName,
                java.util.Comparator.nullsLast(String::compareToIgnoreCase)));
        return result;
    }

    public List<TeamMemberCourseProgressDTO> getMemberCourseProgress(User actor, String targetUserId) {
        tenantSecurityService.requireActiveOrganization(actor.getOrganizationId());
        Set<String> visibleIds = orgHierarchyService.getVisibleUserIds(actor);
        if (!visibleIds.contains(targetUserId)) {
            throw new IllegalStateException("User is outside your team scope");
        }
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new com.prwatech.skillama.exception.ResourceNotFoundException("User not found"));
        tenantSecurityService.assertUserInOrg(target, actor.getOrganizationId());

        return progressRepository.findByUserId(targetUserId).stream()
                .map(row -> TeamMemberCourseProgressDTO.builder()
                        .userId(targetUserId)
                        .userName(target.getName())
                        .courseId(row.getCourseId())
                        .courseName(resolveCourseName(row.getCourseId()))
                        .progress(row.getProgress() != null ? row.getProgress() : 0)
                        .completedLectures(row.getCompletedLectures())
                        .totalLectures(row.getTotalLectures())
                        .lastAccessed(row.getLastAccessed())
                        .build())
                .sorted(java.util.Comparator.comparing(
                        TeamMemberCourseProgressDTO::getCourseName,
                        java.util.Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
    }

    private String resolveCourseName(String courseId) {
        if (courseId == null) {
            return "Unknown course";
        }
        return courseRepository.findById(courseId)
                .map(Course::getName)
                .orElse("Course");
    }

    private double averageProgressForUser(String userId) {
        List<UserCourseProgress> rows = progressRepository.findByUserId(userId);
        if (rows.isEmpty()) {
            return 0;
        }
        return rows.stream()
                .mapToDouble(r -> r.getProgress() != null ? r.getProgress() : 0)
                .average()
                .orElse(0);
    }
}
