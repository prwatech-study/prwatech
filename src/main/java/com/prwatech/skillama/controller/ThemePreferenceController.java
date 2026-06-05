package com.prwatech.skillama.controller;

import com.prwatech.skillama.dto.LmsThemeSwitchRequestDTO;
import com.prwatech.skillama.service.LmsThemeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/skillama/public")
@RequiredArgsConstructor
public class ThemePreferenceController {

    private final LmsThemeService lmsThemeService;

    @PostMapping("/theme-preference")
    public ResponseEntity<?> recordThemePreference(@RequestBody LmsThemeSwitchRequestDTO request) {
        try {
            lmsThemeService.recordVisitorThemeSwitch(request);
            return ResponseEntity.ok(Map.of("status", "success"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }
}
