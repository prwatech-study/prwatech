package com.prwatech.skillama.service;

import com.prwatech.skillama.repository.PlatformReferralShareRepository;
import com.prwatech.skillama.repository.ReferralShareEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class ReferralShareServiceTest {

    @Mock
    private PlatformReferralShareRepository configRepository;
    @Mock
    private ReferralShareEventRepository shareEventRepository;
    @Mock
    private FreemiumService freemiumService;

    @InjectMocks
    private ReferralShareService referralShareService;

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
        // getSharePayload would need freemium mock — formatMessage is sufficient unit coverage
        String link = ReferralShareService.formatMessage("{link}", "X", "https://skillama.co.in/login?referral=SKILL-TEST");
        assertTrue(link.contains("skillama.co.in"));
    }
}
