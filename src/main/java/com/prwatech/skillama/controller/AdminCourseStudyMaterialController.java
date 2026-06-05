package com.prwatech.skillama.controller;

import com.prwatech.authentication.security.JwtUtils;
import com.prwatech.common.Constants;
import com.prwatech.skillama.dto.ApiResponse;
import com.prwatech.skillama.dto.ErrorResponse;
import com.prwatech.skillama.dto.StudyMaterialDTO;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.service.CourseStudyMaterialService;
import com.prwatech.skillama.service.UserService;
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
import java.util.List;

@RestController
@RequestMapping("/skillama/api/admin/courses/{courseId}/materials")
@RequiredArgsConstructor
public class AdminCourseStudyMaterialController {

    private final CourseStudyMaterialService studyMaterialService;
    private final UserService userService;
    private final JwtUtils jwtUtils;

    @ApiOperation(value = "List study materials for a course")
    @ApiImplicitParams({
            @ApiImplicitParam(name = Constants.AUTH, value = Constants.TOKEN_TYPE, required = true,
                    dataType = Constants.AUTH_DATA_TYPE, paramType = Constants.AUTH_PARAM_TYPE)
    })
    @GetMapping
    public ResponseEntity<?> list(@PathVariable String courseId, HttpServletRequest request) {
        try {
            verifyAdminAccess(request);
            List<StudyMaterialDTO> materials = studyMaterialService.listForCourse(courseId);
            return ResponseEntity.ok(new ApiResponse<>(200, materials));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(404, "NOT_FOUND", e.getMessage()));
        } catch (RuntimeException e) {
            return unauthorizedOrBadRequest(e);
        }
    }

    @ApiOperation(value = "Upload a study material file for a course")
    @ApiImplicitParams({
            @ApiImplicitParam(name = Constants.AUTH, value = Constants.TOKEN_TYPE, required = true,
                    dataType = Constants.AUTH_DATA_TYPE, paramType = Constants.AUTH_PARAM_TYPE)
    })
    @PostMapping
    public ResponseEntity<?> upload(
            @PathVariable String courseId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "sortOrder", required = false) Integer sortOrder,
            HttpServletRequest request) {
        try {
            String adminUserId = verifyAdminAccess(request);
            StudyMaterialDTO dto = studyMaterialService.upload(
                    courseId, file, title, description, sortOrder, adminUserId);
            return ResponseEntity.ok(new ApiResponse<>(200, dto));
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().contains("File size")) {
                return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                        .body(new ErrorResponse(413, "FILE_TOO_LARGE", e.getMessage()));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse(400, "INVALID_FILE", e.getMessage()));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(404, "NOT_FOUND", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse(500, "UPLOAD_ERROR", e.getMessage()));
        } catch (RuntimeException e) {
            return unauthorizedOrBadRequest(e);
        }
    }

    @ApiOperation(value = "Delete a study material")
    @ApiImplicitParams({
            @ApiImplicitParam(name = Constants.AUTH, value = Constants.TOKEN_TYPE, required = true,
                    dataType = Constants.AUTH_DATA_TYPE, paramType = Constants.AUTH_PARAM_TYPE)
    })
    @DeleteMapping("/{materialId}")
    public ResponseEntity<?> delete(
            @PathVariable String courseId,
            @PathVariable String materialId,
            HttpServletRequest request) {
        try {
            verifyAdminAccess(request);
            studyMaterialService.delete(courseId, materialId);
            return ResponseEntity.ok(new ApiResponse<>(200, "Deleted"));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(404, "NOT_FOUND", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse(500, "DELETE_ERROR", e.getMessage()));
        } catch (RuntimeException e) {
            return unauthorizedOrBadRequest(e);
        }
    }

    private String verifyAdminAccess(HttpServletRequest request) {
        String userId = extractUserIdFromRequest(request);
        User user = userService.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (user.getRole() != User.UserRole.ADMIN && user.getRole() != User.UserRole.OWNER) {
            throw new RuntimeException("Access denied. ADMIN or OWNER role required.");
        }
        return userId;
    }

    private String extractUserIdFromRequest(HttpServletRequest request) {
        final String requestTokenHeader = request.getHeader("Authorization");
        if (requestTokenHeader == null || !requestTokenHeader.startsWith("Bearer ")) {
            throw new RuntimeException("Authorization header missing or invalid");
        }
        String jwtToken = requestTokenHeader.substring(7);
        String email = jwtUtils.extractUsername(jwtToken);
        User user = userService.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return user.getId();
    }

    private ResponseEntity<?> unauthorizedOrBadRequest(RuntimeException e) {
        if (e.getMessage() != null
                && (e.getMessage().contains("Authorization") || e.getMessage().contains("Unauthorized"))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(401, "UNAUTHORIZED", e.getMessage()));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(400, "BAD_REQUEST", e.getMessage()));
    }
}
