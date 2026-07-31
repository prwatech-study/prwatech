package com.prwatech.skillama.controller;

import com.prwatech.authentication.security.JwtUtils;
import com.prwatech.common.Constants;
import com.prwatech.skillama.dto.AiCostEstimateDTO;
import com.prwatech.skillama.dto.AiUsageRecordRequestDTO;
import com.prwatech.skillama.dto.ApiResponse;
import com.prwatech.skillama.dto.ErrorResponse;
import com.prwatech.skillama.dto.GeneratedImageDTO;
import com.prwatech.skillama.dto.ImageCommitRequestDTO;
import com.prwatech.skillama.dto.ImageUploadResponseDTO;
import com.prwatech.skillama.dto.ThumbnailGenerateRequestDTO;
import com.prwatech.skillama.dto.ThumbnailGenerateResponseDTO;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.Course;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.service.AiUsageService;
import com.prwatech.skillama.service.CourseService;
import com.prwatech.skillama.service.FileStorageService;
import com.prwatech.skillama.service.SkillamaAiClient;
import com.prwatech.skillama.service.UserService;
import com.prwatech.skillama.service.SkillamaAuthSupport;
import com.prwatech.skillama.util.IndiaTime;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Base64;

@RestController
@RequestMapping("/skillama/api/admin/courses/{courseId}/thumbnail")
@RequiredArgsConstructor
public class AdminCourseThumbnailController {

    private final FileStorageService fileStorageService;
    private final CourseService courseService;
    private final UserService userService;
    private final JwtUtils jwtUtils;
    private final SkillamaAuthSupport skillamaAuthSupport;
    private final SkillamaAiClient skillamaAiClient;
    private final AiUsageService aiUsageService;

    // Shares the curriculum image-generation flags so the feature is toggled/capped uniformly.
    @Value("${skillama.ai.image-generation.enabled:true}")
    private boolean aiImageGenerationEnabled;

    @Value("${skillama.ai.image-generation.daily-cap:3}")
    private int aiImageDailyCap;

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

    /**
     * Generate an AI thumbnail candidate for a course (preview only — NOT saved).
     * POST /api/admin/courses/{courseId}/thumbnail/generate
     *
     * Enforces the per-course daily generation cap server-side. The candidate is
     * returned base64 for the admin to preview; the admin picks one and commits it.
     * Mirrors the curriculum submodule image generator.
     */
    @ApiOperation(value = "Generate AI thumbnail candidate for a course",
            notes = "Generates a 16:9 thumbnail from the course name + description. Returns a preview candidate + cost; not persisted until committed. Capped per day.")
    @ApiImplicitParams({
            @ApiImplicitParam(name = Constants.AUTH, value = Constants.TOKEN_TYPE, required = true,
                    dataType = Constants.AUTH_DATA_TYPE, paramType = Constants.AUTH_PARAM_TYPE)
    })
    @PostMapping("/generate")
    public ResponseEntity<?> generateThumbnail(
            @PathVariable String courseId,
            @RequestBody(required = false) ThumbnailGenerateRequestDTO genBody,
            HttpServletRequest request) {
        try {
            String adminUserId = verifyAdminAccess(request);

            if (!aiImageGenerationEnabled) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse(403, "FEATURE_DISABLED", "AI image generation is currently disabled."));
            }

            Course course = courseService.findActiveById(courseId)
                    .orElseThrow(() -> new ResourceNotFoundException("Course not found"));

            // Daily cap (server-authoritative). Each generation is the paid step; committing is free.
            int usedToday = courseService.thumbnailGenUsedToday(course);
            if (usedToday >= aiImageDailyCap) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(new ErrorResponse(429, "DAILY_LIMIT_REACHED",
                        "Today's limit of " + aiImageDailyCap + " thumbnail generations is reached — try again tomorrow, or reach out to the Tech Team."));
            }

            // Prefer the caller's (unsaved-form) values so the image reflects what the admin sees.
            String name = (genBody != null && genBody.getName() != null && !genBody.getName().isBlank())
                ? genBody.getName() : course.getName();
            String description = (genBody != null && genBody.getDescription() != null && !genBody.getDescription().isBlank())
                ? genBody.getDescription() : course.getDescription();
            if ((name == null || name.isBlank()) && (description == null || description.isBlank())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse(400, "NO_SOURCE_CONTENT", "This course has no name or description to generate a thumbnail from."));
            }

            // Variant rotates with today's usage so each of the tries looks different.
            int variant = usedToday;
            GeneratedImageDTO gen = skillamaAiClient.generateThumbnail(name, description, variant);
            if (gen.getImageBase64() == null || gen.getImageBase64().isBlank()) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse(500, "GENERATION_FAILED", "The AI thumbnail service did not return an image."));
            }

            // Only count the try after a successful generation (failed calls are free).
            int newUsed = courseService.incrementThumbnailGenCount(courseId);
            AiCostEstimateDTO cost = aiUsageService.estimateCost(gen.getModelId(), gen.getInputTokens(), gen.getOutputTokens());

            // Persist usage so it accumulates on the AI-usage page (endpoint "generate_thumbnail").
            // Tracking failures must never break generation, so this is best-effort.
            try {
                aiUsageService.recordUsage(AiUsageRecordRequestDTO.builder()
                    .userId(adminUserId)
                    .courseId(courseId)
                    .endpoint("generate_thumbnail")
                    .modelId(gen.getModelId())
                    .inputTokens(gen.getInputTokens())
                    .outputTokens(gen.getOutputTokens())
                    .totalTokens(gen.getTotalTokens())
                    .build());
            } catch (Exception usageEx) {
                // swallow — cost was still shown to the admin; only the tracking write failed
            }

            ThumbnailGenerateResponseDTO response = ThumbnailGenerateResponseDTO.builder()
                .courseId(courseId)
                .imageBase64(gen.getImageBase64())
                .contentType(gen.getContentType())
                .title(gen.getTitle())
                .costUsd(cost.getCostUsd())
                .costInr(cost.getCostInr())
                .usdToInrRate(cost.getUsdToInrRate())
                .totalTokens(gen.getTotalTokens())
                .triesUsedToday(newUsed)
                .triesRemainingToday(Math.max(0, aiImageDailyCap - newUsed))
                .dailyCap(aiImageDailyCap)
                .build();

            return ResponseEntity.ok(new ApiResponse<>(200, response));

        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(404, "NOT_FOUND", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ErrorResponse(502, "AI_SERVICE_ERROR", "AI thumbnail service is unavailable: " + e.getMessage()));
        } catch (RuntimeException e) {
            return unauthorizedOrBadRequest(e);
        }
    }

    /**
     * Commit a chosen AI-generated thumbnail candidate to the course.
     * POST /api/admin/courses/{courseId}/thumbnail/commit
     */
    @ApiOperation(value = "Commit AI thumbnail candidate for a course",
            notes = "Persists a chosen AI-generated candidate to S3 and the course thumbnail field.")
    @ApiImplicitParams({
            @ApiImplicitParam(name = Constants.AUTH, value = Constants.TOKEN_TYPE, required = true,
                    dataType = Constants.AUTH_DATA_TYPE, paramType = Constants.AUTH_PARAM_TYPE)
    })
    @PostMapping("/commit")
    public ResponseEntity<?> commitThumbnail(
            @PathVariable String courseId,
            @RequestBody ImageCommitRequestDTO body,
            HttpServletRequest request) {
        try {
            String adminUserId = verifyAdminAccess(request);

            if (!aiImageGenerationEnabled) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse(403, "FEATURE_DISABLED", "AI image generation is currently disabled."));
            }
            if (body == null || body.getImageBase64() == null || body.getImageBase64().isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse(400, "MISSING_IMAGE", "imageBase64 is required"));
            }

            Course course = courseService.findActiveById(courseId)
                    .orElseThrow(() -> new ResourceNotFoundException("Course not found"));

            // Remove the previous thumbnail (manual or AI) before writing the new one.
            if (StringUtils.hasText(course.getThumbnail()) && fileStorageService.isManagedStorageUrl(course.getThumbnail())) {
                try {
                    fileStorageService.deleteFile(course.getThumbnail());
                } catch (IOException ignored) {
                    // Old file may not exist — don't fail the commit.
                }
            }

            byte[] data = Base64.getDecoder().decode(body.getImageBase64().trim());
            String contentType = (body.getContentType() != null && !body.getContentType().isBlank())
                ? body.getContentType() : "image/png";

            String imageUrl = fileStorageService.uploadGeneratedThumbnail(data, courseId, contentType);

            course.setThumbnail(imageUrl);
            course.setUpdatedBy(adminUserId);
            course.setUpdatedAt(IndiaTime.now());
            courseService.update(courseId, course);

            ImageUploadResponseDTO response = ImageUploadResponseDTO.builder()
                .imagePath(imageUrl)
                .imageUrl(imageUrl)
                .fileName(extractFileName(imageUrl))
                .fileSize((long) data.length)
                .contentType(contentType)
                .build();

            return ResponseEntity.ok(new ApiResponse<>(200, response));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(400, "INVALID_IMAGE_DATA", "Invalid image data: " + e.getMessage()));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(404, "NOT_FOUND", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(500, "UPLOAD_ERROR", "Failed to save thumbnail: " + e.getMessage()));
        } catch (RuntimeException e) {
            return unauthorizedOrBadRequest(e);
        }
    }

    private String extractFileName(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return null;
        }
        String clean = filePath.split("\\?")[0];
        int lastSlash = clean.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < clean.length() - 1) {
            return clean.substring(lastSlash + 1);
        }
        return clean;
    }

    private String verifyAdminAccess(HttpServletRequest request) {
        String userId = extractUserIdFromRequest(request);
        User user = userService.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (user.getRole() != User.UserRole.ADMIN
                && user.getRole() != User.UserRole.OWNER
                && user.getRole() != User.UserRole.TESTER) {
            throw new RuntimeException("Access denied. ADMIN, OWNER, or TESTER role required.");
        }
        return userId;
    }

    private String extractUserIdFromRequest(HttpServletRequest request) {
        return skillamaAuthSupport.resolveUserIdFromRequest(request);
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
