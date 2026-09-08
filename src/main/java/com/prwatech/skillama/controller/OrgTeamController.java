package com.prwatech.skillama.controller;

import com.prwatech.skillama.dto.ApiResponse;
import com.prwatech.skillama.dto.TeamCourseProgressDTO;
import com.prwatech.skillama.dto.TeamMemberCourseProgressDTO;
import com.prwatech.skillama.dto.TeamProgressSummaryDTO;
import com.prwatech.skillama.model.AdminPermissionAction;
import com.prwatech.skillama.model.OrgModule;
import com.prwatech.skillama.model.OrgRole;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.service.OrgAnalyticsService;
import com.prwatech.skillama.service.OrgFeatureService;
import com.prwatech.skillama.service.OrgPermissionService;
import com.prwatech.skillama.service.SkillamaAuthSupport;
import com.prwatech.skillama.service.TenantSecurityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/skillama/api/org/team")
@RequiredArgsConstructor
public class OrgTeamController {

    private final OrgAnalyticsService orgAnalyticsService;
    private final OrgFeatureService orgFeatureService;
    private final SkillamaAuthSupport skillamaAuthSupport;
    private final TenantSecurityService tenantSecurityService;
    private final OrgPermissionService orgPermissionService;

    @GetMapping("/progress")
    public ResponseEntity<ApiResponse<TeamProgressSummaryDTO>> teamProgress(HttpServletRequest request) {
        try {
            User actor = requireTeamAnalyticsActor(request);
            return ResponseEntity.ok(new ApiResponse<>(200, orgAnalyticsService.getTeamProgress(actor)));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse<>(403, null));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }

    @GetMapping("/progress/courses")
    public ResponseEntity<ApiResponse<List<TeamCourseProgressDTO>>> teamProgressByCourse(
            HttpServletRequest request) {
        try {
            User actor = requireTeamAnalyticsActor(request);
            return ResponseEntity.ok(new ApiResponse<>(200, orgAnalyticsService.getTeamProgressByCourse(actor)));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse<>(403, null));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }

    @GetMapping("/members/{userId}/courses")
    public ResponseEntity<ApiResponse<List<TeamMemberCourseProgressDTO>>> memberCourseProgress(
            @PathVariable String userId, HttpServletRequest request) {
        try {
            User actor = requireTeamAnalyticsActor(request);
            return ResponseEntity.ok(new ApiResponse<>(200, orgAnalyticsService.getMemberCourseProgress(actor, userId)));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse<>(403, null));
        } catch (com.prwatech.skillama.exception.ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(404, null));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }

    @GetMapping("/progress/export")
    public ResponseEntity<String> exportTeamProgress(HttpServletRequest request) {
        try {
            User actor = requireTeamAnalyticsActor(request);
            TeamProgressSummaryDTO summary = orgAnalyticsService.getTeamProgress(actor);
            StringBuilder csv = new StringBuilder("name,email,role,department,progressPercent,lastActive\n");
            if (summary.getMembers() != null) {
                summary.getMembers().forEach(m -> csv.append(escapeCsv(m.getName())).append(',')
                        .append(escapeCsv(m.getEmail())).append(',')
                        .append(m.getOrgRole() != null ? m.getOrgRole() : "").append(',')
                        .append(escapeCsv(m.getDepartment())).append(',')
                        .append(m.getAverageProgress()).append(',')
                        .append(m.getLastLoginAt() != null ? m.getLastLoginAt() : "")
                        .append('\n'));
            }
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=team-progress.csv")
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .body(csv.toString());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    /**
     * Quotes separators and also neutralizes leading characters that spreadsheet software
     * treats as the start of a formula. Names can originate from an SSO profile the member
     * controls, so an unescaped value could execute when a manager opens the export.
     */
    private static String escapeCsv(String value) {
        if (value == null) return "";
        String sanitized = value;
        if (!sanitized.isEmpty() && "=+-@\t\r".indexOf(sanitized.charAt(0)) >= 0) {
            sanitized = "'" + sanitized;
        }
        if (sanitized.contains(",") || sanitized.contains("\"") || sanitized.contains("\n")
                || sanitized.contains("\r")) {
            return "\"" + sanitized.replace("\"", "\"\"") + "\"";
        }
        return sanitized;
    }

    private User requireTeamAnalyticsActor(HttpServletRequest request) {
        User actor = tenantSecurityService.requireUser(skillamaAuthSupport.resolveUserIdFromRequest(request));
        if (actor.getOrganizationId() == null) {
            throw new IllegalStateException("Organization required");
        }
        if (!orgFeatureService.isEnabled(actor.getOrganizationId(), "team_analytics")) {
            throw new IllegalStateException("Feature disabled");
        }
        OrgRole role = actor.getOrgRole();
        if (role == OrgRole.MANAGER || TenantSecurityService.isPlatformStaff(actor)) {
            return actor;
        }
        if (role != OrgRole.ORG_ADMIN && role != OrgRole.ORG_OWNER) {
            throw new IllegalStateException("Insufficient role");
        }
        orgPermissionService.require(actor, OrgModule.REPORTING, AdminPermissionAction.READ);
        return actor;
    }
}
