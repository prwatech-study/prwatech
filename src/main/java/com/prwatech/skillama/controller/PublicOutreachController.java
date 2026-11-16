package com.prwatech.skillama.controller;

import com.prwatech.skillama.service.outreach.PrwatechOutreachService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Base64;
import java.util.Map;

@RestController
@RequestMapping("/skillama/public/outreach")
@RequiredArgsConstructor
public class PublicOutreachController {

    private static final byte[] TRANSPARENT_GIF = Base64.getDecoder().decode(
            "R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7");

    private final PrwatechOutreachService outreachService;

    @GetMapping("/c/{token}")
    public ResponseEntity<?> click(@PathVariable String token) {
        return outreachService.recordClickRedirect(token)
                .map(url -> ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build())
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @GetMapping({"/o/{token}", "/o/{token}.gif"})
    public ResponseEntity<byte[]> open(@PathVariable String token) {
        outreachService.recordOpen(token);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_GIF);
        headers.setCacheControl("no-store, no-cache, must-revalidate");
        return new ResponseEntity<>(TRANSPARENT_GIF, headers, HttpStatus.OK);
    }

    @GetMapping("/unsubscribe/{token}")
    public ResponseEntity<Map<String, String>> unsubscribe(@PathVariable String token) {
        boolean ok = outreachService.unsubscribe(token);
        if (!ok) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("status", "error", "message", "Invalid link"));
        }
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "You have been unsubscribed from Skillama invite emails."));
    }

    @GetMapping("/landing/{token}")
    public ResponseEntity<Map<String, String>> landing(@PathVariable String token) {
        outreachService.recordLanding(token);
        return ResponseEntity.ok(Map.of("status", "success"));
    }
}