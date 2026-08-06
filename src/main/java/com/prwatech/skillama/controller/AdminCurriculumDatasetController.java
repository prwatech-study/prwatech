package com.prwatech.skillama.controller;

import com.prwatech.common.Constants;
import com.prwatech.skillama.dto.ApiResponse;
import com.prwatech.skillama.dto.ErrorResponse;
import com.prwatech.skillama.dto.PracticalDatasetDTO;
import com.prwatech.skillama.exception.DuplicateDatasetException;
import com.prwatech.skillama.exception.InvalidDatasetException;
import com.prwatech.skillama.exception.MalwareDetectedException;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.AdminModule;
import com.prwatech.skillama.model.AdminPermissionAction;
import com.prwatech.skillama.service.AdminPermissionService;
import com.prwatech.skillama.service.PracticalDatasetService;
import com.prwatech.skillama.service.SkillamaAuthSupport;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * Admin endpoints for attaching a CSV dataset to a practical-exercise curriculum submodule.
 * Addressed by {moduleId, idx} — the same convention AdminCurriculumImageController uses for
 * submodule-scoped uploads.
 *
 * Endpoints:
 * - POST   /skillama/api/admin/curriculum/submodules/{moduleId}/{idx}/dataset — upload/replace
 * - GET    /skillama/api/admin/curriculum/submodules/{moduleId}/{idx}/dataset — metadata
 * - DELETE /skillama/api/admin/curriculum/submodules/{moduleId}/{idx}/dataset — soft delete
 */
@RestController
@RequestMapping("/skillama/api/admin/curriculum/submodules")
@RequiredArgsConstructor
public class AdminCurriculumDatasetController {

    private final PracticalDatasetService datasetService;
    private final AdminPermissionService adminPermissionService;
    private final SkillamaAuthSupport skillamaAuthSupport;

    @ApiOperation(value = "Upload the CSV dataset for a practical exercise submodule")
    @ApiImplicitParams({
            @ApiImplicitParam(name = Constants.AUTH, value = Constants.TOKEN_TYPE, required = true,
                    dataType = Constants.AUTH_DATA_TYPE, paramType = Constants.AUTH_PARAM_TYPE)
    })
    @PostMapping("/{moduleId}/{idx}/dataset")
    public ResponseEntity<?> upload(
            @PathVariable String moduleId,
            @PathVariable int idx,
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        try {
            String adminUserId = assertCurriculumPermission(request, AdminPermissionAction.UPDATE);
            PracticalDatasetDTO dto = datasetService.upload(moduleId, idx, file, adminUserId);
            return ResponseEntity.ok(new ApiResponse<>(200, dto));
        } catch (InvalidDatasetException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse(400, "INVALID_FILE", e.getMessage()));
        } catch (MalwareDetectedException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse(400, "MALWARE_DETECTED", e.getMessage()));
        } catch (DuplicateDatasetException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse(409, "DUPLICATE_DATASET", e.getMessage()));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(404, "NOT_FOUND", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(new ErrorResponse(502, "SCAN_UNAVAILABLE", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse(500, "UPLOAD_ERROR", e.getMessage()));
        }
    }

    @ApiOperation(value = "Get the CSV dataset metadata attached to a practical exercise submodule")
    @ApiImplicitParams({
            @ApiImplicitParam(name = Constants.AUTH, value = Constants.TOKEN_TYPE, required = true,
                    dataType = Constants.AUTH_DATA_TYPE, paramType = Constants.AUTH_PARAM_TYPE)
    })
    @GetMapping("/{moduleId}/{idx}/dataset")
    public ResponseEntity<?> get(@PathVariable String moduleId, @PathVariable int idx, HttpServletRequest request) {
        try {
            assertCurriculumPermission(request, AdminPermissionAction.READ);
            return ResponseEntity.ok(new ApiResponse<>(200, datasetService.getForSubmodule(moduleId, idx)));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(404, "NOT_FOUND", e.getMessage()));
        }
    }

    @ApiOperation(value = "Remove the CSV dataset from a practical exercise submodule")
    @ApiImplicitParams({
            @ApiImplicitParam(name = Constants.AUTH, value = Constants.TOKEN_TYPE, required = true,
                    dataType = Constants.AUTH_DATA_TYPE, paramType = Constants.AUTH_PARAM_TYPE)
    })
    @DeleteMapping("/{moduleId}/{idx}/dataset")
    public ResponseEntity<?> delete(@PathVariable String moduleId, @PathVariable int idx, HttpServletRequest request) {
        try {
            assertCurriculumPermission(request, AdminPermissionAction.DELETE);
            datasetService.delete(moduleId, idx);
            return ResponseEntity.ok(new ApiResponse<>(200, "Deleted"));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(404, "NOT_FOUND", e.getMessage()));
        }
    }

    private String assertCurriculumPermission(HttpServletRequest request, AdminPermissionAction action) {
        String userId = skillamaAuthSupport.resolveUserIdFromRequest(request);
        adminPermissionService.requirePermission(userId, AdminModule.CURRICULUM, action);
        return userId;
    }
}
