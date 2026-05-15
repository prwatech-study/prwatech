package com.prwatech.skillama.controller;

import com.prwatech.skillama.dto.SalesInterestRequestDTO;
import com.prwatech.skillama.model.SalesLead;
import com.prwatech.skillama.service.SalesLeadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/skillama/leads")
@RequiredArgsConstructor
public class SalesLeadController {

    private final SalesLeadService salesLeadService;

    @PostMapping("/sales-interest")
    public ResponseEntity<?> submitSalesInterest(@RequestBody SalesInterestRequestDTO request) {
        try {
            SalesLead lead = salesLeadService.createLead(request);
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "id", lead.getId(),
                    "message", "Thank you. Our team will contact you if you opted in."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }
}
