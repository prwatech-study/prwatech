package com.prwatech.skillama.service;

import com.prwatech.skillama.model.AdminAuditLog;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.AdminAuditLogRepository;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminAuditServiceTest {

    @Mock private AdminAuditLogRepository auditLogRepository;
    @Mock private SkillamaUserRepository userRepository;

    private AdminAuditService service;

    @BeforeEach
    void setUp() {
        service = new AdminAuditService(auditLogRepository, userRepository);
    }

    @Test
    void logSkipsWhenActorIdBlank() {
        service.log(null, AdminAuditService.USER_CREATE, "User", "u1", "created", null);
        service.log("  ", AdminAuditService.USER_CREATE, "User", "u1", "created", null);
        verify(auditLogRepository, never()).save(any());
    }

    @Test
    void logEnrichesActorEmailAndRole() {
        when(userRepository.findById("admin"))
                .thenReturn(Optional.of(User.builder().id("admin").email("a@x.com").role(User.UserRole.OWNER).build()));

        service.log("admin", AdminAuditService.COURSE_DELETE, "Course", "c1", "deleted course", "{}");

        ArgumentCaptor<AdminAuditLog> captor = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AdminAuditLog saved = captor.getValue();
        assertEquals("admin", saved.getActorId());
        assertEquals("a@x.com", saved.getActorEmail());
        assertEquals("OWNER", saved.getActorRole());
        assertEquals(AdminAuditService.COURSE_DELETE, saved.getAction());
        assertEquals("c1", saved.getEntityId());
    }

    @Test
    void logStillWritesWhenActorNotFound() {
        when(userRepository.findById("gone")).thenReturn(Optional.empty());
        service.log("gone", AdminAuditService.USER_UPDATE, "User", "u1", "x", null);

        ArgumentCaptor<AdminAuditLog> captor = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertEquals("gone", captor.getValue().getActorId());
        assertEquals(null, captor.getValue().getActorEmail());
    }

    @Test
    void listFiltersByActionFirst() {
        Page<AdminAuditLog> page = new PageImpl<>(List.of());
        when(auditLogRepository.findByActionOrderByCreatedAtDesc(eq("USER_CREATE"), any(Pageable.class)))
                .thenReturn(page);
        service.list(0, 20, "USER_CREATE", "someActor");
        verify(auditLogRepository).findByActionOrderByCreatedAtDesc(eq("USER_CREATE"), any(Pageable.class));
        verify(auditLogRepository, never()).findByActorIdOrderByCreatedAtDesc(any(), any());
    }

    @Test
    void listFiltersByActorWhenNoAction() {
        Page<AdminAuditLog> page = new PageImpl<>(List.of());
        when(auditLogRepository.findByActorIdOrderByCreatedAtDesc(eq("actor1"), any(Pageable.class)))
                .thenReturn(page);
        service.list(0, 20, null, "actor1");
        verify(auditLogRepository).findByActorIdOrderByCreatedAtDesc(eq("actor1"), any(Pageable.class));
    }

    @Test
    void listFallsBackToAll() {
        Page<AdminAuditLog> page = new PageImpl<>(List.of());
        when(auditLogRepository.findAllByOrderByCreatedAtDesc(any(Pageable.class))).thenReturn(page);
        service.list(0, 20, "  ", "  ");
        verify(auditLogRepository).findAllByOrderByCreatedAtDesc(any(Pageable.class));
    }
}
