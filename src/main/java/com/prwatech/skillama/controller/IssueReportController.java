package com.prwatech.skillama.controller;

import com.prwatech.skillama.dto.IssueReportResponseDTO;
import com.prwatech.skillama.dto.ReportIssueRequestDTO;
import com.prwatech.skillama.service.IssueReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

/**
 * Public endpoint for learners to report broken content / technical problems.
 */
@RestController
@RequestMapping("/skillama/issues")
@RequiredArgsConstructor
public class IssueReportController {

    private final IssueReportService issueReportService;

    @PostMapping("/report")
    public ResponseEntity<IssueReportResponseDTO> report(
            @RequestBody ReportIssueRequestDTO body,
            HttpServletRequest request) {
        IssueReportResponseDTO dto = issueReportService.submit(body, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }
}
