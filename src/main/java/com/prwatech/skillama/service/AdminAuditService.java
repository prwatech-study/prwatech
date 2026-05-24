package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.AdminAuditLogDTO;
import com.prwatech.skillama.model.AdminAuditLog;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.AdminAuditLogRepository;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import com.prwatech.skillama.util.IndiaTime;

@Service
@RequiredArgsConstructor
public class AdminAuditService {

    public static final String USER_CREATE = "USER_CREATE";
    public static final String USER_UPDATE = "USER_UPDATE";
    public static final String USER_DELETE = "USER_DELETE";
    public static final String USER_HARD_DELETE = "USER_HARD_DELETE";
    public static final String ADMIN_PASSWORD_RESET = "ADMIN_PASSWORD_RESET";
    public static final String COURSE_CREATE = "COURSE_CREATE";
    public static final String COURSE_UPDATE = "COURSE_UPDATE";
    public static final String COURSE_DELETE = "COURSE_DELETE";
    public static final String COURSE_RESTORE = "COURSE_RESTORE";
    public static final String ASSIGN_COURSE = "ASSIGN_COURSE";
    public static final String UNASSIGN_COURSE = "UNASSIGN_COURSE";
    public static final String PLAN_UPDATE = "PLAN_UPDATE";
    public static final String CREDIT_ADJUST = "CREDIT_ADJUST";
    public static final String CURRICULUM_MODULE_CREATE = "CURRICULUM_MODULE_CREATE";
    public static final String CURRICULUM_MODULE_UPDATE = "CURRICULUM_MODULE_UPDATE";
    public static final String CURRICULUM_MODULE_DELETE = "CURRICULUM_MODULE_DELETE";
    public static final String CURRICULUM_SUBMODULE_CREATE = "CURRICULUM_SUBMODULE_CREATE";
    public static final String CURRICULUM_SUBMODULE_UPDATE = "CURRICULUM_SUBMODULE_UPDATE";
    public static final String CURRICULUM_SUBMODULE_DELETE = "CURRICULUM_SUBMODULE_DELETE";

    private final AdminAuditLogRepository auditLogRepository;
    private final SkillamaUserRepository userRepository;

    public void log(String actorId, String action, String entityType, String entityId, String summary, String detailsJson) {
        if (actorId == null || actorId.isBlank()) {
            return;
        }
        User actor = userRepository.findById(actorId).orElse(null);
        AdminAuditLog entry = AdminAuditLog.builder()
                .actorId(actorId)
                .actorEmail(actor != null ? actor.getEmail() : null)
                .actorRole(actor != null && actor.getRole() != null ? actor.getRole().name() : null)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .summary(summary)
                .detailsJson(detailsJson)
                .createdAt(IndiaTime.now())
                .build();
        auditLogRepository.save(entry);
    }

    public Page<AdminAuditLogDTO> list(int page, int size, String action, String actorId) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AdminAuditLog> logs;
        if (action != null && !action.isBlank()) {
            logs = auditLogRepository.findByActionOrderByCreatedAtDesc(action, pageable);
        } else if (actorId != null && !actorId.isBlank()) {
            logs = auditLogRepository.findByActorIdOrderByCreatedAtDesc(actorId, pageable);
        } else {
            logs = auditLogRepository.findAllByOrderByCreatedAtDesc(pageable);
        }
        return logs.map(this::toDto);
    }

    private AdminAuditLogDTO toDto(AdminAuditLog log) {
        return AdminAuditLogDTO.builder()
                .id(log.getId())
                .actorId(log.getActorId())
                .actorEmail(log.getActorEmail())
                .actorRole(log.getActorRole())
                .action(log.getAction())
                .entityType(log.getEntityType())
                .entityId(log.getEntityId())
                .summary(log.getSummary())
                .detailsJson(log.getDetailsJson())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
