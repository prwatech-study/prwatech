package com.prwatech.skillama.service;

import com.prwatech.common.exception.ForbiddenException;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.PlatformReferralShareRepository;
import com.prwatech.skillama.repository.ReferralShareEventRepository;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReferralShareServiceTest {

    @Mock
    private PlatformReferralShareRepository configRepository;
    @Mock
    private ReferralShareEventRepository shareEventRepository;
    @Mock
    private SkillamaUserRepository userRepository;
    @Mock
    private FreemiumService freemiumService;

    @InjectMocks
    private ReferralShareService referralShareService;

    private User user(Boolean emailVerified) {
        return User.builder().id("u1").email("u@x.com").emailVerified(emailVerified).build();
    }

    @Test
    void formatMessageReplacesPlaceholders() {
        String out = ReferralShareService.formatMessage(
                "Code {code} at {link}",
                "SKILL-ABC",
                "https://skillama.co.in/login?referral=SKILL-ABC");
        assertTrue(out.contains("SKILL-ABC"));
        assertTrue(out.contains("https://skillama.co.in/login?referral=SKILL-ABC"));
    }

    @Test
    void buildReferralLinkUsesPublicUrl() {
        ReflectionTestUtils.setField(referralShareService, "publicAppUrl", "https://skillama.co.in");
        String link = ReferralShareService.formatMessage("{link}", "X", "https://skillama.co.in/login?referral=SKILL-TEST");
        assertTrue(link.contains("skillama.co.in"));
    }

    @Test
    void sharePayloadRejectsUnverifiedEmail() {
        when(userRepository.findById("u1")).thenReturn(Optional.of(user(false)));
        assertThrows(ForbiddenException.class, () -> referralShareService.getSharePayload("u1"));
    }

    @Test
    void sharePayloadRejectsNullEmailVerifiedFlag() {
        when(userRepository.findById("u1")).thenReturn(Optional.of(user(null)));
        assertThrows(ForbiddenException.class, () -> referralShareService.getSharePayload("u1"));
    }

    @Test
    void sharePayloadSucceedsForVerifiedUser() {
        ReflectionTestUtils.setField(referralShareService, "publicAppUrl", "https://skillama.co.in");
        when(userRepository.findById("u1")).thenReturn(Optional.of(user(true)));
        when(freemiumService.getReferralCode("u1")).thenReturn("SKILL-ABC");
        when(configRepository.findById(com.prwatech.skillama.model.PlatformReferralShare.SINGLETON_ID))
                .thenReturn(Optional.empty());

        Map<String, Object> payload = referralShareService.getSharePayload("u1");

        assertEquals("SKILL-ABC", payload.get("code"));
        assertEquals("https://skillama.co.in/login?referral=SKILL-ABC", payload.get("link"));
        assertTrue(((String) payload.get("shareMessage")).contains("SKILL-ABC"));
    }

    @Test
    void sharePayloadMissingUserThrowsNotFound() {
        when(userRepository.findById("ghost")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> referralShareService.getSharePayload("ghost"));
    }
}
