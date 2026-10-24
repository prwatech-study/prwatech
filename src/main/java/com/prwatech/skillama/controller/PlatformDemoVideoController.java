package com.prwatech.skillama.controller;

import com.prwatech.skillama.dto.AiSettingsDTO;
import com.prwatech.skillama.dto.DemoVideoDTO;
import com.prwatech.skillama.dto.FreemiumOfferingDTO;
import com.prwatech.skillama.dto.PlatformFeatureRolloutDTO;
import com.prwatech.skillama.dto.PlatformThemeSettingsDTO;
import com.prwatech.skillama.dto.PublicStatsDTO;
import com.prwatech.skillama.dto.UpgradeContactDTO;
import com.prwatech.skillama.exception.SkillamaAuthException;
import com.prwatech.skillama.service.FreemiumService;
import com.prwatech.skillama.service.PlatformAiSettingsService;
import com.prwatech.skillama.service.PlatformDemoVideoService;
import com.prwatech.skillama.service.PlatformFeatureRolloutService;
import com.prwatech.skillama.service.PlatformPublicStatsService;
import com.prwatech.skillama.service.PlatformThemeSettingsService;
import com.prwatech.skillama.service.SkillamaAuthSupport;
import com.prwatech.skillama.service.SkillamaPlatformConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * Public read of platform demo video config (no auth required for learners).
 */
@RestController
@RequestMapping("/skillama/platform")
@RequiredArgsConstructor
public class PlatformDemoVideoController {

    private final PlatformDemoVideoService platformDemoVideoService;
    private final SkillamaPlatformConfigService platformConfigService;
    private final PlatformAiSettingsService platformAiSettingsService;
    private final PlatformThemeSettingsService platformThemeSettingsService;
    private final PlatformFeatureRolloutService platformFeatureRolloutService;
    private final FreemiumService freemiumService;
    private final PlatformPublicStatsService platformPublicStatsService;
    private final SkillamaAuthSupport skillamaAuthSupport;

    @GetMapping("/demo-video")
    public ResponseEntity<DemoVideoDTO> getDemoVideo() {
        return ResponseEntity.ok(platformDemoVideoService.getPublicConfig());
    }

    /** Public config for freemium upgrade CTA (contact email + message). */
    @GetMapping("/upgrade-contact")
    public ResponseEntity<UpgradeContactDTO> getUpgradeContact() {
        return ResponseEntity.ok(platformConfigService.getUpgradeContact());
    }

    /** Public freemium limits & modules — same source as LMS enforcement. */
    @GetMapping("/freemium-offering")
    public ResponseEntity<FreemiumOfferingDTO> getFreemiumOffering() {
        return ResponseEntity.ok(freemiumService.getPublicOffering());
    }

    /** Public AI routing flag — when true, LMS calls dev-ai with tutot API signatures. */
    @GetMapping("/ai-settings")
    public ResponseEntity<AiSettingsDTO> getAiSettings() {
        return ResponseEntity.ok(platformAiSettingsService.getPublicSettings());
    }

    /** Public theme catalog — which built-in themes the owner has allowed. */
    @GetMapping("/theme-settings")
    public ResponseEntity<PlatformThemeSettingsDTO> getThemeSettings() {
        return ResponseEntity.ok(platformThemeSettingsService.getPublicSettings());
    }

    /**
     * Learner-visible platform features with rollout status (Upcoming / Live / New).
     * When Authorization is present, Mock pilots and AI Interview assignees see LIVE
     * for those codes only; anonymous callers get the global status.
     */
    @GetMapping("/features")
    public ResponseEntity<List<PlatformFeatureRolloutDTO>> getPlatformFeatures(
            HttpServletRequest request) {
        String viewerUserId = null;
        try {
            viewerUserId = skillamaAuthSupport.resolveUserIdFromRequest(request);
        } catch (SkillamaAuthException ignored) {
            // Public catalog — ignore missing/invalid token.
        } catch (RuntimeException ignored) {
            // Same — do not fail the public endpoint on auth noise.
        }
        return ResponseEntity.ok(platformFeatureRolloutService.listLearnerCatalog(viewerUserId));
    }

    /** Public marketing aggregates for homepage social proof (cached). */
    @GetMapping("/public-stats")
    public ResponseEntity<PublicStatsDTO> getPublicStats() {
        return ResponseEntity.ok(platformPublicStatsService.getPublicStats());
    }
}
