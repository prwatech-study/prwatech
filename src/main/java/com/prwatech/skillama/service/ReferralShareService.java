package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.ReferralShareConfigDTO;
import com.prwatech.skillama.dto.UpdateReferralShareConfigDTO;
import com.prwatech.skillama.model.PlatformReferralShare;
import com.prwatech.skillama.model.ReferralShareEvent;
import com.prwatech.skillama.repository.PlatformReferralShareRepository;
import com.prwatech.skillama.repository.ReferralShareEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import com.prwatech.skillama.util.IndiaTime;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReferralShareService {

    private static final String DEFAULT_TITLE = "Invite friends to Skillama";
    private static final String DEFAULT_MESSAGE =
            "Join me on Skillama — learn with an AI tutor! Use my referral code {code} when you sign up: {link}";

    private final PlatformReferralShareRepository configRepository;
    private final ReferralShareEventRepository shareEventRepository;
    private final FreemiumService freemiumService;

    @Value("${skillama.app.public-url:https://skillama.co.in}")
    private String publicAppUrl;

    public ReferralShareConfigDTO getPublicConfig() {
        PlatformReferralShare config = configRepository.findById(PlatformReferralShare.SINGLETON_ID)
                .orElse(null);
        if (config == null) {
            return ReferralShareConfigDTO.builder()
                    .title(DEFAULT_TITLE)
                    .shareMessage(DEFAULT_MESSAGE)
                    .build();
        }
        return toDto(config);
    }

    public ReferralShareConfigDTO updateConfig(UpdateReferralShareConfigDTO body, String adminUserId) {
        if (body == null || !StringUtils.hasText(body.getShareMessage())) {
            throw new IllegalArgumentException("shareMessage is required");
        }
        PlatformReferralShare config = configRepository.findById(PlatformReferralShare.SINGLETON_ID)
                .orElse(new PlatformReferralShare());
        config.setId(PlatformReferralShare.SINGLETON_ID);
        config.setTitle(StringUtils.hasText(body.getTitle()) ? body.getTitle().trim() : DEFAULT_TITLE);
        config.setShareMessage(body.getShareMessage().trim());
        config.setUpdatedAt(IndiaTime.now());
        config.setUpdatedBy(adminUserId);
        return toDto(configRepository.save(config));
    }

    public Map<String, Object> getSharePayload(String userId) {
        String code = freemiumService.getReferralCode(userId);
        String link = buildReferralLink(code);
        ReferralShareConfigDTO config = getPublicConfig();
        String message = formatMessage(config.getShareMessage(), code, link);
        return Map.of(
                "code", code,
                "link", link,
                "title", config.getTitle() != null ? config.getTitle() : DEFAULT_TITLE,
                "shareMessage", message,
                "rawTemplate", config.getShareMessage());
    }

    public void trackShare(String userId, String channel) {
        if (!StringUtils.hasText(channel)) {
            throw new IllegalArgumentException("channel is required");
        }
        String code = freemiumService.getReferralCode(userId);
        ReferralShareEvent event = new ReferralShareEvent();
        event.setUserId(userId);
        event.setReferralCode(code);
        event.setChannel(channel.trim().toUpperCase(Locale.ROOT));
        event.setCreatedAt(IndiaTime.now());
        shareEventRepository.save(event);
    }

    public static String formatMessage(String template, String code, String link) {
        String t = StringUtils.hasText(template) ? template : DEFAULT_MESSAGE;
        return t.replace("{code}", code != null ? code : "")
                .replace("{link}", link != null ? link : "");
    }

    private String buildReferralLink(String code) {
        String base = publicAppUrl != null ? publicAppUrl.trim() : "https://skillama.co.in";
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/login?referral=" + (code != null ? code : "");
    }

    private ReferralShareConfigDTO toDto(PlatformReferralShare config) {
        return ReferralShareConfigDTO.builder()
                .title(config.getTitle())
                .shareMessage(config.getShareMessage())
                .updatedAt(config.getUpdatedAt())
                .build();
    }
}
