package com.prwatech.skillama.controller;

import com.prwatech.skillama.dto.ApiResponse;
import com.prwatech.skillama.dto.OrgHostResolveDTO;
import com.prwatech.skillama.dto.OrgPublicBrandingDTO;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.service.OrganizationService;
import com.prwatech.skillama.service.PublicRateLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

/**
 * Unauthenticated tenant endpoints backing the branded login page.
 *
 * <p>Both answer differently for known and unknown tenants, which makes them enumeration
 * oracles by design — the branded page cannot render without them. Rate limiting is the
 * mitigation, so the slug/host dictionary attack is expensive rather than free.
 */
@RestController
@RequestMapping("/skillama/platform/org")
@RequiredArgsConstructor
public class PlatformOrgController {

    private final OrganizationService organizationService;
    private final PublicRateLimiter publicRateLimiter;

    /** Per caller IP; a branded login page load needs only a couple of requests. */
    @Value("${skillama.ratelimit.org-branding-per-minute:60}")
    private int brandingPermitsPerMinute;

    /**
     * Called server-side by the frontend proxy, so every replica's lookups share one source
     * address. Set well above the branding limit; the proxy also caches per host.
     */
    @Value("${skillama.ratelimit.org-resolve-per-minute:300}")
    private int resolvePermitsPerMinute;

    @GetMapping("/{slug}/branding")
    public ResponseEntity<ApiResponse<OrgPublicBrandingDTO>> getBranding(
            @PathVariable String slug, HttpServletRequest request) {
        if (!allow(request, "branding", brandingPermitsPerMinute)) {
            return tooManyRequests();
        }
        return ResponseEntity.ok(new ApiResponse<>(200, organizationService.getPublicBranding(slug)));
    }

    @GetMapping("/discover")
    public ResponseEntity<ApiResponse<OrgHostResolveDTO>> discoverByEmail(
            @RequestParam String email, HttpServletRequest request) {
        if (!allow(request, "discover", brandingPermitsPerMinute)) {
            return tooManyRequests();
        }
        return organizationService.discoverByWorkEmail(email)
                .map(dto -> ResponseEntity.ok(new ApiResponse<>(200, dto)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ApiResponse<>(404, null)));
    }

    @GetMapping("/resolve")
    public ResponseEntity<ApiResponse<OrgHostResolveDTO>> resolveHost(
            @RequestParam String host, HttpServletRequest request) {
        if (!allow(request, "resolve", resolvePermitsPerMinute)) {
            return tooManyRequests();
        }
        try {
            return ResponseEntity.ok(new ApiResponse<>(200, organizationService.resolveHost(host)));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(404, null));
        }
    }

    private boolean allow(HttpServletRequest request, String scope, int permitsPerMinute) {
        return publicRateLimiter.tryAcquire(
                scope + ":" + PublicRateLimiter.callerKey(request), permitsPerMinute);
    }

    private static <T> ResponseEntity<ApiResponse<T>> tooManyRequests() {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", "60")
                .body(new ApiResponse<>(429, null));
    }
}
