package com.prwatech.skillama.controller;

import com.prwatech.skillama.dto.DemoVideoDTO;
import com.prwatech.skillama.service.PlatformDemoVideoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public read of platform demo video config (no auth required for learners).
 */
@RestController
@RequestMapping("/skillama/platform")
@RequiredArgsConstructor
public class PlatformDemoVideoController {

    private final PlatformDemoVideoService platformDemoVideoService;

    @GetMapping("/demo-video")
    public ResponseEntity<DemoVideoDTO> getDemoVideo() {
        return ResponseEntity.ok(platformDemoVideoService.getPublicConfig());
    }
}
