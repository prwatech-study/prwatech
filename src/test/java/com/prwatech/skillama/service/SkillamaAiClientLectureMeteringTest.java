package com.prwatech.skillama.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prwatech.skillama.dto.GeneratedLectureDTO;
import com.prwatech.skillama.model.PlatformAiSettings;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.AiUsageEventRepository;
import com.prwatech.skillama.repository.PlatformAiSettingsRepository;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import com.prwatech.skillama.util.IndiaTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * End-to-end proof that a real generateLecture() call actually debits the user's tracked
 * AI wallet by the correct amount — the exact claim disputed in the "climbs during
 * generation, settles back down afterward" report: the mid-flight climb is a synthetic
 * frontend estimate (see creditBurnMath.js), capped at +$0.02 (2 credits); this test
 * verifies what actually lands in the backend is the real, much smaller, per-token cost,
 * correctly recorded — not that the credit "goes missing".
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SkillamaAiClientLectureMeteringTest {

    @Mock private AiUsageEventRepository aiUsageEventRepository;
    @Mock private PlatformAiSettingsRepository platformAiSettingsRepository;
    @Mock private com.prwatech.skillama.repository.PlatformEfficiencyAssumptionsRepository platformEfficiencyAssumptionsRepository;
    @Mock private SkillamaUserRepository userRepository;
    @Mock private UsdInrExchangeRateService usdInrExchangeRateService;
    @Mock private TimeWalletService timeWalletService;
    @Mock private RestTemplate restTemplate;

    private AiUsageService aiUsageService;
    private SkillamaAiClient client;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();

        aiUsageService = new AiUsageService(aiUsageEventRepository, platformAiSettingsRepository,
                platformEfficiencyAssumptionsRepository,
                userRepository, objectMapper, usdInrExchangeRateService, timeWalletService);
        aiUsageService.loadRateCard();
        when(usdInrExchangeRateService.getUsdToInrRate()).thenReturn(83.0);

        PlatformAiSettings settings = new PlatformAiSettings();
        settings.setAiUsageTrackingEnabled(true);
        settings.setPlatformMonthlyBudgetUsd(1000.0);
        settings.setFreemiumMonthlyBudgetUsdPerUser(0.5);
        when(platformAiSettingsRepository.findById(PlatformAiSettings.SINGLETON_ID))
                .thenReturn(Optional.of(settings));

        client = new SkillamaAiClient(aiUsageService, objectMapper);
        ReflectionTestUtils.setField(client, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(client, "aiBaseUrl", "https://ai.example.com");
    }

    private User freemiumUser(double usedUsdSoFar) {
        return User.builder().id("u1").email("u@x.com").role(User.UserRole.USER)
                .planTier(User.PlanTier.FREEMIUM)
                .aiCostPeriodStart(IndiaTime.now())
                .aiCostUsdThisPeriod(usedUsdSoFar)
                .build();
    }

    private void stubAiTutorResponse(int inputTokens, int outputTokens) {
        String json = "{\"data\":{\"lecture_text\":\"...\",\"audio_url\":null," +
                "\"subtitle_path\":null,\"model_id\":\"default\"," +
                "\"usage\":{\"inputTokens\":" + inputTokens + ",\"outputTokens\":" + outputTokens +
                ",\"totalTokens\":" + (inputTokens + outputTokens) + "}}}";
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(json, HttpStatus.OK));
    }

    @Test
    void generateLectureDebitsTheUsersWalletByTheRealComputedCost() {
        User user = freemiumUser(0.025); // "2.5 credits" already used this period
        when(userRepository.findById("u1")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(aiUsageEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // A realistic lecture generation: ~800 input / 400 output tokens.
        stubAiTutorResponse(800, 400);

        GeneratedLectureDTO result = client.generateLecture(user, "course1", "Intro", "Python");

        // default rate card: $0.0003/1k input, $0.0006/1k output
        double expectedCost = (800 / 1000.0) * 0.0003 + (400 / 1000.0) * 0.0006; // = 0.00048
        assertEquals(1200, result.getTotalTokens());
        assertEquals(0.025 + expectedCost, user.getAiCostUsdThisPeriod(), 1e-9);
        verify(aiUsageEventRepository).save(argThat(evt ->
                "lecture_generation".equals(evt.getEndpoint()) && evt.getCostUsd() > 0));
        verify(userRepository).save(user);
    }

    @Test
    void realLectureCostIsFarSmallerThanTheFrontendBurnAnimationCeiling() {
        User user = freemiumUser(0.025);
        when(userRepository.findById("u1")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(aiUsageEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Even a long, expensive lecture (3k input / 1.5k output tokens) costs far less
        // than the frontend's +$0.02 burn-animation ceiling (creditBurnMath.js).
        stubAiTutorResponse(3000, 1500);
        client.generateLecture(user, "course1", "Intro", "Python");

        double actualCost = user.getAiCostUsdThisPeriod() - 0.025;
        double burnAnimationCeilingUsd = 0.02;
        assertTrue(actualCost < burnAnimationCeilingUsd,
                "expected real cost (" + actualCost + ") to be far below the animation's estimate ceiling");
    }
}
