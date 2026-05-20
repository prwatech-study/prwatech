package com.prwatech.skillama.controller;

import com.prwatech.skillama.dto.ReferralShareConfigDTO;
import com.prwatech.skillama.service.ReferralShareService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/skillama/platform")
@RequiredArgsConstructor
public class PlatformReferralShareController {

    private final ReferralShareService referralShareService;

    @GetMapping("/referral-share")
    public ResponseEntity<ReferralShareConfigDTO> getReferralShareConfig() {
        return ResponseEntity.ok(referralShareService.getPublicConfig());
    }
}
