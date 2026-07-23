package com.prwatech.skillama.controller;

import com.prwatech.skillama.dto.IssueReportResponseDTO;
import com.prwatech.skillama.dto.ReportIssueRequestDTO;
import com.prwatech.skillama.service.FileStorageService;
import com.prwatech.skillama.service.IssueReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Map;

/**
 * Public endpoint for learners to report broken content / technical problems.
 */
@RestController
@RequestMapping("/skillama/issues")
@RequiredArgsConstructor
public class IssueReportController {

    private final IssueReportService issueReportService;
    private final FileStorageService fileStorageService;

    @PostMapping("/report")
    public ResponseEntity<IssueReportResponseDTO> report(
            @RequestBody ReportIssueRequestDTO body,
            HttpServletRequest request) {
        IssueReportResponseDTO dto = issueReportService.submit(body, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Upload a single support attachment (screenshot / PDF) to the support bucket.
     * Public like /report; returns the stored URL to include in the report body.
     */
    @PostMapping("/attachments")
    public ResponseEntity<Map<String, String>> uploadAttachment(
            @RequestParam("file") MultipartFile file) {
        try {
            String url = fileStorageService.uploadSupportAttachment(file);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("url", url));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Upload failed"));
        }
    }
}
