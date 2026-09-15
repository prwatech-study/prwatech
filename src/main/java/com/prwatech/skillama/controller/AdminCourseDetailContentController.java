package com.prwatech.skillama.controller;

import com.prwatech.common.Constants;
import com.prwatech.skillama.dto.ApiResponse;
import com.prwatech.skillama.dto.ErrorResponse;
import com.prwatech.skillama.dto.GeneratedCourseDetailDTO;
import com.prwatech.skillama.exception.AiBudgetLimitException;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.service.CourseDetailContentService;
import com.prwatech.skillama.service.SkillamaAuthSupport;
import com.prwatech.skillama.service.UserService;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

/**
 * Admin draft of public course-detail copy. Reuses GET curriculum internally;
 * persist is the existing course PUT.
 */
@RestController
@RequestMapping("/skillama/api/admin/courses/{courseId}/detail-content")
@RequiredArgsConstructor
public class AdminCourseDetailContentController {

    private final CourseDetailContentService courseDetailContentService;
    private final UserService userService;
    private final SkillamaAuthSupport skillamaAuthSupport;

    @ApiOperation(value = "Generate AI course-detail copy",
            notes = "Generates overview, objectives, key topics, prerequisites, outcomes, and AI Tutor help from the curriculum, persists them with the outline hash, and returns the saved copy. View Details (GET .../share) does the same lazily.")
    @ApiImplicitParams({
            @ApiImplicitParam(name = Constants.AUTH, value = Constants.TOKEN_TYPE, required = true,
                    dataType = Constants.AUTH_DATA_TYPE, paramType = Constants.AUTH_PARAM_TYPE)
    })
    @PostMapping("/generate")
    public ResponseEntity<?> generate(@PathVariable String courseId, HttpServletRequest request) {
        try {
            String adminUserId = verifyAdminAccess(request);
            GeneratedCourseDetailDTO draft = courseDetailContentService.generateAndSave(adminUserId, courseId);
            return ResponseEntity.ok(new ApiResponse<>(200, draft));
        } catch (AiBudgetLimitException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(e.toResponseBody());
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(404, "NOT_FOUND", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse(400, "BAD_REQUEST", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(new ErrorResponse(502, "AI_SERVICE_ERROR", e.getMessage()));
        } catch (RuntimeException e) {
            if (e.getMessage() != null
                    && (e.getMessage().contains("Authorization") || e.getMessage().contains("Unauthorized")
                    || e.getMessage().contains("Access denied"))) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse(401, "UNAUTHORIZED", e.getMessage()));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse(400, "BAD_REQUEST", e.getMessage()));
        }
    }

    private String verifyAdminAccess(HttpServletRequest request) {
        String userId = skillamaAuthSupport.resolveUserIdFromRequest(request);
        User user = userService.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (user.getRole() != User.UserRole.ADMIN
                && user.getRole() != User.UserRole.OWNER
                && user.getRole() != User.UserRole.TESTER) {
            throw new RuntimeException("Access denied. ADMIN, OWNER, or TESTER role required.");
        }
        return userId;
    }
}
