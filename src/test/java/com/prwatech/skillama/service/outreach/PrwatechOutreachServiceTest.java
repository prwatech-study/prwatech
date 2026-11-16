package com.prwatech.skillama.service.outreach;

import com.prwatech.skillama.dto.OutreachDailyRowDTO;
import com.prwatech.skillama.dto.OutreachSummaryDTO;
import com.prwatech.skillama.dto.OutreachTemplatePreviewDTO;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.model.outreach.OutreachCampaign;
import com.prwatech.skillama.model.outreach.OutreachRecipient;
import com.prwatech.skillama.model.outreach.OutreachWaveDelivery;
import com.prwatech.skillama.repository.OutreachCampaignRepository;
import com.prwatech.skillama.repository.OutreachDailyRollupRepository;
import com.prwatech.skillama.repository.OutreachRecipientRepository;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import com.prwatech.skillama.service.UserContactService;
import com.prwatech.skillama.util.IndiaTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PrwatechOutreachServiceTest {

    @Mock private OutreachCampaignRepository campaignRepository;
    @Mock private OutreachRecipientRepository recipientRepository;
    @Mock private OutreachDailyRollupRepository rollupRepository;
    @Mock private SkillamaUserRepository userRepository;
    @Mock private UserContactService userContactService;
    @Mock private OutreachInviteMailer inviteMailer;
    @Mock private MongoTemplate skillamaMongoTemplate;
    @Mock private MongoTemplate mainMongoTemplate;

    private PrwatechOutreachService service;

    @BeforeEach
    void setUp() {
        service = new PrwatechOutreachService(
                campaignRepository,
                recipientRepository,
                rollupRepository,
                userRepository,
                userContactService,
                inviteMailer,
                skillamaMongoTemplate,
                mainMongoTemplate);
        ReflectionTestUtils.setField(service, "publicSiteUrl", "https://skillama.co.in");
        ReflectionTestUtils.setField(service, "apiBaseUrl", "https://prwatech.xyz");
    }

  private static String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : dig) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private OutreachRecipient activeRecipient(String email, String token) {
        String norm = email.trim().toLowerCase();
        OutreachRecipient r = OutreachRecipient.builder()
                .id("r1")
                .email(norm)
                .emailHash(sha256(norm))
                .trackingToken(token)
                .lifecycle(OutreachRecipient.Lifecycle.ACTIVE)
                .build();
        r.wave(1).setStatus(OutreachWaveDelivery.Status.SUBMITTED);
        return r;
    }

    @Test
    void applySignupAttribution_grantsBonusAndMarksRegistered() {
        when(userContactService.normalizeEmail("learner@example.com")).thenReturn("learner@example.com");
        when(recipientRepository.findByTrackingToken("tok1"))
                .thenReturn(Optional.of(activeRecipient("learner@example.com", "tok1")));
        when(campaignRepository.findById(OutreachCampaign.SINGLETON_ID))
                .thenReturn(Optional.of(OutreachCampaign.builder()
                        .id(OutreachCampaign.SINGLETON_ID)
                        .signupBonusUsd(1.0)
                        .currentWave(1)
                        .build()));
        when(rollupRepository.findByDateAndWave(any(), anyInt())).thenReturn(Optional.empty());
        when(rollupRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        User user = User.builder().id("u1").email("learner@example.com").build();

        service.applySignupAttribution(user, "tok1");

        assertEquals(1.0, user.getOutreachBonusUsd());
        verify(userRepository).save(user);
        verify(recipientRepository).save(argThat(r ->
                r.getLifecycle() == OutreachRecipient.Lifecycle.REGISTERED && r.isBonusGranted()));
    }

    @Test
    void applySignupAttribution_skipsWhenOptedOut() {
        OutreachRecipient r = activeRecipient("learner@example.com", "tok1");
        r.setLifecycle(OutreachRecipient.Lifecycle.OPTED_OUT);
        when(recipientRepository.findByTrackingToken("tok1")).thenReturn(Optional.of(r));

        User user = User.builder().id("u1").email("learner@example.com").build();
        service.applySignupAttribution(user, "tok1");

        assertTrue(user.getOutreachBonusUsd() == null || user.getOutreachBonusUsd() <= 0);
        verify(userRepository, never()).save(any());
        verify(recipientRepository, never()).save(any());
    }

    @Test
    void applySignupAttribution_skipsWhenEmailDoesNotMatchToken() {
        when(userContactService.normalizeEmail("other@example.com")).thenReturn("other@example.com");
        when(recipientRepository.findByTrackingToken("tok1"))
                .thenReturn(Optional.of(activeRecipient("learner@example.com", "tok1")));

        User user = User.builder().id("u1").email("other@example.com").build();
        service.applySignupAttribution(user, "tok1");

        assertTrue(user.getOutreachBonusUsd() == null || user.getOutreachBonusUsd() <= 0);
        verify(recipientRepository, never()).save(any());
    }

    @Test
    void applySignupAttribution_doesNotOverwriteExistingBonus() {
        when(userContactService.normalizeEmail("learner@example.com")).thenReturn("learner@example.com");
        when(recipientRepository.findByTrackingToken("tok1"))
                .thenReturn(Optional.of(activeRecipient("learner@example.com", "tok1")));
        when(campaignRepository.findById(OutreachCampaign.SINGLETON_ID))
                .thenReturn(Optional.of(OutreachCampaign.builder()
                        .id(OutreachCampaign.SINGLETON_ID)
                        .signupBonusUsd(1.0)
                        .currentWave(1)
                        .build()));
        when(rollupRepository.findByDateAndWave(any(), anyInt())).thenReturn(Optional.empty());
        when(rollupRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        User user = User.builder().id("u1").email("learner@example.com").outreachBonusUsd(1.0).build();

        service.applySignupAttribution(user, "tok1");

        assertEquals(1.0, user.getOutreachBonusUsd());
        verify(userRepository, never()).save(user);
        verify(recipientRepository).save(any());
    }

    @Test
    void unsubscribe_marksRecipientOptedOut() {
        when(recipientRepository.findByTrackingToken("tok1"))
                .thenReturn(Optional.of(activeRecipient("a@b.com", "tok1")));
        when(campaignRepository.findById(OutreachCampaign.SINGLETON_ID))
                .thenReturn(Optional.of(OutreachCampaign.builder()
                        .id(OutreachCampaign.SINGLETON_ID)
                        .currentWave(1)
                        .build()));
        when(rollupRepository.findByDateAndWave(any(), anyInt())).thenReturn(Optional.empty());
        when(rollupRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertTrue(service.unsubscribe("tok1"));
        verify(recipientRepository).save(argThat(r ->
                r.getLifecycle() == OutreachRecipient.Lifecycle.OPTED_OUT && r.getOptedOutAt() != null));
    }

    @Test
    void sync_skipsLegacyEmailAlreadyOnSkillama() {
        com.prwatech.user.model.User legacy = new com.prwatech.user.model.User();
        legacy.setId("leg1");
        legacy.setEmail("exists@skillama.test");
        legacy.setDisable(false);

        when(mainMongoTemplate.find(any(Query.class), eq(com.prwatech.user.model.User.class)))
                .thenReturn(List.of(legacy));
        when(campaignRepository.findById(OutreachCampaign.SINGLETON_ID))
                .thenReturn(Optional.of(OutreachCampaign.builder()
                        .id(OutreachCampaign.SINGLETON_ID)
                        .build()));
        when(userContactService.normalizeEmail("exists@skillama.test")).thenReturn("exists@skillama.test");
        when(userRepository.findByEmail("exists@skillama.test"))
                .thenReturn(Optional.of(User.builder().id("u1").build()));

        PrwatechOutreachService.SyncResult result = service.syncLegacyRecipients();

        assertEquals(1, result.eligibleLegacy());
        assertEquals(0, result.queuedNew());
        verify(recipientRepository, never()).save(any());
        verify(campaignRepository).save(argThat(c -> Long.valueOf(1).equals(c.getSyncExcludedOnSkillama())));
    }

    @Test
    void getSummary_exposesOnlyAggregateMetrics_noRecipientIdentityFields() {
        when(campaignRepository.findById(OutreachCampaign.SINGLETON_ID))
                .thenReturn(Optional.of(OutreachCampaign.builder()
                        .id(OutreachCampaign.SINGLETON_ID)
                        .status(OutreachCampaign.Status.DRAFT)
                        .currentWave(1)
                        .maxWaves(3)
                        .dailyBatchSize(500)
                        .build()));
        when(recipientRepository.count()).thenReturn(10L);
        when(recipientRepository.countByLifecycle(any())).thenReturn(0L);
        when(skillamaMongoTemplate.count(any(Query.class), eq(OutreachRecipient.class))).thenReturn(0L);
        when(rollupRepository.findByDateBetweenOrderByDateAsc(any(), any()))
                .thenReturn(Collections.emptyList());

        OutreachSummaryDTO summary = service.getSummary();

        assertNotNull(summary);
        assertEquals("DRAFT", summary.getCampaignStatus());
        assertPiiSafeDto(OutreachSummaryDTO.class);
        assertPiiSafeDto(OutreachDailyRowDTO.class);
        assertPiiSafeDto(OutreachTemplatePreviewDTO.class);
    }

    private static final Set<String> ALLOWED_EMAIL_NAMED_METRICS = Set.of(
            "excludedinvalidemail",
            "excludedalreadyonskillama");

    private static void assertPiiSafeDto(Class<?> type) {
        for (Field field : type.getDeclaredFields()) {
            String name = field.getName().toLowerCase();
            boolean mentionsEmail = name.contains("email");
            if (mentionsEmail) {
                assertTrue(
                        ALLOWED_EMAIL_NAMED_METRICS.contains(name),
                        () -> "Unexpected email-related field on admin summary DTO: " + type.getSimpleName() + "." + field.getName());
            }
            assertFalse(
                    name.equals("legacyprwatechuserid") || name.equals("displayname"),
                    () -> "Unexpected recipient identity field: " + field.getName());
            assertFalse(name.contains("phone"), () -> "Unexpected phone field: " + field.getName());
            assertFalse(name.equals("name") || name.equals("username"), () -> "Unexpected name field: " + field.getName());
        }
    }
}