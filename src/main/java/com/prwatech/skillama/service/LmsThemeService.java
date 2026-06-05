package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.LmsThemeStatsDTO;
import com.prwatech.skillama.dto.LmsThemeSwitchRequestDTO;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.LmsThemeEvent;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.LmsThemeEventRepository;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import com.prwatech.skillama.util.IndiaTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LmsThemeService {

    private static final String CLASSIC = "classic";
    private static final String AURORA = "aurora";
    private static final String HOMEPAGE = "homepage";
    private static final String LMS = "lms";

    private final LmsThemeEventRepository lmsThemeEventRepository;
    private final SkillamaUserRepository userRepository;

    @Transactional
    public void recordThemeSwitch(String userId, LmsThemeSwitchRequestDTO request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String theme = normalizeTheme(request != null ? request.getTheme() : null);
        if (theme == null) {
            throw new IllegalArgumentException("theme must be classic or aurora");
        }

        String previous = request != null ? request.getPreviousTheme() : null;
        if (previous != null && previous.equals(theme)) {
            return;
        }

        String context = normalizeContext(request != null ? request.getContext() : null);
        if (context == null && request != null && request.getPagePath() != null) {
            context = inferContextFromPath(request.getPagePath());
        }
        if (context == null) {
            context = LMS;
        }

        lmsThemeEventRepository.save(buildEvent(theme, previous, request, userId, user.getEmail(), context, false));

        user.setLmsThemePreference(theme);
        user.setUpdatedAt(IndiaTime.now());
        userRepository.save(user);
    }

    @Transactional
    public void recordVisitorThemeSwitch(LmsThemeSwitchRequestDTO request) {
        String theme = normalizeTheme(request != null ? request.getTheme() : null);
        if (theme == null) {
            throw new IllegalArgumentException("theme must be classic or aurora");
        }

        String visitorId = request != null ? request.getVisitorId() : null;
        if (visitorId == null || visitorId.isBlank()) {
            throw new IllegalArgumentException("visitorId is required");
        }

        String context = normalizeContext(request != null ? request.getContext() : null);
        if (context == null && request != null && request.getPagePath() != null) {
            context = inferContextFromPath(request.getPagePath());
        }
        if (!HOMEPAGE.equals(context)) {
            throw new IllegalArgumentException("anonymous theme tracking is only allowed on homepage");
        }

        String previous = request != null ? request.getPreviousTheme() : null;
        if (previous != null && previous.equals(theme)) {
            return;
        }

        lmsThemeEventRepository.save(
                buildEvent(theme, previous, request, null, null, HOMEPAGE, true));
    }

    public LmsThemeStatsDTO getStats() {
        long classicSwitches = lmsThemeEventRepository.countByTheme(CLASSIC);
        long auroraSwitches = lmsThemeEventRepository.countByTheme(AURORA);
        long activeClassic = userRepository.countByLmsThemePreference(CLASSIC);
        long activeAurora = userRepository.countByLmsThemePreference(AURORA);

        long homepageClassic = lmsThemeEventRepository.countByThemeAndContext(CLASSIC, HOMEPAGE);
        long homepageAurora = lmsThemeEventRepository.countByThemeAndContext(AURORA, HOMEPAGE);
        long lmsClassic = lmsThemeEventRepository.countByThemeAndContext(CLASSIC, LMS);
        long lmsAurora = lmsThemeEventRepository.countByThemeAndContext(AURORA, LMS);
        long visitorClassic = lmsThemeEventRepository.countByThemeAndAnonymousTrue(CLASSIC);
        long visitorAurora = lmsThemeEventRepository.countByThemeAndAnonymousTrue(AURORA);

        return LmsThemeStatsDTO.builder()
                .classic(classicSwitches)
                .aurora(auroraSwitches)
                .totalSwitches(classicSwitches + auroraSwitches)
                .activeClassic(activeClassic)
                .activeAurora(activeAurora)
                .homepageClassic(homepageClassic)
                .homepageAurora(homepageAurora)
                .lmsClassic(lmsClassic)
                .lmsAurora(lmsAurora)
                .visitorClassic(visitorClassic)
                .visitorAurora(visitorAurora)
                .build();
    }

    private LmsThemeEvent buildEvent(
            String theme,
            String previous,
            LmsThemeSwitchRequestDTO request,
            String userId,
            String userEmail,
            String context,
            boolean anonymous) {
        return LmsThemeEvent.builder()
                .userId(userId)
                .userEmail(userEmail)
                .theme(theme)
                .previousTheme(previous)
                .source(request != null ? request.getSource() : null)
                .pagePath(request != null ? request.getPagePath() : null)
                .context(context)
                .visitorId(request != null ? request.getVisitorId() : null)
                .anonymous(anonymous)
                .createdAt(IndiaTime.now())
                .build();
    }

    private String inferContextFromPath(String pagePath) {
        if (pagePath == null) {
            return null;
        }
        String path = pagePath.trim();
        if ("/".equals(path) || path.startsWith("/courses/")) {
            return HOMEPAGE;
        }
        if (path.startsWith("/lms")) {
            return LMS;
        }
        return null;
    }

    private String normalizeContext(String raw) {
        if (raw == null) {
            return null;
        }
        String c = raw.trim().toLowerCase();
        if (HOMEPAGE.equals(c) || LMS.equals(c)) {
            return c;
        }
        return null;
    }

    private String normalizeTheme(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim().toLowerCase();
        if (CLASSIC.equals(t) || AURORA.equals(t)) {
            return t;
        }
        return null;
    }
}
