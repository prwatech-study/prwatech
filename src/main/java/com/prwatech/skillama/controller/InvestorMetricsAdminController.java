package com.prwatech.skillama.controller;

import com.prwatech.skillama.dto.ApiResponse;
import com.prwatech.skillama.dto.InvestorMetricsDTO;
import com.prwatech.skillama.service.AdminPermissionService;
import com.prwatech.skillama.service.InvestorMetricsService;
import com.prwatech.skillama.service.SkillamaAuthSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

/** Investor-facing measured metrics rollup. OWNER only. */
@RestController
@RequestMapping("/skillama/api/admin/investor-metrics")
@RequiredArgsConstructor
public class InvestorMetricsAdminController {

    private final InvestorMetricsService investorMetricsService;
    private final AdminPermissionService adminPermissionService;
    private final SkillamaAuthSupport skillamaAuthSupport;

    @GetMapping
    public ResponseEntity<ApiResponse<InvestorMetricsDTO>> getInvestorMetrics(HttpServletRequest request) {
        try {
            adminPermissionService.requireOwner(skillamaAuthSupport.resolveUserIdFromRequest(request));
            return ResponseEntity.ok(new ApiResponse<>(200, investorMetricsService.getInvestorMetrics()));
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("Owner access required")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse<>(403, null));
            }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }
}
