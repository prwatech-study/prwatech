package com.prwatech.skillama.controller;

import com.prwatech.authentication.security.JwtUtils;
import com.prwatech.common.Constants;
import com.prwatech.skillama.dto.ApiResponse;
import com.prwatech.skillama.dto.ErrorResponse;
import com.prwatech.skillama.dto.ImageDeleteResponseDTO;
import com.prwatech.skillama.dto.ImageUploadResponseDTO;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.CourseCurriculum;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.service.CourseCurriculumService;
import com.prwatech.skillama.service.CourseService;
import com.prwatech.skillama.service.FileStorageService;
import com.prwatech.skillama.service.UserService;
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
    private final JwtUtils jwtUtils;
    
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
            // Verify admin access
            verifyAdminAccess(request);
            
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
            
            // Get module order and submodule order (default to 1 if null)
            Integer moduleOrder = module.getOrder() != null ? module.getOrder() : 1;
            Integer lessonOrder = submodule.getOrder() != null ? submodule.getOrder() : (idx + 1);
            Integer slideNumber = 1; // Default slide number
            
            // Upload new image to S3 with proper path structure
            String imagePath = fileStorageService.uploadImageForSubmodule(
                file, 
                module.getCourseId(), 
                moduleOrder, 
                lessonOrder, 
                slideNumber
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
            if (e.getMessage().contains("Authorization") || e.getMessage().contains("Unauthorized")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(401, "UNAUTHORIZED", e.getMessage()));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(400, "BAD_REQUEST", e.getMessage()));
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
            // Verify admin access
            verifyAdminAccess(request);
            
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
            if (e.getMessage().contains("Authorization") || e.getMessage().contains("Unauthorized")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(401, "UNAUTHORIZED", e.getMessage()));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(400, "BAD_REQUEST", e.getMessage()));
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
            // Verify admin access
            verifyAdminAccess(request);
            
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
            if (e.getMessage().contains("Authorization") || e.getMessage().contains("Unauthorized")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(401, "UNAUTHORIZED", e.getMessage()));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(400, "BAD_REQUEST", e.getMessage()));
        }
    }
    
    /**
     * Verifies that the user has ADMIN or OWNER role
     */
    private void verifyAdminAccess(HttpServletRequest request) {
        String userId = extractUserIdFromRequest(request);
        User user = userService.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        if (user.getRole() != User.UserRole.ADMIN && user.getRole() != User.UserRole.OWNER) {
            throw new RuntimeException("Access denied. ADMIN or OWNER role required.");
        }
    }
    
    /**
     * Extracts userId from JWT token in Authorization header
     */
    private String extractUserIdFromRequest(HttpServletRequest request) {
        final String requestTokenHeader = request.getHeader("Authorization");
        
        if (requestTokenHeader == null || !requestTokenHeader.startsWith("Bearer ")) {
            throw new RuntimeException("Authorization header missing or invalid");
        }
        
        String jwtToken = requestTokenHeader.substring(7);
        String email = jwtUtils.extractUsername(jwtToken);
        
        // Find user by email to get the MongoDB id
        User user = userService.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        return user.getId();
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

