package com.prwatech.skillama.controller;

import com.prwatech.authentication.security.JwtUtils;
import com.prwatech.skillama.model.CourseCurriculum;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.service.AdminAuditService;
import com.prwatech.skillama.service.CourseCurriculumService;
import com.prwatech.skillama.service.CourseService;
import com.prwatech.skillama.service.UserService;
import com.prwatech.skillama.repository.CourseCurriculumRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/skillama/curriculum")
@RequiredArgsConstructor
public class CourseCurriculumController {
    private final CourseCurriculumService curriculumService;
    private final CourseService courseService;
    private final CourseCurriculumRepository curriculumRepo;
    private final AdminAuditService adminAuditService;
    private final JwtUtils jwtUtils;
    private final UserService userService;

    @PostMapping("/module")
    public ResponseEntity<CourseCurriculum> addModule(
            @RequestBody CourseCurriculum module,
            HttpServletRequest request) {
        CourseCurriculum created = courseService.addModule(module);
        auditCurriculum(request, AdminAuditService.CURRICULUM_MODULE_CREATE, created.getId(),
                "Created module " + (created.getModuleName() != null ? created.getModuleName() : created.getId()));
        return ResponseEntity.ok(created);
    }

    @GetMapping("/{moduleId}")
    public ResponseEntity<CourseCurriculum> getById(@PathVariable String moduleId) {
        return curriculumService.findById(moduleId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/module/{moduleId}")
    public ResponseEntity<CourseCurriculum> updateModule(
            @PathVariable String moduleId,
            @RequestBody CourseCurriculum updated,
            HttpServletRequest request) {
        CourseCurriculum result = courseService.updateModule(moduleId, updated);
        if (result == null) return ResponseEntity.notFound().build();
        auditCurriculum(request, AdminAuditService.CURRICULUM_MODULE_UPDATE, moduleId,
                "Updated module " + moduleId);
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/module/{moduleId}")
    public ResponseEntity<Void> removeModule(
            @PathVariable String moduleId,
            HttpServletRequest request) {
        courseService.removeModule(moduleId);
        auditCurriculum(request, AdminAuditService.CURRICULUM_MODULE_DELETE, moduleId,
                "Deleted module " + moduleId);
        return ResponseEntity.noContent().build();
    }

    // --- SUBMODULE MANAGEMENT ---
    @PostMapping("/module/{moduleId}/submodule")
    public ResponseEntity<CourseCurriculum> addSubmodule(
            @PathVariable String moduleId,
            @RequestBody CourseCurriculum.Submodule submodule,
            HttpServletRequest request) {
        CourseCurriculum result = courseService.addSubmodule(moduleId, submodule);
        if (result == null) return ResponseEntity.notFound().build();
        auditCurriculum(request, AdminAuditService.CURRICULUM_SUBMODULE_CREATE, moduleId,
                "Added submodule to module " + moduleId);
        return ResponseEntity.ok(result);
    }

    @PutMapping("/module/{moduleId}/submodule/{idx}")
    public ResponseEntity<CourseCurriculum> updateSubmodule(
            @PathVariable String moduleId,
            @PathVariable int idx,
            @RequestBody CourseCurriculum.Submodule submodule,
            HttpServletRequest request) {
        CourseCurriculum result = courseService.updateSubmodule(moduleId, idx, submodule);
        if (result == null) return ResponseEntity.notFound().build();
        auditCurriculum(request, AdminAuditService.CURRICULUM_SUBMODULE_UPDATE, moduleId,
                "Updated submodule " + idx + " on module " + moduleId);
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/module/{moduleId}/submodule/{idx}")
    public ResponseEntity<CourseCurriculum> removeSubmodule(
            @PathVariable String moduleId,
            @PathVariable int idx,
            HttpServletRequest request) {
        CourseCurriculum result = courseService.removeSubmodule(moduleId, idx);
        if (result == null) return ResponseEntity.notFound().build();
        auditCurriculum(request, AdminAuditService.CURRICULUM_SUBMODULE_DELETE, moduleId,
                "Removed submodule " + idx + " from module " + moduleId);
        return ResponseEntity.ok(result);
    }

    private void auditCurriculum(HttpServletRequest request, String action, String entityId, String summary) {
        Optional<String> actorId = extractAdminActorId(request);
        actorId.ifPresent(id -> adminAuditService.log(id, action, "CURRICULUM", entityId, summary, null));
    }

    private Optional<String> extractAdminActorId(HttpServletRequest request) {
        try {
            String header = request.getHeader("Authorization");
            if (header == null || !header.startsWith("Bearer ")) {
                return Optional.empty();
            }
            String email = jwtUtils.extractUsername(header.substring(7));
            User user = userService.findByEmail(email).orElse(null);
            if (user == null || (user.getRole() != User.UserRole.ADMIN && user.getRole() != User.UserRole.OWNER)) {
                return Optional.empty();
            }
            return Optional.of(user.getId());
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
