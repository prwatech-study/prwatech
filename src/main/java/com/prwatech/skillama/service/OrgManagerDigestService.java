package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.TeamMemberDTO;
import com.prwatech.skillama.dto.TeamProgressSummaryDTO;
import com.prwatech.skillama.model.Organization;
import com.prwatech.skillama.model.OrgRole;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.notification.NotificationEventType;
import com.prwatech.skillama.repository.OrganizationRepository;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Weekly manager digest from team analytics — preview API + scheduled email.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrgManagerDigestService {

    private final OrganizationRepository organizationRepository;
    private final SkillamaUserRepository userRepository;
    private final OrgAnalyticsService orgAnalyticsService;
    private final NotificationSettingsService notificationSettingsService;
    private final AdminAuditService adminAuditService;

    public Map<String, Object> buildDigest(User manager) {
        if (manager == null || manager.getOrganizationId() == null) {
            throw new IllegalArgumentException("Manager must belong to an organization");
        }
        Organization org = organizationRepository.findById(manager.getOrganizationId())
                .orElseThrow(() -> new IllegalArgumentException("Organization not found"));

        TeamProgressSummaryDTO team = orgAnalyticsService.getTeamProgress(manager);
        List<Map<String, Object>> heatmap = team.getMembers() == null
                ? List.of()
                : team.getMembers().stream()
                        .sorted(Comparator.comparingDouble(TeamMemberDTO::getAverageProgress).reversed())
                        .limit(15)
                        .map(m -> {
                            Map<String, Object> row = new HashMap<>();
                            row.put("name", m.getName());
                            row.put("email", m.getEmail());
                            row.put("department", m.getDepartment());
                            row.put("averageProgress", m.getAverageProgress());
                            return row;
                        })
                        .collect(Collectors.toList());

        Map<String, Object> digest = new HashMap<>();
        digest.put("organizationId", org.getId());
        digest.put("organizationName", org.getName());
        digest.put("managerEmail", manager.getEmail());
        digest.put("managerName", manager.getName());
        digest.put("totalMembers", team.getTotalMembers());
        digest.put("activeMembers", team.getActiveMembers());
        digest.put("averageProgress", team.getAverageProgress());
        digest.put("skillsHeatmap", heatmap);
        digest.put("generatedAt", com.prwatech.skillama.util.IndiaTime.now().toString());
        return digest;
    }

    public List<Map<String, Object>> previewForOrganization(String organizationId) {
        return managersForOrg(organizationId).stream().map(this::buildDigest).toList();
    }

    /** Cron entry: email every active org manager/admin/owner. */
    public int sendWeeklyDigests() {
        int sent = 0;
        for (Organization org : organizationRepository.findAll()) {
            if (org == null || org.getId() == null) {
                continue;
            }
            for (User manager : managersForOrg(org.getId())) {
                try {
                    Map<String, Object> digest = buildDigest(manager);
                    if (!StringUtils.hasText(manager.getEmail())) {
                        continue;
                    }
                    String body = formatEmailBody(digest);
                    notificationSettingsService.sendLearnerNotification(
                            NotificationEventType.ORG_MANAGER_DIGEST,
                            manager.getEmail(),
                            "Weekly team digest — " + digest.get("organizationName"),
                            body);
                    adminAuditService.log(
                            manager.getId(),
                            AdminAuditService.ORG_MANAGER_DIGEST_SENT,
                            "Organization",
                            org.getId(),
                            "Manager digest emailed to " + manager.getEmail(),
                            null);
                    sent++;
                } catch (Exception e) {
                    log.warn("Digest email failed for {}: {}", manager.getId(), e.getMessage());
                }
            }
        }
        return sent;
    }

    private List<User> managersForOrg(String organizationId) {
        return userRepository.findByOrganizationId(organizationId).stream()
                .filter(User::isActive)
                .filter(u -> {
                    OrgRole role = u.getOrgRole();
                    return role == OrgRole.ORG_OWNER
                            || role == OrgRole.ORG_ADMIN
                            || role == OrgRole.MANAGER;
                })
                .toList();
    }

    private static String formatEmailBody(Map<String, Object> digest) {
        StringBuilder sb = new StringBuilder();
        sb.append("Hi ").append(digest.getOrDefault("managerName", "there")).append(",\n\n");
        sb.append("Weekly Skillama team snapshot for ")
                .append(digest.get("organizationName"))
                .append(":\n\n");
        sb.append("- Members: ").append(digest.get("totalMembers")).append("\n");
        sb.append("- Active: ").append(digest.get("activeMembers")).append("\n");
        sb.append("- Average progress: ").append(digest.get("averageProgress")).append("%\n\n");
        sb.append("Top learners (skills heatmap):\n");
        Object heat = digest.get("skillsHeatmap");
        if (heat instanceof List<?> list && !list.isEmpty()) {
            for (Object row : list) {
                if (row instanceof Map<?, ?> m) {
                    sb.append("  • ")
                            .append(m.get("name"))
                            .append(" — ")
                            .append(m.get("averageProgress"))
                            .append("%");
                    if (m.get("department") != null) {
                        sb.append(" (").append(m.get("department")).append(")");
                    }
                    sb.append("\n");
                }
            }
        } else {
            sb.append("  (no member progress yet)\n");
        }
        sb.append("\n— Skillama\n");
        return sb.toString();
    }
}
