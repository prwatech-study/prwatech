package com.prwatech.skillama.controller;

import com.prwatech.authentication.security.JwtUtils;
import com.prwatech.common.Constants;
import com.prwatech.skillama.dto.AiCostEstimateDTO;
import com.prwatech.skillama.dto.ApiResponse;
import com.prwatech.skillama.dto.ErrorResponse;
import com.prwatech.skillama.dto.GeneratedImageDTO;
import com.prwatech.skillama.dto.ImageCommitRequestDTO;
import com.prwatech.skillama.dto.ImageGenerateRequestDTO;
import com.prwatech.skillama.dto.ImageDeleteResponseDTO;
import com.prwatech.skillama.dto.ImageGenerateResponseDTO;
import com.prwatech.skillama.dto.ImageUploadResponseDTO;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.AdminModule;
import com.prwatech.skillama.model.AdminPermissionAction;
import com.prwatech.skillama.model.CourseCurriculum;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.service.AdminPermissionService;
import com.prwatech.skillama.service.AiUsageService;
import com.prwatech.skillama.service.CourseCurriculumService;
import com.prwatech.skillama.service.CourseService;
import com.prwatech.skillama.service.FileStorageService;
import com.prwatech.skillama.service.SkillamaAiClient;
import com.prwatech.skillama.service.UserService;
import com.prwatech.skillama.service.SkillamaAuthSupport;
import org.springframework.beans.factory.annotation.Value;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Base64;
import java.util.Optional;

/**
 * Admin Controller for Curriculum Image Upload Management
 * 
 * Note: The requirements document specifies endpoints with submoduleId, but the current
 * implementation uses moduleId + index. We'll use moduleId and index as path parameters.
 * 
 * Endpoints:
 * - POST /api/admin/curriculum/submodules/{moduleId}/{idx}/image - Upload image for specific submodule
 * - POST /api/admin/curriculum/submodules/image - Upload image (returns URL only)
 * - DELETE /api/admin/curriculum/submodules/{moduleId}/{idx}/image - Delete submodule image
 */
@RestController
@RequestMapping("/skillama/api/admin/curriculum/submodules")
@RequiredArgsConstructor
public class AdminCurriculumImageController {
    
    private final FileStorageService fileStorageService;
    private final CourseService courseService;
    private final CourseCurriculumService curriculumService;
    private final UserService userService;
    private final AdminPermissionService adminPermissionService;
    private final JwtUtils jwtUtils;
    private final SkillamaAuthSupport skillamaAuthSupport;
    private final SkillamaAiClient skillamaAiClient;
    private final AiUsageService aiUsageService;

    // Feature flag (defense-in-depth): the frontend hides the button, and the
    // backend refuses when off, so the endpoints are non-usable when disabled.
    @Value("${skillama.ai.image-generation.enabled:true}")
    private boolean aiImageGenerationEnabled;

    @Value("${skillama.ai.image-generation.daily-cap:3}")
    private int aiImageDailyCap;

    private static final String IMAGE_SUBDIRECTORY = "curriculum/images";
    
    /**
     * Upload Image for Submodule
     * POST /api/admin/curriculum/submodules/{moduleId}/{idx}/image
     */
    @ApiOperation(value = "Upload image for submodule", notes = "Uploads an image file for a specific curriculum submodule")
    @ApiResponses(value = {
            @io.swagger.annotations.ApiResponse(code = 200, message = "Image uploaded successfully"),
            @io.swagger.annotations.ApiResponse(code = 400, message = "Invalid file type or size"),
            @io.swagger.annotations.ApiResponse(code = 401, message = "Unauthorized"),
            @io.swagger.annotations.ApiResponse(code = 404, message = "Submodule not found"),
            @io.swagger.annotations.ApiResponse(code = 413, message = "File too large"),
            @io.swagger.annotations.ApiResponse(code = 500, message = "Internal server error")
    })
    @ApiImplicitParams({
            @ApiImplicitParam(
                    name = Constants.AUTH,
                    value = Constants.TOKEN_TYPE,
                    required = true,
                    dataType = Constants.AUTH_DATA_TYPE,
                    paramType = Constants.AUTH_PARAM_TYPE)
    })
    @PostMapping("/{moduleId}/{idx}/image")
    public ResponseEntity<?> uploadSubmoduleImage(
            @PathVariable String moduleId,
            @PathVariable int idx,
            @RequestParam("image") MultipartFile file,
            HttpServletRequest request) {
        try {
            assertCurriculumPermission(request, AdminPermissionAction.UPDATE);

            // Find module to get courseId and module order
            CourseCurriculum module = curriculumService.findById(moduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Module not found"));
            
            // Find submodule
            Optional<CourseCurriculum.Submodule> submoduleOpt = courseService.findSubmodule(moduleId, idx);
            if (submoduleOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(404, "SUBMODULE_NOT_FOUND", "Submodule not found"));
            }
            
            // Delete old image if exists
            CourseCurriculum.Submodule submodule = submoduleOpt.get();
            if (submodule.getImagePath() != null && !submodule.getImagePath().isEmpty()) {
                try {
                    fileStorageService.deleteFile(submodule.getImagePath());
                } catch (IOException e) {
                    // Log but don't fail - old file might not exist
                }
            }
            
            // Upload new image to S3 — use moduleId + submodule index (order fields can collide).
            String imagePath = fileStorageService.uploadImageForSubmoduleById(
                file,
                module.getCourseId(),
                moduleId,
                idx,
                1
            );
            
            // Update submodule
            courseService.updateSubmoduleImagePath(moduleId, idx, imagePath);
            
            // Build response
            ImageUploadResponseDTO response = ImageUploadResponseDTO.builder()
                .moduleId(moduleId)
                .submoduleIndex(idx)
                .imagePath(imagePath)
                .imageUrl(imagePath)
                .fileName(extractFileName(imagePath))
                .fileSize(file.getSize())
                .contentType(file.getContentType())
                .build();
            
            return ResponseEntity.ok(new ApiResponse<>(200, response));
            
        } catch (IllegalArgumentException e) {
            // File validation errors
            if (e.getMessage().contains("File size")) {
                return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(new ErrorResponse(413, "FILE_TOO_LARGE", e.getMessage()));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(400, "INVALID_FILE_TYPE", e.getMessage()));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(404, "SUBMODULE_NOT_FOUND", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(500, "UPLOAD_ERROR", "Failed to upload image: " + e.getMessage()));
        } catch (RuntimeException e) {
            return curriculumPermissionError(e);
        }
    }
    
    /**
     * Upload Image (Returns URL Only)
     * POST /api/admin/curriculum/submodules/image
     */
    @ApiOperation(value = "Upload image", notes = "Uploads an image file and returns the image URL. Can be used when creating or updating a submodule.")
    @ApiResponses(value = {
            @io.swagger.annotations.ApiResponse(code = 200, message = "Image uploaded successfully"),
            @io.swagger.annotations.ApiResponse(code = 400, message = "Invalid file type or size"),
            @io.swagger.annotations.ApiResponse(code = 401, message = "Unauthorized"),
            @io.swagger.annotations.ApiResponse(code = 413, message = "File too large"),
            @io.swagger.annotations.ApiResponse(code = 500, message = "Internal server error")
    })
    @ApiImplicitParams({
            @ApiImplicitParam(
                    name = Constants.AUTH,
                    value = Constants.TOKEN_TYPE,
                    required = true,
                    dataType = Constants.AUTH_DATA_TYPE,
                    paramType = Constants.AUTH_PARAM_TYPE)
    })
    @PostMapping("/image")
    public ResponseEntity<?> uploadImage(
            @RequestParam("image") MultipartFile file,
            HttpServletRequest request) {
        try {
            assertCurriculumPermission(request, AdminPermissionAction.UPDATE);

            // Generic curriculum image: upload to AWS S3 (curriculum/images prefix)
            // Same bucket as submodule images; UI can use this URL when saving submodule imagePath
            String imagePath = fileStorageService.uploadImageToS3(file, IMAGE_SUBDIRECTORY);
            
            // Build response
            ImageUploadResponseDTO response = ImageUploadResponseDTO.builder()
                .imagePath(imagePath)
                .imageUrl(imagePath)
                .fileName(extractFileName(imagePath))
                .fileSize(file.getSize())
                .contentType(file.getContentType())
                .build();
            
            return ResponseEntity.ok(new ApiResponse<>(200, response));
            
        } catch (IllegalArgumentException e) {
            // File validation errors
            if (e.getMessage().contains("File size")) {
                return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(new ErrorResponse(413, "FILE_TOO_LARGE", e.getMessage()));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(400, "INVALID_FILE_TYPE", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(500, "UPLOAD_ERROR", "Failed to upload image: " + e.getMessage()));
        } catch (RuntimeException e) {
            return curriculumPermissionError(e);
        }
    }
    
    /**
     * Delete Submodule Image
     * DELETE /api/admin/curriculum/submodules/{moduleId}/{idx}/image
     */
    @ApiOperation(value = "Delete submodule image", notes = "Deletes the image associated with a submodule")
    @ApiResponses(value = {
            @io.swagger.annotations.ApiResponse(code = 200, message = "Image deleted successfully"),
            @io.swagger.annotations.ApiResponse(code = 401, message = "Unauthorized"),
            @io.swagger.annotations.ApiResponse(code = 404, message = "Submodule or image not found"),
            @io.swagger.annotations.ApiResponse(code = 500, message = "Internal server error")
    })
    @ApiImplicitParams({
            @ApiImplicitParam(
                    name = Constants.AUTH,
                    value = Constants.TOKEN_TYPE,
                    required = true,
                    dataType = Constants.AUTH_DATA_TYPE,
                    paramType = Constants.AUTH_PARAM_TYPE)
    })
    @DeleteMapping("/{moduleId}/{idx}/image")
    public ResponseEntity<?> deleteSubmoduleImage(
            @PathVariable String moduleId,
            @PathVariable int idx,
            HttpServletRequest request) {
        try {
            assertCurriculumPermission(request, AdminPermissionAction.UPDATE);

            // Find submodule
            Optional<CourseCurriculum.Submodule> submoduleOpt = courseService.findSubmodule(moduleId, idx);
            if (submoduleOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(404, "SUBMODULE_NOT_FOUND", "Submodule not found"));
            }
            
            CourseCurriculum.Submodule submodule = submoduleOpt.get();
            String imagePath = submodule.getImagePath();
            
            // Check if image exists
            if (imagePath == null || imagePath.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(404, "IMAGE_NOT_FOUND", "Image not found for this submodule"));
            }
            
            // Delete file
            try {
                fileStorageService.deleteFile(imagePath);
            } catch (IOException e) {
                // Log but continue - file might not exist on disk
            }
            
            // Update submodule to remove image path
            courseService.updateSubmoduleImagePath(moduleId, idx, null);
            
            // Build response
            ImageDeleteResponseDTO response = ImageDeleteResponseDTO.builder()
                .moduleId(moduleId)
                .submoduleIndex(idx)
                .imagePath(null)
                .build();
            
            return ResponseEntity.ok(new ApiResponse<>(200, response));
            
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(404, "SUBMODULE_NOT_FOUND", e.getMessage()));
        } catch (RuntimeException e) {
            return curriculumPermissionError(e);
        }
    }
    
    /**
     * Generate an AI diagram candidate for a submodule (preview only — NOT saved).
     * POST /api/admin/curriculum/submodules/{moduleId}/{idx}/image/generate
     *
     * Enforces the per-submodule daily generation cap server-side. The candidate is
     * returned base64 for the admin to preview; the admin picks one and commits it.
     */
    @ApiOperation(value = "Generate AI image candidate for submodule",
            notes = "Generates a Napkin-style diagram from the submodule script. Returns a preview candidate + cost; not persisted until committed. Capped per day.")
    @ApiImplicitParams({
            @ApiImplicitParam(name = Constants.AUTH, value = Constants.TOKEN_TYPE, required = true,
                    dataType = Constants.AUTH_DATA_TYPE, paramType = Constants.AUTH_PARAM_TYPE)
    })
    @PostMapping("/{moduleId}/{idx}/image/generate")
    public ResponseEntity<?> generateSubmoduleImage(
            @PathVariable String moduleId,
            @PathVariable int idx,
            @RequestBody(required = false) ImageGenerateRequestDTO genBody,
            HttpServletRequest request) {
        try {
            assertCurriculumPermission(request, AdminPermissionAction.UPDATE);

            if (!aiImageGenerationEnabled) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse(403, "FEATURE_DISABLED", "AI image generation is currently disabled."));
            }

            CourseCurriculum module = curriculumService.findById(moduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Module not found"));

            Optional<CourseCurriculum.Submodule> submoduleOpt = courseService.findSubmodule(moduleId, idx);
            if (submoduleOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(404, "SUBMODULE_NOT_FOUND", "Submodule not found"));
            }
            CourseCurriculum.Submodule submodule = submoduleOpt.get();

            // Daily cap (server-authoritative). Each generation is the paid step; committing is free.
            int usedToday = courseService.imageGenUsedToday(submodule);
            if (usedToday >= aiImageDailyCap) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(new ErrorResponse(429, "DAILY_LIMIT_REACHED",
                        "Today's limit of " + aiImageDailyCap + " image generations is reached — try again tomorrow, or reach out to the Tech Team."));
            }

            // Prefer the caller's (unsaved-modal) values so the image reflects what the admin sees.
            String label = (genBody != null && genBody.getLabel() != null && !genBody.getLabel().isBlank())
                ? genBody.getLabel() : submodule.getLabel();
            String scriptText = (genBody != null && genBody.getScriptText() != null && !genBody.getScriptText().isBlank())
                ? genBody.getScriptText() : submodule.getScriptText();
            if ((scriptText == null || scriptText.isBlank()) && (label == null || label.isBlank())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse(400, "NO_SOURCE_CONTENT", "This submodule has no script text or label to generate an image from."));
            }

            // Variant rotates with today's usage so each of the 3 tries looks different.
            int variant = usedToday;
            GeneratedImageDTO gen = skillamaAiClient.generateImage(label, module.getModuleName(), scriptText, variant);
            if (gen.getImageBase64() == null || gen.getImageBase64().isBlank()) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse(500, "GENERATION_FAILED", "The AI image service did not return an image."));
            }

            // Only count the try after a successful generation (failed calls are free).
            int newUsed = courseService.incrementImageGenCount(moduleId, idx);
            AiCostEstimateDTO cost = aiUsageService.estimateCost(gen.getModelId(), gen.getInputTokens(), gen.getOutputTokens());

            ImageGenerateResponseDTO response = ImageGenerateResponseDTO.builder()
                .moduleId(moduleId)
                .submoduleIndex(idx)
                .imageBase64(gen.getImageBase64())
                .contentType(gen.getContentType())
                .diagramType(gen.getDiagramType())
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
                .body(new ErrorResponse(404, "SUBMODULE_NOT_FOUND", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ErrorResponse(502, "AI_SERVICE_ERROR", "AI image service is unavailable: " + e.getMessage()));
        } catch (RuntimeException e) {
            return curriculumPermissionError(e);
        }
    }

    /**
     * Commit a chosen AI-generated image candidate to the submodule's slide.
     * POST /api/admin/curriculum/submodules/{moduleId}/{idx}/image/commit
     */
    @ApiOperation(value = "Commit AI image candidate for submodule",
            notes = "Persists a chosen AI-generated candidate to S3 and the submodule imagePath.")
    @ApiImplicitParams({
            @ApiImplicitParam(name = Constants.AUTH, value = Constants.TOKEN_TYPE, required = true,
                    dataType = Constants.AUTH_DATA_TYPE, paramType = Constants.AUTH_PARAM_TYPE)
    })
    @PostMapping("/{moduleId}/{idx}/image/commit")
    public ResponseEntity<?> commitSubmoduleImage(
            @PathVariable String moduleId,
            @PathVariable int idx,
            @RequestBody ImageCommitRequestDTO body,
            HttpServletRequest request) {
        try {
            assertCurriculumPermission(request, AdminPermissionAction.UPDATE);

            if (!aiImageGenerationEnabled) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse(403, "FEATURE_DISABLED", "AI image generation is currently disabled."));
            }
            if (body == null || body.getImageBase64() == null || body.getImageBase64().isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse(400, "MISSING_IMAGE", "imageBase64 is required"));
            }

            CourseCurriculum module = curriculumService.findById(moduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Module not found"));

            Optional<CourseCurriculum.Submodule> submoduleOpt = courseService.findSubmodule(moduleId, idx);
            if (submoduleOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(404, "SUBMODULE_NOT_FOUND", "Submodule not found"));
            }
            CourseCurriculum.Submodule submodule = submoduleOpt.get();

            // Remove the previous image (manual or AI) before writing the new one.
            if (submodule.getImagePath() != null && !submodule.getImagePath().isEmpty()) {
                try {
                    fileStorageService.deleteFile(submodule.getImagePath());
                } catch (IOException e) {
                    // Old file may not exist — don't fail the commit.
                }
            }

            byte[] data = Base64.getDecoder().decode(body.getImageBase64().trim());
            String contentType = (body.getContentType() != null && !body.getContentType().isBlank())
                ? body.getContentType() : "image/svg+xml";

            String imagePath = fileStorageService.uploadGeneratedImageForSubmodule(
                data, module.getCourseId(), moduleId, idx, contentType);

            courseService.updateSubmoduleImagePath(moduleId, idx, imagePath);

            ImageUploadResponseDTO response = ImageUploadResponseDTO.builder()
                .moduleId(moduleId)
                .submoduleIndex(idx)
                .imagePath(imagePath)
                .imageUrl(imagePath)
                .fileName(extractFileName(imagePath))
                .fileSize((long) data.length)
                .contentType(contentType)
                .build();

            return ResponseEntity.ok(new ApiResponse<>(200, response));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(400, "INVALID_IMAGE_DATA", "Invalid image data: " + e.getMessage()));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(404, "SUBMODULE_NOT_FOUND", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(500, "UPLOAD_ERROR", "Failed to save image: " + e.getMessage()));
        } catch (RuntimeException e) {
            return curriculumPermissionError(e);
        }
    }

    private ResponseEntity<ErrorResponse> curriculumPermissionError(RuntimeException e) {
        String msg = e.getMessage() != null ? e.getMessage() : "";
        if (msg.contains("Insufficient permission")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse(403, "FORBIDDEN", msg));
        }
        if (msg.contains("Authorization") || msg.contains("Admin access")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse(401, "UNAUTHORIZED", msg));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse(400, "BAD_REQUEST", msg));
    }
    
    private void assertCurriculumPermission(HttpServletRequest request, AdminPermissionAction action) {
        String userId = extractUserIdFromRequest(request);
        adminPermissionService.requirePermission(userId, AdminModule.CURRICULUM, action);
    }
    
    /**
     * Extracts userId from JWT token in Authorization header
     */
    private String extractUserIdFromRequest(HttpServletRequest request) {
        return skillamaAuthSupport.resolveUserIdFromRequest(request);
    }
    
    /**
     * Extracts filename from a file path/URL
     */
    private String extractFileName(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return null;
        }
        int lastSlash = filePath.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < filePath.length() - 1) {
            return filePath.substring(lastSlash + 1);
        }
        return filePath;
    }
}

