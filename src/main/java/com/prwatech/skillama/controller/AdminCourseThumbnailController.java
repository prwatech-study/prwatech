package com.prwatech.skillama.controller;

import com.prwatech.authentication.security.JwtUtils;
import com.prwatech.common.Constants;
import com.prwatech.skillama.dto.ApiResponse;
import com.prwatech.skillama.dto.ErrorResponse;
import com.prwatech.skillama.dto.ImageUploadResponseDTO;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.Course;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.service.CourseService;
import com.prwatech.skillama.service.FileStorageService;
import com.prwatech.skillama.service.UserService;
import com.prwatech.skillama.util.IndiaTime;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

@RestController
@RequestMapping("/skillama/api/admin/courses/{courseId}/thumbnail")
@RequiredArgsConstructor
public class AdminCourseThumbnailController {

    private final FileStorageService fileStorageService;
    private final CourseService courseService;
    private final UserService userService;
    private final JwtUtils jwtUtils;

    @ApiOperation(value = "Upload course thumbnail / social share image")
    @ApiImplicitParams({
            @ApiImplicitParam(name = Constants.AUTH, value = Constants.TOKEN_TYPE, required = true,
                    dataType = Constants.AUTH_DATA_TYPE, paramType = Constants.AUTH_PARAM_TYPE)
    })
    @PostMapping
    public ResponseEntity<?> uploadThumbnail(
            @PathVariable String courseId,
            @RequestParam("image") MultipartFile file,
            HttpServletRequest request) {
        try {
            String adminUserId = verifyAdminAccess(request);
            Course course = courseService.findActiveById(courseId)
                    .orElseThrow(() -> new ResourceNotFoundException("Course not found"));

            if (StringUtils.hasText(course.getThumbnail()) && fileStorageService.isManagedStorageUrl(course.getThumbnail())) {
                try {
                    fileStorageService.deleteFile(course.getThumbnail());
                } catch (IOException ignored) {
                    // best-effort
                }
            }

            String prefix = "courses/" + courseId + "/social";
            String imageUrl = fileStorageService.uploadImageToS3(file, prefix);

            course.setThumbnail(imageUrl);
            course.setUpdatedBy(adminUserId);
            course.setUpdatedAt(IndiaTime.now());
            courseService.update(courseId, course);

            ImageUploadResponseDTO response = ImageUploadResponseDTO.builder()
                    .imagePath(imageUrl)
                    .imageUrl(imageUrl)
                    .fileName(file.getOriginalFilename())
                    .fileSize(file.getSize())
                    .contentType(file.getContentType())
                    .build();

            return ResponseEntity.ok(new ApiResponse<>(200, response));
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().contains("File size")) {
                return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                        .body(new ErrorResponse(413, "FILE_TOO_LARGE", e.getMessage()));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse(400, "INVALID_FILE_TYPE", e.getMessage()));
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
