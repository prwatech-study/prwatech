package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.UpsertOnboardingEventRequest;
import com.prwatech.skillama.model.Course;
import com.prwatech.skillama.model.EventMembership;
import com.prwatech.skillama.model.OnboardingEvent;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.model.UserCourseEnrollment;
import com.prwatech.skillama.notification.NotificationEventType;
import com.prwatech.skillama.repository.CourseRepository;
import com.prwatech.skillama.repository.EventMembershipRepository;
import com.prwatech.skillama.repository.OnboardingEventRepository;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import com.prwatech.skillama.repository.UserCourseEnrollmentRepository;
import com.prwatech.skillama.util.IndiaTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OnboardingEventServiceTest {

    @Mock private OnboardingEventRepository eventRepository;
    @Mock private EventMembershipRepository membershipRepository;
    @Mock private SkillamaUserRepository userRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private UserCourseEnrollmentRepository enrollmentRepository;
    @Mock private UserCourseAccessService userCourseAccessService;
    @Mock private AdminAuditService adminAuditService;
    @Mock private NotificationSettingsService notificationSettingsService;
    @Mock private SkillamaHtmlMailer skillamaHtmlMailer;

    private OnboardingEventService service;
    private final AtomicReference<OnboardingEvent> stored = new AtomicReference<>();
    private final List<EventMembership> memberships = new ArrayList<>();
    private final Map<String, User> users = new HashMap<>();

    @BeforeEach
    void setUp() {
        service = new OnboardingEventService(eventRepository, membershipRepository, userRepository,
                courseRepository, enrollmentRepository, userCourseAccessService, adminAuditService,
                notificationSettingsService, skillamaHtmlMailer);
        when(eventRepository.save(any())).thenAnswer(inv -> {
            OnboardingEvent event = inv.getArgument(0);
            if (event.getId() == null) {
                event.setId("evt1");
            }
            stored.set(event);
            return event;
        });
        when(eventRepository.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(stored.get())
                .filter(event -> event.getId().equals(inv.getArgument(0))));
        when(eventRepository.findByReferralCode(anyString())).thenAnswer(inv -> {
            OnboardingEvent event = stored.get();
            if (event != null && inv.getArgument(0).equals(event.getReferralCode())) {
                return Optional.of(event);
            }
            return Optional.empty();
        });
        when(eventRepository.findAllByOrderByCreatedAtDesc()).thenAnswer(inv -> {
            OnboardingEvent event = stored.get();
            return event == null ? List.of() : List.of(event);
        });
        when(eventRepository.findByStatusNot(any())).thenAnswer(inv -> {
            OnboardingEvent event = stored.get();
            if (event == null || event.getStatus() == inv.getArgument(0)) {
                return List.of();
            }
            return List.of(event);
        });
        when(membershipRepository.save(any())).thenAnswer(inv -> {
            EventMembership membership = inv.getArgument(0);
            if (membership.getId() == null) {
                membership.setId("mem-" + memberships.size());
            }
            memberships.removeIf(existing -> existing.getUserId().equals(membership.getUserId()));
            memberships.add(membership);
            return membership;
        });
        when(membershipRepository.findByUserId(anyString())).thenAnswer(inv -> memberships.stream()
                .filter(membership -> membership.getUserId().equals(inv.getArgument(0)))
                .findFirst());
        when(membershipRepository.findByEventId(anyString())).thenAnswer(inv -> new ArrayList<>(memberships));
        when(membershipRepository.findByEventIdAndStatus(anyString(), any())).thenAnswer(inv -> memberships.stream()
                .filter(membership -> membership.getStatus() == inv.getArgument(1))
                .toList());
        when(membershipRepository.findByStatus(any())).thenAnswer(inv -> memberships.stream()
                .filter(membership -> membership.getStatus() == inv.getArgument(0))
                .toList());
        when(membershipRepository.countByEventId(anyString())).thenAnswer(inv -> (long) memberships.size());
        when(membershipRepository.countByEventIdAndStatus(anyString(), any())).thenAnswer(inv -> memberships.stream()
                .filter(membership -> membership.getStatus() == inv.getArgument(1))
                .count());
        when(userRepository.findByReferralCode(anyString())).thenReturn(Optional.empty());
        when(userRepository.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(users.get(inv.getArgument(0))));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(notificationSettingsService.isEnabled(any())).thenReturn(true);
        when(courseRepository.findById("c1")).thenReturn(Optional.of(course("c1", "Python", true)));
        when(courseRepository.findById("c2")).thenReturn(Optional.of(course("c2", "Java", true)));
        when(enrollmentRepository.findByUserId(anyString())).thenReturn(List.of());
        when(enrollmentRepository.findByUserIdAndCourseId(anyString(), anyString())).thenAnswer(inv -> {
            UserCourseEnrollment enrollment = new UserCourseEnrollment();
            enrollment.setUserId(inv.getArgument(0));
            enrollment.setCourseId(inv.getArgument(1));
            enrollment.setEnrollmentType(UserCourseEnrollment.EnrollmentType.EVENT);
            enrollment.setStatus(UserCourseEnrollment.EnrollmentStatus.ACTIVE);
            return Optional.of(enrollment);
        });
    }

    @Test
    void creditEventRejectsMissingZeroAndNegativeCredits() {
        assertThrows(IllegalArgumentException.class, () -> service.create(creditRequest(null), "admin"));
        assertThrows(IllegalArgumentException.class, () -> service.create(creditRequest(0.0), "admin"));
        assertThrows(IllegalArgumentException.class, () -> service.create(creditRequest(-5.0), "admin"));
    }

    @Test
    void timeEventRejectsMissingAndPastExpiry() {
        UpsertOnboardingEventRequest missing = timeRequest(null);
        assertThrows(IllegalArgumentException.class, () -> service.create(missing, "admin"));
        UpsertOnboardingEventRequest past = timeRequest(IndiaTime.now().minusDays(1));
        assertThrows(IllegalArgumentException.class, () -> service.create(past, "admin"));
    }

    @Test
    void rejectsCrossedModeFieldsAndExpiryBeforeOpen() {
        UpsertOnboardingEventRequest creditWithEnd = creditRequest(100.0);
        creditWithEnd.setEndsAt(IndiaTime.now().plusDays(10));
        assertThrows(IllegalArgumentException.class, () -> service.create(creditWithEnd, "admin"));

        UpsertOnboardingEventRequest timeWithCredits = timeRequest(IndiaTime.now().plusMonths(3));
        timeWithCredits.setCreditsPerStudent(10.0);
        assertThrows(IllegalArgumentException.class, () -> service.create(timeWithCredits, "admin"));

        UpsertOnboardingEventRequest earlyEnd = timeRequest(IndiaTime.now().plusDays(1));
        earlyEnd.setRegistrationOpensAt(IndiaTime.now().plusDays(2));
        assertThrows(IllegalArgumentException.class, () -> service.create(earlyEnd, "admin"));
    }

    @Test
    void rejectsEmptyUnknownInactiveCoursesAndBadFeatures() {
        UpsertOnboardingEventRequest empty = creditRequest(50.0);
        empty.setCourseIds(List.of());
        assertThrows(IllegalArgumentException.class, () -> service.create(empty, "admin"));

        UpsertOnboardingEventRequest unknown = creditRequest(50.0);
        unknown.setCourseIds(List.of("missing"));
        assertThrows(IllegalArgumentException.class, () -> service.create(unknown, "admin"));

        Course inactive = course("c1", "Python", false);
        when(courseRepository.findById("c1")).thenReturn(Optional.of(inactive));
        assertThrows(IllegalArgumentException.class, () -> service.create(creditRequest(50.0), "admin"));

        when(courseRepository.findById("c1")).thenReturn(Optional.of(course("c1", "Python", true)));
        UpsertOnboardingEventRequest feature = creditRequest(50.0);
        feature.setFeatureCodes(List.of("org_hierarchy"));
        assertThrows(IllegalArgumentException.class, () -> service.create(feature, "admin"));
    }

    @Test
    void omittedRegistrationOpensTheDayBeforeInIndia() {
        LocalDate eventDate = LocalDate.of(2026, 11, 15);
        UpsertOnboardingEventRequest request = creditRequest(80.0);
        request.setEventDate(eventDate);
        request.setRegistrationOpensAt(null);
        service.create(request, "admin");
        assertEquals(LocalDateTime.of(2026, 11, 14, 0, 0), stored.get().getRegistrationOpensAt());
        assertEquals(LocalDateTime.of(2026, 11, 13, 18, 30),
                OnboardingEventService.registrationOpensAtUtc(eventDate));
    }

    @Test
    void publishOnceAndKeepsTheSameCode() {
        service.create(creditRequest(40.0), "admin");
        assertNull(stored.get().getReferralCode());
        service.publish("evt1", "admin");
        String code = stored.get().getReferralCode();
        assertTrue(code.startsWith("EVT-"));
        service.publish("evt1", "admin");
        assertEquals(code, stored.get().getReferralCode());
        verify(adminAuditService, times(2)).log(eq("admin"), eq("EVENT_PUBLISH"), any(), eq("evt1"), any(), any());
    }

    @Test
    void codeGenerationSkipsATakenPersonalCode() {
        when(userRepository.findByReferralCode(anyString())).thenReturn(Optional.of(new User()), Optional.empty());
        service.create(creditRequest(40.0), "admin");
        service.publish("evt1", "admin");
        assertTrue(stored.get().getReferralCode().startsWith("EVT-"));
    }

    @Test
    void immutableFieldsAndEndedEditsAreRejected() {
        service.create(creditRequest(40.0), "admin");
        service.publish("evt1", "admin");
        double credits = stored.get().getCreditsPerStudent();
        UpsertOnboardingEventRequest mode = creditRequest(40.0);
        mode.setLimitMode("TIME");
        mode.setEndsAt(IndiaTime.now().plusDays(30));
        assertThrows(IllegalArgumentException.class, () -> service.update("evt1", mode, "admin"));
        assertEquals(credits, stored.get().getCreditsPerStudent());

        UpsertOnboardingEventRequest amount = creditRequest(90.0);
        assertThrows(IllegalArgumentException.class, () -> service.update("evt1", amount, "admin"));

        UpsertOnboardingEventRequest code = creditRequest(40.0);
        code.setReferralCode("EVT-OTHER1");
        assertThrows(IllegalArgumentException.class, () -> service.update("evt1", code, "admin"));

        stored.get().setStatus(OnboardingEvent.Status.ENDED);
        UpsertOnboardingEventRequest courses = creditRequest(40.0);
        courses.setCourseIds(List.of("c1", "c2"));
        assertThrows(IllegalArgumentException.class, () -> service.update("evt1", courses, "admin"));
        assertThrows(IllegalArgumentException.class, () -> service.reopen("evt1", "admin"));
    }

    @Test
    void closeReopenAndEnd() {
        UpsertOnboardingEventRequest request = timeRequest(IndiaTime.now().plusDays(10));
        request.setPublish(true);
        service.create(request, "admin");
        service.closeRegistration("evt1", "admin");
        assertEquals(OnboardingEvent.Status.CLOSED, stored.get().getStatus());
        service.reopen("evt1", "admin");
        assertEquals(OnboardingEvent.Status.OPEN, stored.get().getStatus());

        stored.get().setEndsAt(IndiaTime.now().minusMinutes(1));
        stored.get().setStatus(OnboardingEvent.Status.CLOSED);
        assertThrows(IllegalArgumentException.class, () -> service.reopen("evt1", "admin"));

        stored.get().setEndsAt(IndiaTime.now().plusDays(10));
        stored.get().setStatus(OnboardingEvent.Status.OPEN);
        User student = student("u1");
        service.joinNewStudent(student, stored.get().getReferralCode(), "c1");
        LocalDateTime expiry = stored.get().getEndsAt();
        service.end("evt1", "admin");
        service.end("evt1", "admin");
        assertEquals(OnboardingEvent.Status.ENDED, stored.get().getStatus());
        assertEquals(EventMembership.Status.REVERTED, memberships.get(0).getStatus());
        assertEquals(EventMembership.RevertReason.ENDED_BY_ADMIN, memberships.get(0).getRevertReason());
        assertEquals(expiry, stored.get().getEndsAt());
        verify(adminAuditService).log(eq("admin"), eq("EVENT_CLOSE"), any(), any(), any(), any());
        verify(adminAuditService).log(eq("admin"), eq("EVENT_REOPEN"), any(), any(), any(), any());
        verify(adminAuditService, times(2)).log(eq("admin"), eq("EVENT_END"), any(), any(), any(), any());
    }

    @Test
    void newStudentJoinsWithoutRewardingAReferrer() {
        service.create(publishedCredit(), "admin");
        User student = student("u1");
        service.joinNewStudent(student, "  " + stored.get().getReferralCode().toLowerCase() + " ", "c1");
        assertEquals(1, memberships.size());
        assertNull(student.getReferredBy());
        assertEquals(User.UserRole.USER, student.getRole());
        verify(skillamaHtmlMailer).sendEventRegistration(eq("u1@x.com"), eq("Ada"), eq("IIT Roorkee"));
        verify(notificationSettingsService, never()).sendTeamNotification(any(), any(), any());
    }

    @Test
    void joinWindowAndExistingAccountRules() {
        UpsertOnboardingEventRequest request = publishedCredit();
        request.setRegistrationOpensAt(IndiaTime.now().plusHours(2));
        service.create(request, "admin");
        IllegalStateException early = assertThrows(IllegalStateException.class,
                () -> service.assertOpenForNewSignup(stored.get().getReferralCode()));
        assertEquals(OnboardingEventService.NOT_OPEN_YET, early.getMessage());

        stored.get().setRegistrationOpensAt(IndiaTime.now().minusMinutes(1));
        stored.get().setStatus(OnboardingEvent.Status.DRAFT);
        stored.get().setReferralCode(null);
        assertFalse(service.isEventCode("EVT-NONE"));

        stored.get().setReferralCode("EVT-OPEN111");
        stored.get().setStatus(OnboardingEvent.Status.CLOSED);
        assertEquals(OnboardingEventService.NOT_ACCEPTING, assertThrows(IllegalStateException.class,
                () -> service.assertOpenForNewSignup("EVT-OPEN111")).getMessage());

        stored.get().setStatus(OnboardingEvent.Status.ENDED);
        assertEquals(OnboardingEventService.EVENT_ENDED, assertThrows(IllegalStateException.class,
                () -> service.assertOpenForNewSignup("EVT-OPEN111")).getMessage());
    }

    @Test
    void timeEventAllowsJoinAtExpiryAndRejectsOneSecondLater() {
        LocalDateTime end = IndiaTime.now().plusSeconds(2);
        UpsertOnboardingEventRequest request = timeRequest(end);
        request.setPublish(true);
        service.create(request, "admin");
        service.assertOpenForNewSignup(stored.get().getReferralCode());
        stored.get().setEndsAt(IndiaTime.now().minusSeconds(1));
        assertEquals(OnboardingEventService.EVENT_ENDED, assertThrows(IllegalStateException.class,
                () -> service.assertOpenForNewSignup(stored.get().getReferralCode())).getMessage());
    }

    @Test
    void secondEventCodeIsRejected() {
        service.create(publishedCredit(), "admin");
        User student = student("u1");
        service.joinNewStudent(student, stored.get().getReferralCode(), null);
        assertThrows(IllegalStateException.class,
                () -> service.joinNewStudent(student, stored.get().getReferralCode(), "c1"));
    }

    @Test
    void creditSpendStaysOnTheEventAndBlocksTheNextCall() {
        service.create(publishedCredit(), "admin");
        User student = student("u1");
        service.joinNewStudent(student, stored.get().getReferralCode(), "c1");
        assertEquals(EventAccessSupport.Gate.COVERED, service.gate(student));
        service.recordCoveredSpend("u1", 0.30);
        assertEquals(EventAccessSupport.Gate.COVERED, service.gate(student));
        service.recordCoveredSpend("u1", 0.15);
        assertEquals(EventAccessSupport.Gate.CREDIT_BLOCKED, service.gate(student));
        assertEquals(EventMembership.Status.REVERTED, memberships.get(0).getStatus());
        assertEquals(EventAccessSupport.Gate.NONE, service.gate(student));
    }

    @Test
    void timeSpendDoesNotUseACreditCeilingAndLifetimeSurvivesASecondRecord() {
        UpsertOnboardingEventRequest request = timeRequest(IndiaTime.now().plusDays(30));
        request.setPublish(true);
        service.create(request, "admin");
        User student = student("u1");
        service.joinNewStudent(student, stored.get().getReferralCode(), "c1");
        service.recordCoveredSpend("u1", 1.5);
        service.recordCoveredSpend("u1", 2.5);
        assertEquals(4.0, stored.get().getLifetimeSpendUsd(), 0.0001);
        assertEquals(EventAccessSupport.Gate.COVERED, service.gate(student));
        assertEquals(0.0, student.getAiCostUsdThisPeriod() == null ? 0.0 : student.getAiCostUsdThisPeriod(), 0.0001);
    }

    @Test
    void alarmEmailsEachBandOnceAndWeeklyOnlyWhileOverBudget() {
        UpsertOnboardingEventRequest request = publishedCredit();
        request.setAlarmBudgetCredits(100.0);
        service.create(request, "admin");
        User student = student("u1");
        service.joinNewStudent(student, stored.get().getReferralCode(), "c1");
        service.recordCoveredSpend("u1", 0.50);
        service.recordCoveredSpend("u1", 0.01);
        verify(notificationSettingsService, times(1)).sendTeamNotification(
                eq(NotificationEventType.EVENT_SPEND_ALERT), anyString(), anyString());
        service.recordCoveredSpend("u1", 0.30);
        verify(notificationSettingsService, times(2)).sendTeamNotification(
                eq(NotificationEventType.EVENT_SPEND_ALERT), anyString(), anyString());
        service.recordCoveredSpend("u1", 0.25);
        verify(notificationSettingsService, times(3)).sendTeamNotification(
                eq(NotificationEventType.EVENT_SPEND_ALERT), anyString(), anyString());
        service.recordCoveredSpend("u1", 0.25);
        verify(notificationSettingsService, times(4)).sendTeamNotification(
                eq(NotificationEventType.EVENT_SPEND_ALERT), anyString(), anyString());

        stored.get().setLastWeeklyAlarmAt(IndiaTime.now().minusDays(8));
        when(eventRepository.findByStatusNot(OnboardingEvent.Status.ENDED)).thenReturn(List.of(stored.get()));
        service.sendDueWeeklyAlarms();
        verify(notificationSettingsService, times(5)).sendTeamNotification(
                eq(NotificationEventType.EVENT_SPEND_ALERT), anyString(), anyString());

        stored.get().setAlarmBudgetUsd(10.0);
        service.sendDueWeeklyAlarms();
        verify(notificationSettingsService, times(5)).sendTeamNotification(
                eq(NotificationEventType.EVENT_SPEND_ALERT), anyString(), anyString());
    }

    @Test
    void noAlarmBudgetDoesNotEmail() {
        service.create(publishedCredit(), "admin");
        User student = student("u1");
        service.joinNewStudent(student, stored.get().getReferralCode(), "c1");
        service.recordCoveredSpend("u1", 5);
        verify(notificationSettingsService, never()).sendTeamNotification(any(), any(), any());
        assertTrue(service.list().get(0).getLifetimeCreditsBurned() > 0);
    }

    @Test
    void featureAllowListAndPaidPlanFloor() {
        service.create(publishedCredit(), "admin");
        User student = student("u1");
        service.joinNewStudent(student, stored.get().getReferralCode(), "c1");
        assertTrue(service.grantsHiddenFeature("u1", "ai_tutor"));
        assertTrue(service.deniesLiveFeature("u1", "ai_exam"));
        assertFalse(service.moduleAllowed(student, "Debug"));

        student.setPlanTier(User.PlanTier.PAID);
        assertFalse(service.deniesLiveFeature("u1", "debug_assistant"));
        assertTrue(service.grantsHiddenFeature("u1", "debug_assistant"));
        assertNull(service.eventModules(student));
    }

    @Test
    void removingTheRetainedCoursePromotesAnotherAndBlocksTheOldOne() {
        UpsertOnboardingEventRequest request = publishedCredit();
        request.setCourseIds(List.of("c1", "c2"));
        service.create(request, "admin");
        User student = student("u1");
        service.joinNewStudent(student, stored.get().getReferralCode(), "c1");
        UserCourseEnrollment oldRetained = new UserCourseEnrollment();
        oldRetained.setUserId("u1");
        oldRetained.setCourseId("c1");
        oldRetained.setEnrollmentType(UserCourseEnrollment.EnrollmentType.USER_SELECTED_FREEMIUM);
        oldRetained.setStatus(UserCourseEnrollment.EnrollmentStatus.ACTIVE);
        when(enrollmentRepository.findByUserId("u1")).thenReturn(List.of(oldRetained));

        UpsertOnboardingEventRequest edit = creditRequest(40.0);
        edit.setCourseIds(List.of("c2"));
        service.update("evt1", edit, "admin");
        assertEquals("c2", memberships.get(0).getRetainedCourseId());
        assertEquals(UserCourseEnrollment.EnrollmentStatus.INACTIVE, oldRetained.getStatus());
    }

    @Test
    void revertedMemberIsNotReEnrolledWhenACourseIsAdded() {
        service.create(publishedCredit(), "admin");
        User student = student("u1");
        service.joinNewStudent(student, stored.get().getReferralCode(), "c1");
        memberships.get(0).setStatus(EventMembership.Status.REVERTED);
        UpsertOnboardingEventRequest edit = creditRequest(40.0);
        edit.setCourseIds(List.of("c1", "c2"));
        service.update("evt1", edit, "admin");
        verify(userCourseAccessService, never()).enrollIfAbsent(eq("u1"), eq("c2"), any());
    }

    @Test
    void expiryJobRevertsAndASecondRevertKeepsTheRetainedCourse() {
        UpsertOnboardingEventRequest request = timeRequest(IndiaTime.now().minusMinutes(1));
        request.setPublish(true);
        request.setRegistrationOpensAt(IndiaTime.now().minusDays(2));
        assertThrows(IllegalArgumentException.class, () -> service.create(request, "admin"));

        UpsertOnboardingEventRequest open = timeRequest(IndiaTime.now().plusDays(2));
        open.setPublish(true);
        service.create(open, "admin");
        User student = student("u1");
        service.joinNewStudent(student, stored.get().getReferralCode(), "c1");
        stored.get().setEndsAt(IndiaTime.now().minusMinutes(1));
        when(membershipRepository.findByStatus(EventMembership.Status.ACTIVE)).thenReturn(List.of(memberships.get(0)));
        service.expireDueMemberships();
        assertEquals(EventMembership.RevertReason.EXPIRED, memberships.get(0).getRevertReason());
        service.expireDueMemberships();
        assertEquals("c1", memberships.get(0).getRetainedCourseId());
    }

    @Test
    void paidStudentKeepsAPurchasedCourseWhenTheEventEnds() {
        service.create(publishedCredit(), "admin");
        User student = student("u1");
        student.setPlanTier(User.PlanTier.PAID);
        student.setAiWalletLimitUsd(10.0);
        service.joinNewStudent(student, stored.get().getReferralCode(), "c1");
        UserCourseEnrollment purchased = new UserCourseEnrollment();
        purchased.setUserId("u1");
        purchased.setCourseId("bought");
        purchased.setEnrollmentType(UserCourseEnrollment.EnrollmentType.PURCHASED);
        purchased.setStatus(UserCourseEnrollment.EnrollmentStatus.ACTIVE);
        when(enrollmentRepository.findByUserId("u1")).thenReturn(List.of(purchased));
        service.recordCoveredSpend("u1", 1);
        assertEquals(EventAccessSupport.Gate.NONE, service.gate(student));
        assertEquals(User.PlanTier.PAID, student.getPlanTier());
        assertEquals(10.0, student.getAiWalletLimitUsd(), 0.0001);
        assertEquals(UserCourseEnrollment.EnrollmentStatus.ACTIVE, purchased.getStatus());
    }

    @Test
    void registrationMailHighlightsTheNameAndDropsWhenDisabled() {
        String html = SkillamaHtmlMailer.eventRegistrationHtml("Ada", "IIT <Roorkee>", "https://skillama.co.in");
        assertTrue(html.contains("IIT &lt;Roorkee&gt;"));
        assertFalse(html.contains("<Roorkee>"));
        assertFalse(html.toLowerCase().contains("expires"));
        assertFalse(html.toLowerCase().contains("valid until"));
        String plain = SkillamaHtmlMailer.eventRegistrationPlain("Ada", "IIT Roorkee", "https://skillama.co.in");
        assertTrue(plain.contains("IIT Roorkee"));
        assertFalse(plain.contains("EVT-"));

        when(notificationSettingsService.isEnabled(NotificationEventType.EVENT_REGISTRATION)).thenReturn(false);
        service.create(publishedCredit(), "admin");
        service.joinNewStudent(student("u1"), stored.get().getReferralCode(), "c1");
        verify(skillamaHtmlMailer, never()).sendEventRegistration(any(), any(), any());
    }

    @Test
    void mailFailureDoesNotDropTheMembership() {
        when(skillamaHtmlMailer.sendEventRegistration(any(), any(), any()))
                .thenThrow(new RuntimeException("smtp down"));
        service.create(publishedCredit(), "admin");
        service.joinNewStudent(student("u1"), stored.get().getReferralCode(), "c1");
        assertEquals(1, memberships.size());
        assertEquals(EventMembership.Status.ACTIVE, memberships.get(0).getStatus());
    }

    @Test
    void joinedCountIncludesRevertedStudents() {
        service.create(publishedCredit(), "admin");
        service.joinNewStudent(student("u1"), stored.get().getReferralCode(), "c1");
        memberships.get(0).setStatus(EventMembership.Status.REVERTED);
        assertEquals(1, service.list().get(0).getStudentsJoined());
        assertEquals(0, service.list().get(0).getActiveCount());
        assertEquals("REVERTED", service.get("evt1").getStudents().get(0).getStatus());
    }

    private UpsertOnboardingEventRequest creditRequest(Double credits) {
        UpsertOnboardingEventRequest request = new UpsertOnboardingEventRequest();
        request.setName("IIT Roorkee");
        request.setEventDate(LocalDate.now().plusDays(1));
        request.setLimitMode("CREDIT");
        request.setCreditsPerStudent(credits);
        request.setCourseIds(List.of("c1"));
        request.setFeatureCodes(List.of("ai_tutor", "code_lab"));
        return request;
    }

    private UpsertOnboardingEventRequest publishedCredit() {
        UpsertOnboardingEventRequest request = creditRequest(40.0);
        request.setPublish(true);
        request.setRegistrationOpensAt(IndiaTime.now().minusHours(1));
        return request;
    }

    private UpsertOnboardingEventRequest timeRequest(LocalDateTime endsAt) {
        UpsertOnboardingEventRequest request = creditRequest(null);
        request.setLimitMode("TIME");
        request.setCreditsPerStudent(null);
        request.setEndsAt(endsAt);
        request.setRegistrationOpensAt(IndiaTime.now().minusHours(1));
        return request;
    }

    private static Course course(String id, String name, boolean active) {
        Course course = new Course();
        course.setId(id);
        course.setName(name);
        course.setActive(active);
        return course;
    }

    private User student(String id) {
        User user = new User();
        user.setId(id);
        user.setName("Ada");
        user.setEmail(id + "@x.com");
        user.setRole(User.UserRole.USER);
        user.setPlanTier(User.PlanTier.FREEMIUM);
        users.put(id, user);
        return user;
    }
}
