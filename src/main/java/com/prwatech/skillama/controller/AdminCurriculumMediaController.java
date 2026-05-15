package com.prwatech.skillama.controller;

import com.prwatech.authentication.security.JwtUtils;
import com.prwatech.skillama.dto.ApiResponse;
import com.prwatech.skillama.dto.ImageUploadResponseDTO;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.service.CurriculumMediaService;
import com.prwatech.skillama.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;

/**
 * Curriculum image upload — same S3 flow as demo video; matches frontend proxy paths.
 */
@RestController
@RequestMapping("/skillama/api/admin/curriculum")
@RequiredArgsConstructor
public class AdminCurriculumMediaController {

    private final CurriculumMediaService curriculumMediaService;
    private final UserService userService;
    private final JwtUtils jwtUtils;

    @PostMapping(value = "/submodules/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ImageUploadResponseDTO>> uploadImage(
            @RequestParam("image") MultipartFile image,
            HttpServletRequest request) {
        try {
            extractUserIdFromRequest(request);
            ImageUploadResponseDTO dto = curriculumMediaService.uploadImage(image);
            return ResponseEntity.ok(new ApiResponse<>(200, dto));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }

    @PostMapping(
            value = "/submodules/{moduleId}/{submoduleIndex}/image",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ImageUploadResponseDTO>> uploadSubmoduleImage(
            @PathVariable String moduleId,
            @PathVariable int submoduleIndex,
            @RequestParam("image") MultipartFile image,
            HttpServletRequest request) {
        try {
            extractUserIdFromRequest(request);
            ImageUploadResponseDTO dto =
                    curriculumMediaService.uploadSubmoduleImage(moduleId, submoduleIndex, image);
            return ResponseEntity.ok(new ApiResponse<>(200, dto));
        } catch (com.prwatech.skillama.exception.ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(404, null));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }

    @DeleteMapping("/submodules/{moduleId}/{submoduleIndex}/image")
    public ResponseEntity<ApiResponse<ImageUploadResponseDTO>> deleteSubmoduleImage(
            @PathVariable String moduleId,
            @PathVariable int submoduleIndex,
            HttpServletRequest request) {
        try {
            extractUserIdFromRequest(request);
            ImageUploadResponseDTO dto =
                    curriculumMediaService.deleteSubmoduleImage(moduleId, submoduleIndex);
            return ResponseEntity.ok(new ApiResponse<>(200, dto));
        } catch (com.prwatech.skillama.exception.ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(404, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }

    private String extractUserIdFromRequest(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("Unauthorized");
        }
        String email = jwtUtils.extractUsername(authHeader.substring(7));
        return userService.findByEmail(email)
                .map(User::getId)
                .orElseThrow(() -> new RuntimeException("Unauthorized"));
    }
}
