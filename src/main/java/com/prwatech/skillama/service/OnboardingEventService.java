package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.EventLearnerAccessDTO;
import com.prwatech.skillama.dto.EventStudentDTO;
import com.prwatech.skillama.dto.FreemiumCourseOptionDTO;
import com.prwatech.skillama.dto.OnboardingEventDetailDTO;
import com.prwatech.skillama.dto.OnboardingEventSummaryDTO;
import com.prwatech.skillama.dto.UpsertOnboardingEventRequest;
import com.prwatech.skillama.exception.AiBudgetLimitException;
import com.prwatech.skillama.exception.ResourceNotFoundException;
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
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class OnboardingEventService implements EventAccessSupport {

    public static final String NOT_OPEN_YET = "This event code is not open yet.";
    public static final String EVENT_ENDED = "This event has ended.";
    public static final String NOT_ACCEPTING = "This event is no longer accepting new students.";
    public static final String NEW_STUDENTS_ONLY = "This code is only for new students.";
    public static final String PROFILE_REJECTED = "Event codes can only be used when creating a new account.";
    public static final String ALREADY_JOINED = "You are already registered through an event.";
    public static final double CREDITS_PER_USD = 100.0;

    private static final Logger LOGGER = LoggerFactory.getLogger(OnboardingEventService.class);
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final Set<UserCourseEnrollment.EnrollmentType> PROTECTED_TYPES = Set.of(
            UserCourseEnrollment.EnrollmentType.PURCHASED,
            UserCourseEnrollment.EnrollmentType.ASSIGNED,
            UserCourseEnrollment.EnrollmentType.REQUEST_APPROVED);

    private final OnboardingEventRepository eventRepository;
    private final EventMembershipRepository membershipRepository;
    private final SkillamaUserRepository userRepository;
    private final CourseRepository courseRepository;
    private final UserCourseEnrollmentRepository enrollmentRepository;
    private final UserCourseAccessService userCourseAccessService;
    private final AdminAuditService adminAuditService;
    private final NotificationSettingsService notificationSettingsService;
    private final SkillamaHtmlMailer skillamaHtmlMailer;

    @Value("${skillama.app.public-url:https://skillama.co.in}")
    private String publicAppUrl;

    public static LocalDateTime defaultRegistrationOpensAt(LocalDate eventDate) {
        return eventDate.minusDays(1).atStartOfDay();
    }

    /** 00:00 IST on the day before the event, expressed in UTC, for the open/closed boundary. */
    public static LocalDateTime registrationOpensAtUtc(LocalDate eventDate) {
        ZonedDateTime ist = defaultRegistrationOpensAt(eventDate).atZone(IndiaTime.ZONE);
        return ist.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    public static double creditsToUsd(double credits) {
        return credits / CREDITS_PER_USD;
    }

    public static double usdToCredits(double usd) {
        return usd * CREDITS_PER_USD;
    }

    public boolean isEventCode(String rawCode) {
        return findByCode(rawCode).isPresent();
    }

    public List<EventFeatureCatalog.Feature> featureChoices() {
        return EventFeatureCatalog.FEATURES;
    }

    public List<OnboardingEventSummaryDTO> list() {
        return eventRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toSummary).toList();
    }

    public OnboardingEventDetailDTO get(String eventId) {
        OnboardingEvent event = require(eventId);
        List<EventStudentDTO> students = membershipRepository.findByEventId(eventId).stream()
                .map(this::toStudent)
                .toList();
        return OnboardingEventDetailDTO.builder().event(toSummary(event)).students(students).build();
    }

    @Transactional
    public OnboardingEventDetailDTO create(UpsertOnboardingEventRequest request, String actorId) {
        OnboardingEvent event = new OnboardingEvent();
        event.setCreatedAt(IndiaTime.now());
        event.setCreatedBy(actorId);
        event.setStatus(OnboardingEvent.Status.DRAFT);
        event.setLifetimeSpendUsd(0.0);
        applyWritable(event, request, true);
        event = eventRepository.save(event);
        adminAuditService.log(actorId, "EVENT_CREATE", "OnboardingEvent", event.getId(),
                "Created event " + event.getName(), null);
        if (Boolean.TRUE.equals(request.getPublish())) {
            return publish(event.getId(), actorId);
        }
        return get(event.getId());
    }

    @Transactional
    public OnboardingEventDetailDTO update(String eventId, UpsertOnboardingEventRequest request, String actorId) {
        OnboardingEvent event = require(eventId);
        rejectImmutableChanges(event, request);
        if (event.getStatus() == OnboardingEvent.Status.ENDED && coursesOrFeaturesChanged(event, request)) {
            throw new IllegalArgumentException("An ended event cannot change courses or features");
        }
        List<String> previousCourses = new ArrayList<>(event.getCourseIds() == null ? List.of() : event.getCourseIds());
        applyWritable(event, request, false);
        event.setUpdatedAt(IndiaTime.now());
        resetAlarmCursor(event);
        eventRepository.save(event);
        if (event.getStatus() != OnboardingEvent.Status.ENDED && coursesOrFeaturesChanged(previousCourses, event.getCourseIds())) {
            syncActiveMembers(event);
        }
        adminAuditService.log(actorId, "EVENT_UPDATE", "OnboardingEvent", event.getId(),
                "Updated event " + event.getName(), null);
        if (Boolean.TRUE.equals(request.getPublish()) && event.getStatus() == OnboardingEvent.Status.DRAFT) {
            return publish(event.getId(), actorId);
        }
        return get(event.getId());
    }

    @Transactional
    public OnboardingEventDetailDTO publish(String eventId, String actorId) {
        OnboardingEvent event = require(eventId);
        if (event.getStatus() == OnboardingEvent.Status.ENDED) {
            throw new IllegalArgumentException("An ended event cannot be published");
        }
        if (event.getReferralCode() == null || event.getReferralCode().isBlank()) {
            event.setReferralCode(generateCode());
        }
        if (event.getStatus() == OnboardingEvent.Status.DRAFT) {
            event.setStatus(OnboardingEvent.Status.OPEN);
        }
        event.setUpdatedAt(IndiaTime.now());
        eventRepository.save(event);
        adminAuditService.log(actorId, "EVENT_PUBLISH", "OnboardingEvent", event.getId(),
                "Published event " + event.getName(), null);
        return get(event.getId());
    }

    @Transactional
    public OnboardingEventDetailDTO closeRegistration(String eventId, String actorId) {
        OnboardingEvent event = require(eventId);
        if (event.getStatus() == OnboardingEvent.Status.ENDED) {
            throw new IllegalArgumentException("An ended event cannot be closed");
        }
        if (event.getStatus() != OnboardingEvent.Status.CLOSED) {
            event.setStatus(OnboardingEvent.Status.CLOSED);
            event.setUpdatedAt(IndiaTime.now());
            eventRepository.save(event);
        }
        adminAuditService.log(actorId, "EVENT_CLOSE", "OnboardingEvent", event.getId(),
                "Closed registration for " + event.getName(), null);
        return get(event.getId());
    }

    @Transactional
    public OnboardingEventDetailDTO reopen(String eventId, String actorId) {
        OnboardingEvent event = require(eventId);
        if (event.getStatus() == OnboardingEvent.Status.ENDED) {
            throw new IllegalArgumentException("An ended event cannot be reopened");
        }
        if (event.getStatus() != OnboardingEvent.Status.CLOSED) {
            throw new IllegalArgumentException("Only a closed event can be reopened");
        }
        if (event.getLimitMode() == OnboardingEvent.LimitMode.TIME
                && event.getEndsAt() != null
                && IndiaTime.now().isAfter(event.getEndsAt())) {
            throw new IllegalArgumentException(EVENT_ENDED);
        }
        event.setStatus(OnboardingEvent.Status.OPEN);
        event.setUpdatedAt(IndiaTime.now());
        eventRepository.save(event);
        adminAuditService.log(actorId, "EVENT_REOPEN", "OnboardingEvent", event.getId(),
                "Reopened registration for " + event.getName(), null);
        return get(event.getId());
    }

    @Transactional
    public OnboardingEventDetailDTO end(String eventId, String actorId) {
        OnboardingEvent event = require(eventId);
        if (event.getStatus() != OnboardingEvent.Status.ENDED) {
            event.setStatus(OnboardingEvent.Status.ENDED);
            event.setUpdatedAt(IndiaTime.now());
            eventRepository.save(event);
            for (EventMembership membership : membershipRepository.findByEventIdAndStatus(
                    event.getId(), EventMembership.Status.ACTIVE)) {
                revert(membership, EventMembership.RevertReason.ENDED_BY_ADMIN);
            }
        }
        adminAuditService.log(actorId, "EVENT_END", "OnboardingEvent", event.getId(),
                "Ended event " + event.getName(), null);
        return get(event.getId());
    }

    /** Throws when a new signup cannot use this code. Does not create a user. */
    public void assertOpenForNewSignup(String rawCode) {
        OnboardingEvent event = findByCode(rawCode)
                .orElseThrow(() -> new IllegalArgumentException("Invalid referral code"));
        assertJoinWindow(event, IndiaTime.now());
    }

    public String resolveSignupCourse(String rawCode, String requestedCourseId) {
        OnboardingEvent event = findByCode(rawCode)
                .orElseThrow(() -> new IllegalArgumentException("Invalid referral code"));
        return resolveCourseId(event, requestedCourseId);
    }

    public List<FreemiumCourseOptionDTO> signupCourses(String rawCode) {
        OnboardingEvent event = findByCode(rawCode)
                .orElseThrow(() -> new IllegalArgumentException("Invalid referral code"));
        assertJoinWindow(event, IndiaTime.now());
        List<FreemiumCourseOptionDTO> options = new ArrayList<>();
        for (String courseId : event.getCourseIds()) {
            courseRepository.findById(courseId).ifPresent(course -> options.add(
                    FreemiumCourseOptionDTO.builder().id(course.getId()).name(course.getName()).build()));
        }
        return options;
    }

    @Transactional
    public void joinNewStudent(User user, String rawCode, String requestedCourseId) {
        if (user == null || user.getId() == null) {
            throw new IllegalStateException("Student must be saved before joining an event");
        }
        if (user.getRole() != null && user.getRole() != User.UserRole.USER) {
            throw new IllegalStateException("Event access is only for students");
        }
        OnboardingEvent event = findByCode(rawCode)
                .orElseThrow(() -> new IllegalArgumentException("Invalid referral code"));
        assertJoinWindow(event, IndiaTime.now());
        if (membershipRepository.findByUserId(user.getId()).isPresent()) {
            throw new IllegalStateException(ALREADY_JOINED);
        }
        String retained = resolveCourseId(event, requestedCourseId);
        EventMembership membership = EventMembership.builder()
                .eventId(event.getId())
                .userId(user.getId())
                .joinedAt(IndiaTime.now())
                .retainedCourseId(retained)
                .status(EventMembership.Status.ACTIVE)
                .spendUsd(0.0)
                .build();
        membershipRepository.save(membership);
        user.setChosenFreemiumCourseId(retained);
        user.setRole(User.UserRole.USER);
        userRepository.save(user);
        syncMember(event, membership);
        sendRegistrationMail(user, event);
    }

    public EventLearnerAccessDTO learnerAccess(User user) {
        gate(user);
        Optional<EventMembership> active = activeMembership(user);
        if (active.isEmpty()) {
            return null;
        }
        OnboardingEvent event = eventRepository.findById(active.get().getEventId()).orElse(null);
        if (event == null) {
            return null;
        }
        Double remaining = null;
        if (event.getLimitMode() == OnboardingEvent.LimitMode.CREDIT && event.getCreditsPerStudent() != null) {
            remaining = Math.max(0, event.getCreditsPerStudent() - usdToCredits(active.get().getSpendUsd()));
        }
        return EventLearnerAccessDTO.builder()
                .active(true)
                .eventName(event.getName())
                .limitMode(event.getLimitMode().name())
                .creditsRemaining(remaining)
                .endsAt(event.getLimitMode() == OnboardingEvent.LimitMode.TIME ? event.getEndsAt() : null)
                .build();
    }

    @Override
    public Gate gate(User user) {
        if (user == null || user.getId() == null) {
            return Gate.NONE;
        }
        EventMembership membership = membershipRepository.findByUserId(user.getId()).orElse(null);
        if (membership == null || membership.getStatus() != EventMembership.Status.ACTIVE) {
            return Gate.NONE;
        }
        OnboardingEvent event = eventRepository.findById(membership.getEventId()).orElse(null);
        if (event == null) {
            return Gate.NONE;
        }
        if (event.getLimitMode() == OnboardingEvent.LimitMode.TIME
                && event.getEndsAt() != null
                && IndiaTime.now().isAfter(event.getEndsAt())) {
            revert(membership, EventMembership.RevertReason.EXPIRED);
            return Gate.NONE;
        }
        if (event.getLimitMode() == OnboardingEvent.LimitMode.CREDIT
                && event.getCreditsPerStudent() != null
                && usdToCredits(membership.getSpendUsd()) >= event.getCreditsPerStudent()) {
            revert(membership, EventMembership.RevertReason.CREDITS_EXHAUSTED);
            if (isPaid(user)) {
                return Gate.NONE;
            }
            return Gate.CREDIT_BLOCKED;
        }
        return Gate.COVERED;
    }

    @Override
    @Transactional
    public void recordCoveredSpend(String userId, double billedUsd) {
        if (userId == null || billedUsd <= 0) {
            return;
        }
        EventMembership membership = membershipRepository.findByUserId(userId).orElse(null);
        if (membership == null || membership.getStatus() != EventMembership.Status.ACTIVE) {
            return;
        }
        OnboardingEvent event = eventRepository.findById(membership.getEventId()).orElse(null);
        if (event == null) {
            return;
        }
        membership.setSpendUsd(membership.getSpendUsd() + billedUsd);
        membershipRepository.save(membership);
        event.setLifetimeSpendUsd(event.getLifetimeSpendUsd() + billedUsd);
        notifySpendThresholds(event);
        eventRepository.save(event);
    }

    @Override
    public boolean deniesLiveFeature(String userId, String featureCode) {
        if (userId == null || !EventFeatureCatalog.isEventFeature(featureCode)) {
            return false;
        }
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || isPaid(user)) {
            return false;
        }
        gate(user);
        Optional<EventMembership> active = activeMembership(user);
        if (active.isEmpty()) {
            return false;
        }
        OnboardingEvent event = eventRepository.findById(active.get().getEventId()).orElse(null);
        return event != null && !containsCode(event.getFeatureCodes(), featureCode);
    }

    @Override
    public boolean grantsHiddenFeature(String userId, String featureCode) {
        if (userId == null || featureCode == null) {
            return false;
        }
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return false;
        }
        gate(user);
        Optional<EventMembership> active = activeMembership(user);
        if (active.isEmpty()) {
            return false;
        }
        OnboardingEvent event = eventRepository.findById(active.get().getEventId()).orElse(null);
        if (event == null) {
            return false;
        }
        if (containsCode(event.getFeatureCodes(), featureCode)) {
            return true;
        }
        if (!isPaid(user)) {
            return false;
        }
        String moduleCode = null;
        for (EventFeatureCatalog.Feature feature : EventFeatureCatalog.FEATURES) {
            if (feature.code().equalsIgnoreCase(featureCode) && feature.moduleName() != null) {
                moduleCode = feature.code();
            }
        }
        return moduleCode != null;
    }

    @Override
    public List<String> eventModules(User user) {
        if (user == null || isPaid(user)) {
            return null;
        }
        gate(user);
        Optional<EventMembership> active = activeMembership(user);
        if (active.isEmpty()) {
            return null;
        }
        OnboardingEvent event = eventRepository.findById(active.get().getEventId()).orElse(null);
        if (event == null) {
            return null;
        }
        return EventFeatureCatalog.modulesFor(event.getFeatureCodes());
    }

    public boolean moduleAllowed(User user, String moduleName) {
        List<String> modules = eventModules(user);
        if (modules == null) {
            return false;
        }
        return modules.stream().anyMatch(module -> module.equalsIgnoreCase(moduleName));
    }

    public boolean restrictsModules(User user) {
        return eventModules(user) != null;
    }

    @Transactional
    public void expireDueMemberships() {
        LocalDateTime now = IndiaTime.now();
        for (EventMembership membership : membershipRepository.findByStatus(EventMembership.Status.ACTIVE)) {
            OnboardingEvent event = eventRepository.findById(membership.getEventId()).orElse(null);
            if (event == null || event.getLimitMode() != OnboardingEvent.LimitMode.TIME || event.getEndsAt() == null) {
                continue;
            }
            if (now.isAfter(event.getEndsAt())) {
                revert(membership, EventMembership.RevertReason.EXPIRED);
            }
        }
    }

    @Transactional
    public void sendDueWeeklyAlarms() {
        LocalDateTime now = IndiaTime.now();
        for (OnboardingEvent event : eventRepository.findByStatusNot(OnboardingEvent.Status.ENDED)) {
            if (event.getAlarmBudgetUsd() == null || event.getAlarmBudgetUsd() <= 0) {
                continue;
            }
            if (event.getLifetimeSpendUsd() < event.getAlarmBudgetUsd()) {
                continue;
            }
            if (event.getLastWeeklyAlarmAt() != null && event.getLastWeeklyAlarmAt().plusDays(7).isAfter(now)) {
                continue;
            }
            sendSpendAlarm(event, "weekly");
            event.setLastWeeklyAlarmAt(now);
            eventRepository.save(event);
        }
    }

    private void applyWritable(OnboardingEvent event, UpsertOnboardingEventRequest request, boolean creating) {
        if (request == null) {
            throw new IllegalArgumentException("Event details are required");
        }
        if (request.getName() == null || request.getName().isBlank()) {
            throw new IllegalArgumentException("Event name is required");
        }
        if (request.getEventDate() == null) {
            throw new IllegalArgumentException("Event date is required");
        }
        OnboardingEvent.LimitMode mode = creating
                ? parseMode(request.getLimitMode())
                : event.getLimitMode();
        if (creating) {
            event.setLimitMode(mode);
            if (mode == OnboardingEvent.LimitMode.CREDIT) {
                if (request.getEndsAt() != null) {
                    throw new IllegalArgumentException("Credit events cannot have an expiry");
                }
                if (request.getCreditsPerStudent() == null || request.getCreditsPerStudent() <= 0) {
                    throw new IllegalArgumentException("Credits per student must be greater than zero");
                }
                event.setCreditsPerStudent(request.getCreditsPerStudent());
                event.setEndsAt(null);
            } else {
                if (request.getCreditsPerStudent() != null) {
                    throw new IllegalArgumentException("Time events cannot have credits per student");
                }
                if (request.getEndsAt() == null) {
                    throw new IllegalArgumentException("Expiry is required");
                }
                if (!request.getEndsAt().isAfter(IndiaTime.now())) {
                    throw new IllegalArgumentException("Expiry is already in the past");
                }
                event.setEndsAt(request.getEndsAt());
                event.setCreditsPerStudent(null);
            }
        }
        event.setName(request.getName().trim());
        event.setOrganizerName(blankToNull(request.getOrganizerName()));
        event.setTrainerName(blankToNull(request.getTrainerName()));
        event.setEventDate(request.getEventDate());
        if (creating || request.getRegistrationOpensAt() != null) {
            LocalDateTime opens = request.getRegistrationOpensAt() != null
                    ? request.getRegistrationOpensAt()
                    : defaultRegistrationOpensAt(request.getEventDate());
            event.setRegistrationOpensAt(opens);
        }
        LocalDateTime opens = event.getRegistrationOpensAt();
        if (event.getLimitMode() == OnboardingEvent.LimitMode.TIME
                && event.getEndsAt() != null
                && opens != null
                && !event.getEndsAt().isAfter(opens)) {
            throw new IllegalArgumentException("Expiry is before registration opens");
        }
        event.setFeatureCodes(normalizeFeatures(request.getFeatureCodes()));
        event.setCourseIds(normalizeCourses(request.getCourseIds()));
        if (request.getAlarmBudgetCredits() == null) {
            event.setAlarmBudgetUsd(null);
        } else if (request.getAlarmBudgetCredits() <= 0) {
            throw new IllegalArgumentException("Alarm budget must be greater than zero");
        } else {
            event.setAlarmBudgetUsd(creditsToUsd(request.getAlarmBudgetCredits()));
        }
        event.setUpdatedAt(IndiaTime.now());
    }

    private void rejectImmutableChanges(OnboardingEvent event, UpsertOnboardingEventRequest request) {
        if (request.getLimitMode() != null
                && !request.getLimitMode().trim().equalsIgnoreCase(event.getLimitMode().name())) {
            throw new IllegalArgumentException("The credit or time limit cannot be changed");
        }
        if (event.getLimitMode() == OnboardingEvent.LimitMode.CREDIT
                && request.getCreditsPerStudent() != null
                && Double.compare(request.getCreditsPerStudent(), event.getCreditsPerStudent()) != 0) {
            throw new IllegalArgumentException("Credits per student cannot be changed");
        }
        if (event.getLimitMode() == OnboardingEvent.LimitMode.TIME
                && request.getEndsAt() != null
                && !request.getEndsAt().equals(event.getEndsAt())) {
            throw new IllegalArgumentException("Expiry cannot be changed");
        }
        if (request.getReferralCode() != null && !request.getReferralCode().isBlank()
                && event.getReferralCode() != null
                && !FreemiumService.normalizeReferralCode(request.getReferralCode()).equals(event.getReferralCode())) {
            throw new IllegalArgumentException("Referral code cannot be changed");
        }
    }

    private void resetAlarmCursor(OnboardingEvent event) {
        if (event.getAlarmBudgetUsd() == null || event.getAlarmBudgetUsd() <= 0) {
            return;
        }
        double percent = (event.getLifetimeSpendUsd() / event.getAlarmBudgetUsd()) * 100.0;
        int highestStillCrossed = 0;
        for (int threshold : thresholdsUpTo(percent)) {
            if (percent >= threshold) {
                highestStillCrossed = threshold;
            }
        }
        Integer previous = event.getLastSpendAlarmPercent();
        if (previous != null && previous > highestStillCrossed) {
            event.setLastSpendAlarmPercent(highestStillCrossed == 0 ? null : highestStillCrossed);
        }
    }

    private void assertJoinWindow(OnboardingEvent event, LocalDateTime now) {
        if (event.getStatus() == OnboardingEvent.Status.DRAFT || event.getReferralCode() == null) {
            throw new IllegalArgumentException("Invalid referral code");
        }
        if (event.getStatus() == OnboardingEvent.Status.CLOSED) {
            throw new IllegalStateException(NOT_ACCEPTING);
        }
        if (event.getStatus() == OnboardingEvent.Status.ENDED) {
            throw new IllegalStateException(EVENT_ENDED);
        }
        if (event.getRegistrationOpensAt() != null && now.isBefore(event.getRegistrationOpensAt())) {
            throw new IllegalStateException(NOT_OPEN_YET);
        }
        if (event.getLimitMode() == OnboardingEvent.LimitMode.TIME
                && event.getEndsAt() != null
                && now.isAfter(event.getEndsAt())) {
            throw new IllegalStateException(EVENT_ENDED);
        }
    }

    private String resolveCourseId(OnboardingEvent event, String requestedCourseId) {
        List<String> courses = event.getCourseIds() == null ? List.of() : event.getCourseIds();
        if (courses.isEmpty()) {
            throw new IllegalArgumentException("At least one course is required");
        }
        if (requestedCourseId == null || requestedCourseId.isBlank()) {
            if (courses.size() == 1) {
                return courses.get(0);
            }
            throw new IllegalArgumentException("Please choose a course to start with");
        }
        String trimmed = requestedCourseId.trim();
        if (!courses.contains(trimmed)) {
            throw new IllegalArgumentException("Please select a valid course");
        }
        return trimmed;
    }

    private void syncActiveMembers(OnboardingEvent event) {
        for (EventMembership membership : membershipRepository.findByEventIdAndStatus(
                event.getId(), EventMembership.Status.ACTIVE)) {
            syncMember(event, membership);
        }
    }

    private void syncMember(OnboardingEvent event, EventMembership membership) {
        List<String> courseIds = event.getCourseIds() == null ? List.of() : event.getCourseIds();
        if (courseIds.isEmpty()) {
            return;
        }
        String retained = membership.getRetainedCourseId();
        if (retained == null || !courseIds.contains(retained)) {
            retained = courseIds.get(0);
            membership.setRetainedCourseId(retained);
            membershipRepository.save(membership);
            final String keptCourseId = retained;
            userRepository.findById(membership.getUserId()).ifPresent(user -> {
                user.setChosenFreemiumCourseId(keptCourseId);
                userRepository.save(user);
            });
        }
        Set<String> desired = new LinkedHashSet<>(courseIds);
        for (String courseId : desired) {
            boolean keep = courseId.equals(retained);
            ensureEnrollment(membership.getUserId(), courseId, keep);
        }
        for (UserCourseEnrollment enrollment : enrollmentRepository.findByUserId(membership.getUserId())) {
            if (desired.contains(enrollment.getCourseId())) {
                continue;
            }
            if (PROTECTED_TYPES.contains(enrollment.getEnrollmentType())) {
                continue;
            }
            if (enrollment.getEnrollmentType() == UserCourseEnrollment.EnrollmentType.EVENT
                    || (enrollment.getEnrollmentType() == UserCourseEnrollment.EnrollmentType.USER_SELECTED_FREEMIUM
                    && !enrollment.getCourseId().equals(retained))) {
                enrollment.setStatus(UserCourseEnrollment.EnrollmentStatus.INACTIVE);
                enrollmentRepository.save(enrollment);
            }
        }
    }

    private void ensureEnrollment(String userId, String courseId, boolean retained) {
        Optional<UserCourseEnrollment> existing = enrollmentRepository.findByUserIdAndCourseId(userId, courseId);
        if (existing.isPresent() && PROTECTED_TYPES.contains(existing.get().getEnrollmentType())) {
            if (existing.get().getStatus() != UserCourseEnrollment.EnrollmentStatus.ACTIVE) {
                existing.get().setStatus(UserCourseEnrollment.EnrollmentStatus.ACTIVE);
                enrollmentRepository.save(existing.get());
            }
            return;
        }
        UserCourseEnrollment.EnrollmentType type = retained
                ? UserCourseEnrollment.EnrollmentType.USER_SELECTED_FREEMIUM
                : UserCourseEnrollment.EnrollmentType.EVENT;
        userCourseAccessService.enrollIfAbsent(userId, courseId, type);
        enrollmentRepository.findByUserIdAndCourseId(userId, courseId).ifPresent(enrollment -> {
            if (!PROTECTED_TYPES.contains(enrollment.getEnrollmentType())
                    && enrollment.getEnrollmentType() != type) {
                enrollment.setEnrollmentType(type);
            }
            enrollment.setStatus(UserCourseEnrollment.EnrollmentStatus.ACTIVE);
            enrollmentRepository.save(enrollment);
        });
    }

    private void revert(EventMembership membership, EventMembership.RevertReason reason) {
        if (membership.getStatus() == EventMembership.Status.REVERTED) {
            return;
        }
        membership.setStatus(EventMembership.Status.REVERTED);
        membership.setRevertReason(reason);
        membership.setRevertedAt(IndiaTime.now());
        membershipRepository.save(membership);
        String retained = membership.getRetainedCourseId();
        for (UserCourseEnrollment enrollment : enrollmentRepository.findByUserId(membership.getUserId())) {
            if (PROTECTED_TYPES.contains(enrollment.getEnrollmentType())) {
                continue;
            }
            if (retained != null && retained.equals(enrollment.getCourseId())) {
                enrollment.setEnrollmentType(UserCourseEnrollment.EnrollmentType.USER_SELECTED_FREEMIUM);
                enrollment.setStatus(UserCourseEnrollment.EnrollmentStatus.ACTIVE);
                enrollmentRepository.save(enrollment);
                continue;
            }
            if (enrollment.getEnrollmentType() == UserCourseEnrollment.EnrollmentType.EVENT
                    || enrollment.getEnrollmentType() == UserCourseEnrollment.EnrollmentType.USER_SELECTED_FREEMIUM) {
                enrollment.setStatus(UserCourseEnrollment.EnrollmentStatus.INACTIVE);
                enrollmentRepository.save(enrollment);
            }
        }
    }

    private void notifySpendThresholds(OnboardingEvent event) {
        if (event.getAlarmBudgetUsd() == null || event.getAlarmBudgetUsd() <= 0) {
            return;
        }
        double percent = (event.getLifetimeSpendUsd() / event.getAlarmBudgetUsd()) * 100.0;
        int previous = event.getLastSpendAlarmPercent() == null ? 0 : event.getLastSpendAlarmPercent();
        int highest = previous;
        for (int threshold : thresholdsUpTo(percent)) {
            if (threshold > previous && percent >= threshold) {
                sendSpendAlarm(event, threshold + "%");
                highest = threshold;
            }
        }
        if (highest != previous) {
            event.setLastSpendAlarmPercent(highest);
        }
        if (percent >= 100.0 && event.getLastWeeklyAlarmAt() == null) {
            event.setLastWeeklyAlarmAt(IndiaTime.now());
        }
    }

    private static List<Integer> thresholdsUpTo(double percent) {
        List<Integer> thresholds = new ArrayList<>();
        thresholds.add(50);
        thresholds.add(80);
        thresholds.add(100);
        int extra = 125;
        while (extra <= percent && extra < 100_000) {
            thresholds.add(extra);
            extra += 25;
        }
        return thresholds;
    }

    private void sendSpendAlarm(OnboardingEvent event, String band) {
        double burned = usdToCredits(event.getLifetimeSpendUsd());
        double budget = event.getAlarmBudgetUsd() == null ? 0 : usdToCredits(event.getAlarmBudgetUsd());
        String subject = "Event spend alert — " + event.getName();
        String body = event.getName() + " has burned " + formatCredits(burned)
                + " credits of a " + formatCredits(budget) + " credit alarm budget (" + band + ").\n\n"
                + "Students can keep using the event. End the event from the Events page to stop access.";
        notificationSettingsService.sendTeamNotification(NotificationEventType.EVENT_SPEND_ALERT, subject, body);
    }

    private void sendRegistrationMail(User user, OnboardingEvent event) {
        try {
            if (!notificationSettingsService.isEnabled(NotificationEventType.EVENT_REGISTRATION)) {
                return;
            }
            if (user.getEmail() == null || user.getEmail().isBlank()) {
                return;
            }
            skillamaHtmlMailer.sendEventRegistration(user.getEmail(), user.getName(), event.getName());
        } catch (RuntimeException ex) {
            LOGGER.warn("Event registration mail failed for {}: {}", user.getEmail(), ex.getMessage());
        }
    }

    private String generateCode() {
        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder suffix = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                suffix.append(CODE_ALPHABET.charAt(ThreadLocalRandom.current().nextInt(CODE_ALPHABET.length())));
            }
            String code = "EVT-" + suffix;
            if (eventRepository.findByReferralCode(code).isEmpty()
                    && userRepository.findByReferralCode(code).isEmpty()) {
                return code;
            }
        }
        throw new IllegalStateException("Could not generate an event code");
    }

    private OnboardingEvent require(String eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));
    }

    private Optional<OnboardingEvent> findByCode(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            return Optional.empty();
        }
        return eventRepository.findByReferralCode(FreemiumService.normalizeReferralCode(rawCode))
                .filter(event -> event.getReferralCode() != null && !event.getReferralCode().isBlank());
    }

    private Optional<EventMembership> activeMembership(User user) {
        if (user == null || user.getId() == null) {
            return Optional.empty();
        }
        return membershipRepository.findByUserId(user.getId())
                .filter(membership -> membership.getStatus() == EventMembership.Status.ACTIVE);
    }

    private List<String> normalizeFeatures(List<String> raw) {
        List<String> codes = new ArrayList<>();
        if (raw == null) {
            return codes;
        }
        Set<String> seen = new HashSet<>();
        for (String code : raw) {
            if (code == null || code.isBlank()) {
                continue;
            }
            String normalized = code.trim().toLowerCase(Locale.ROOT);
            if (!EventFeatureCatalog.isEventFeature(normalized)) {
                throw new IllegalArgumentException("Unknown or unsupported feature code");
            }
            if (seen.add(normalized)) {
                codes.add(normalized);
            }
        }
        return codes;
    }

    private List<String> normalizeCourses(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            throw new IllegalArgumentException("At least one course is required");
        }
        List<String> ids = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String id : raw) {
            if (id == null || id.isBlank()) {
                continue;
            }
            String trimmed = id.trim();
            Course course = courseRepository.findById(trimmed)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown course"));
            if (course.getDeletedAt() != null || Boolean.FALSE.equals(course.getActive())) {
                throw new IllegalArgumentException("Inactive or deleted course");
            }
            if (seen.add(trimmed)) {
                ids.add(trimmed);
            }
        }
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("At least one course is required");
        }
        return ids;
    }

    private OnboardingEvent.LimitMode parseMode(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Limit mode is required");
        }
        try {
            return OnboardingEvent.LimitMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Limit mode is required");
        }
    }

    private OnboardingEventSummaryDTO toSummary(OnboardingEvent event) {
        long joined = membershipRepository.countByEventId(event.getId());
        long active = membershipRepository.countByEventIdAndStatus(event.getId(), EventMembership.Status.ACTIVE);
        return OnboardingEventSummaryDTO.builder()
                .id(event.getId())
                .name(event.getName())
                .organizerName(event.getOrganizerName())
                .trainerName(event.getTrainerName())
                .eventDate(event.getEventDate())
                .registrationOpensAt(event.getRegistrationOpensAt())
                .limitMode(event.getLimitMode() == null ? null : event.getLimitMode().name())
                .creditsPerStudent(event.getCreditsPerStudent())
                .endsAt(event.getEndsAt())
                .featureCodes(event.getFeatureCodes())
                .courseIds(event.getCourseIds())
                .alarmBudgetCredits(event.getAlarmBudgetUsd() == null ? null : usdToCredits(event.getAlarmBudgetUsd()))
                .lifetimeCreditsBurned(usdToCredits(event.getLifetimeSpendUsd()))
                .referralCode(event.getReferralCode())
                .referralLink(event.getReferralCode() == null ? null : referralLink(event.getReferralCode()))
                .status(event.getStatus() == null ? null : event.getStatus().name())
                .studentsJoined(joined)
                .activeCount(active)
                .revertedCount(Math.max(0, joined - active))
                .build();
    }

    private EventStudentDTO toStudent(EventMembership membership) {
        User user = userRepository.findById(membership.getUserId()).orElse(null);
        return EventStudentDTO.builder()
                .userId(membership.getUserId())
                .name(user != null ? user.getName() : null)
                .email(user != null ? user.getEmail() : null)
                .joinedAt(membership.getJoinedAt())
                .creditsBurned(usdToCredits(membership.getSpendUsd()))
                .status(membership.getStatus() == null ? null : membership.getStatus().name())
                .revertReason(membership.getRevertReason() == null ? null : membership.getRevertReason().name())
                .build();
    }

    private String referralLink(String code) {
        String base = publicAppUrl == null ? "https://skillama.co.in" : publicAppUrl.replaceAll("/$", "");
        return base + "/login?referral=" + code;
    }

    private static boolean containsCode(List<String> codes, String code) {
        if (codes == null || code == null) {
            return false;
        }
        for (String existing : codes) {
            if (code.equalsIgnoreCase(existing)) {
                return true;
            }
        }
        return false;
    }

    private static boolean coursesOrFeaturesChanged(OnboardingEvent event, UpsertOnboardingEventRequest request) {
        if (request.getCourseIds() != null && !new HashSet<>(request.getCourseIds()).equals(new HashSet<>(
                event.getCourseIds() == null ? List.of() : event.getCourseIds()))) {
            return true;
        }
        if (request.getFeatureCodes() != null) {
            Set<String> next = new HashSet<>();
            for (String code : request.getFeatureCodes()) {
                if (code != null) {
                    next.add(code.trim().toLowerCase(Locale.ROOT));
                }
            }
            Set<String> current = new HashSet<>();
            if (event.getFeatureCodes() != null) {
                for (String code : event.getFeatureCodes()) {
                    current.add(code.toLowerCase(Locale.ROOT));
                }
            }
            return !next.equals(current);
        }
        return false;
    }

    private static boolean coursesOrFeaturesChanged(List<String> previousCourses, List<String> nextCourses) {
        return !new HashSet<>(previousCourses).equals(new HashSet<>(nextCourses == null ? List.of() : nextCourses));
    }

    private static boolean isPaid(User user) {
        return user.getPlanTier() == User.PlanTier.PAID
                || user.getPlanTier() == User.PlanTier.ENTERPRISE
                || user.getPlanTier() == null;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String formatCredits(double credits) {
        if (Math.abs(credits - Math.rint(credits)) < 0.05) {
            return String.valueOf((long) Math.rint(credits));
        }
        return String.format(Locale.US, "%.1f", credits);
    }

    /** Exposed for the budget gate so a blocked freemium call uses the shared learner message. */
    public static AiBudgetLimitException creditBlocked() {
        return new AiBudgetLimitException(AiBudgetLimitException.LEARNER_MESSAGE, 0, 0);
    }
}
