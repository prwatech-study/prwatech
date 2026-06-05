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

        lmsThemeEventRepository.save(LmsThemeEvent.builder()
                .userId(userId)
                .userEmail(user.getEmail())
                .theme(theme)
                .previousTheme(previous)
                .source(request != null ? request.getSource() : null)
                .pagePath(request != null ? request.getPagePath() : null)
                .createdAt(IndiaTime.now())
                .build());

        user.setLmsThemePreference(theme);
        user.setUpdatedAt(IndiaTime.now());
        userRepository.save(user);
    }

    public LmsThemeStatsDTO getStats() {
        long classicSwitches = lmsThemeEventRepository.countByTheme(CLASSIC);
        long auroraSwitches = lmsThemeEventRepository.countByTheme(AURORA);
        long activeClassic = userRepository.countByLmsThemePreference(CLASSIC);
        long activeAurora = userRepository.countByLmsThemePreference(AURORA);

        return LmsThemeStatsDTO.builder()
                .classic(classicSwitches)
                .aurora(auroraSwitches)
                .totalSwitches(classicSwitches + auroraSwitches)
                .activeClassic(activeClassic)
                .activeAurora(activeAurora)
                .build();
    }

    private String normalizeTheme(String raw) {
        if (raw == null) return null;
        String t = raw.trim().toLowerCase();
        if (CLASSIC.equals(t) || AURORA.equals(t)) {
            return t;
        }
        return null;
    }
}
