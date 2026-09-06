package com.prwatech.skillama.controller;

import com.prwatech.skillama.dto.ApiResponse;
import com.prwatech.skillama.dto.ErrorResponse;
import com.prwatech.skillama.dto.KnowledgeBaseFileDTO;
import com.prwatech.skillama.dto.KnowledgeBaseSyncEventDTO;
import com.prwatech.skillama.dto.KnowledgeBaseSyncResponseDTO;
import com.prwatech.skillama.dto.KnowledgeBaseSyncStatusDTO;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.AdminModule;
import com.prwatech.skillama.model.AdminPermissionAction;
import com.prwatech.skillama.service.AdminPermissionService;
import com.prwatech.skillama.service.CourseKnowledgeFileService;
import com.prwatech.skillama.service.SkillamaAuthSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/skillama/api/admin/courses/{courseId}/knowledge-base")
@RequiredArgsConstructor
public class AdminKnowledgeBaseController {

    private final CourseKnowledgeFileService knowledgeFileService;
    private final AdminPermissionService adminPermissionService;
    private final SkillamaAuthSupport skillamaAuthSupport;

    @GetMapping
    public ResponseEntity<?> list(@PathVariable String courseId, HttpServletRequest request) {
        try {
            requirePermission(request, AdminPermissionAction.READ);
            List<KnowledgeBaseFileDTO> files = knowledgeFileService.listForCourse(courseId);
            return ResponseEntity.ok(new ApiResponse<>(200, files));
        } catch (ResourceNotFoundException e) {
            return notFound(e);
        } catch (RuntimeException e) {
            return forbiddenOrUnauthorized(e);
        }
    }

    @PostMapping
    public ResponseEntity<?> upload(
            @PathVariable String courseId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "replaceFileId", required = false) String replaceFileId,
            HttpServletRequest request) {
        try {
            String userId = requirePermission(request, AdminPermissionAction.UPDATE);
            KnowledgeBaseFileDTO dto = knowledgeFileService.upload(courseId, file, replaceFileId, userId);
            return ResponseEntity.ok(new ApiResponse<>(200, dto));
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().contains("File size")) {
                return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                        .body(new ErrorResponse(413, "FILE_TOO_LARGE", e.getMessage()));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse(400, "INVALID_FILE", e.getMessage()));
        } catch (ResourceNotFoundException e) {
            return notFound(e);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse(500, "UPLOAD_ERROR", e.getMessage()));
        } catch (RuntimeException e) {
            return forbiddenOrUnauthorized(e);
        }
    }

    @DeleteMapping("/{fileId}")
    public ResponseEntity<?> delete(
            @PathVariable String courseId,
            @PathVariable String fileId,
            HttpServletRequest request) {
        try {
            requirePermission(request, AdminPermissionAction.DELETE);
            knowledgeFileService.delete(courseId, fileId);
            return ResponseEntity.ok(new ApiResponse<>(200, "Deleted"));
        } catch (ResourceNotFoundException e) {
            return notFound(e);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse(500, "DELETE_ERROR", e.getMessage()));
        } catch (RuntimeException e) {
            return forbiddenOrUnauthorized(e);
        }
    }

    @PostMapping("/sync")
    public ResponseEntity<?> sync(@PathVariable String courseId, HttpServletRequest request) {
        try {
            String userId = requirePermission(request, AdminPermissionAction.UPDATE);
            KnowledgeBaseSyncResponseDTO result = knowledgeFileService.sync(courseId, userId);
            return ResponseEntity.ok(new ApiResponse<>(200, result));
        } catch (ResourceNotFoundException e) {
            return notFound(e);
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("Owner approval required")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ErrorResponse(403, "OWNER_APPROVAL_REQUIRED", e.getMessage()));
            }
            return forbiddenOrUnauthorized(e);
        }
    }

    @GetMapping("/sync/{jobId}")
    public ResponseEntity<?> getSyncStatus(
            @PathVariable String courseId,
            @PathVariable String jobId,
            HttpServletRequest request) {
        try {
            requirePermission(request, AdminPermissionAction.READ);
            KnowledgeBaseSyncStatusDTO status = knowledgeFileService.getSyncStatus(courseId, jobId);
            return ResponseEntity.ok(new ApiResponse<>(200, status));
        } catch (ResourceNotFoundException e) {
            return notFound(e);
        } catch (RuntimeException e) {
            return forbiddenOrUnauthorized(e);
        }
    }

    @GetMapping("/sync/history")
    public ResponseEntity<?> getSyncHistory(
            @PathVariable String courseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        try {
            requirePermission(request, AdminPermissionAction.READ);
            Page<KnowledgeBaseSyncEventDTO> history = knowledgeFileService.getSyncHistory(courseId, page, size);
            return ResponseEntity.ok(new ApiResponse<>(200, history));
        } catch (ResourceNotFoundException e) {
            return notFound(e);
        } catch (RuntimeException e) {
            return forbiddenOrUnauthorized(e);
        }
    }

    private String requirePermission(HttpServletRequest request, AdminPermissionAction action) {
        String userId = skillamaAuthSupport.resolveUserIdFromRequest(request);
        adminPermissionService.requirePermission(userId, AdminModule.KNOWLEDGE_BASE, action);
        return userId;
    }

    private ResponseEntity<ErrorResponse> notFound(ResourceNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(404, "NOT_FOUND", e.getMessage()));
    }

    private ResponseEntity<?> forbiddenOrUnauthorized(RuntimeException e) {
        String message = e.getMessage() != null ? e.getMessage() : "";
        if (message.contains("Insufficient permission") || message.contains("Owner access required")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse(403, "FORBIDDEN", message));
        }
        if (message.contains("Authorization") || message.contains("Unauthorized") || message.contains("Admin access")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(401, "UNAUTHORIZED", message));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(400, "BAD_REQUEST", message));
    }
}
