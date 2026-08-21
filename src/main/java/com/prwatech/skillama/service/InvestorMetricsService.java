package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.DashboardStatsDTO;
import com.prwatech.skillama.dto.InvestorMetricsDTO;
import com.prwatech.skillama.model.AiUsageEvent;
import com.prwatech.skillama.model.Course;
import com.prwatech.skillama.model.ExamAttempt;
import com.prwatech.skillama.model.ModuleQuizAttempt;
import com.prwatech.skillama.model.ReferralConversionEvent;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.model.UserCourseEnrollment;
import com.prwatech.skillama.model.UserLectureProgress;
import com.prwatech.skillama.model.UserLoginEvent;
import com.prwatech.skillama.repository.AiUsageEventRepository;
import com.prwatech.skillama.repository.CourseRepository;
import com.prwatech.skillama.repository.CourseShareEventRepository;
import com.prwatech.skillama.repository.ExamAttemptRepository;
import com.prwatech.skillama.repository.ModuleQuizAttemptRepository;
import com.prwatech.skillama.repository.ReferralConversionEventRepository;
import com.prwatech.skillama.repository.ReferralShareEventRepository;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import com.prwatech.skillama.repository.UserCourseEnrollmentRepository;
import com.prwatech.skillama.repository.UserLectureProgressRepository;
import com.prwatech.skillama.repository.UserLoginEventRepository;
import com.prwatech.skillama.util.IndiaTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Owner-only investor metrics rollup. Everything here is MEASURED from stored collections
 * (usage events, login events, attempts, share events) — never assumed. Follows the
 * codebase's findAll + stream aggregation style (see AdminService.getDashboardStatistics).
 */
@Service
@RequiredArgsConstructor
public class InvestorMetricsService {

    /** Admin-side content generation — the cost of BUILDING a course, not serving learners. */
    private static final Set<String> CONTENT_CREATION_ENDPOINTS =
            Set.of("lecture_generation", "generate_image", "generate_thumbnail");

    private static final Set<String> DOUBT_ENDPOINTS =
            Set.of("chat_ask", "ai_mentor_ask", "ai_mentor_follow_up");

    private static final String UNATTRIBUTED_COURSE_KEY = "__unattributed__";

    private final AiUsageEventRepository aiUsageEventRepository;
    private final SkillamaUserRepository userRepository;
    private final UserLoginEventRepository userLoginEventRepository;
    private final UserCourseEnrollmentRepository enrollmentRepository;
    private final UserLectureProgressRepository lectureProgressRepository;
    private final ModuleQuizAttemptRepository moduleQuizAttemptRepository;
    private final ExamAttemptRepository examAttemptRepository;
    private final ReferralConversionEventRepository referralConversionEventRepository;
    private final ReferralShareEventRepository referralShareEventRepository;
    private final CourseShareEventRepository courseShareEventRepository;
    private final CourseRepository courseRepository;
    private final AiAnswerFeedbackService aiAnswerFeedbackService;
    private final UsdInrExchangeRateService usdInrExchangeRateService;
    private final AdminService adminService;

    public InvestorMetricsDTO getInvestorMetrics() {
        LocalDateTime now = IndiaTime.now();
        LocalDate today = now.toLocalDate();
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDateTime periodStart = monthStart.atStartOfDay();
        LocalDateTime periodEnd = today.plusDays(1).atStartOfDay().minusNanos(1);
        double usdToInr = usdInrExchangeRateService.getUsdToInrRate();

        List<AiUsageEvent> periodEvents = aiUsageEventRepository.findByCreatedAtBetween(periodStart, periodEnd);
        List<AiUsageEvent> allEvents = aiUsageEventRepository.findAll();
        List<User> learners = userRepository.findAll().stream()
                .filter(u -> u.getEffectiveRole() == User.UserRole.USER)
                .collect(Collectors.toList());
        List<UserLectureProgress> allLectureProgress = lectureProgressRepository.findAll();
        DashboardStatsDTO dashboard = adminService.getDashboardStatistics();

        return InvestorMetricsDTO.builder()
                .periodStart(monthStart)
                .periodEnd(today)
                .cost(buildCostSection(periodEvents, allEvents, allLectureProgress, usdToInr))
                .users(buildUsersSection(learners, allLectureProgress, dashboard))
                .traffic(buildTrafficSection(learners, now))
                .activity(buildActivitySection(periodEvents, periodStart, periodEnd, dashboard))
                .growth(buildGrowthSection(learners, periodStart, periodEnd, now))
                .build();
    }

    private InvestorMetricsDTO.CostSection buildCostSection(List<AiUsageEvent> periodEvents,
                                                            List<AiUsageEvent> allEvents,
                                                            List<UserLectureProgress> allLectureProgress,
                                                            double usdToInr) {
        double totalCostUsd = periodEvents.stream().mapToDouble(AiUsageEvent::getCostUsd).sum();
        long totalTokens = periodEvents.stream().mapToLong(AiUsageEvent::getTotalTokens).sum();
        long activeAiUsers = periodEvents.stream()
                .map(AiUsageEvent::getUserId)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .count();
        Double avgPerUserUsd = activeAiUsers > 0 ? round(totalCostUsd / activeAiUsers) : null;

        double creationCostUsd = periodEvents.stream()
                .filter(e -> e.getEndpoint() != null && CONTENT_CREATION_ENDPOINTS.contains(e.getEndpoint()))
                .mapToDouble(AiUsageEvent::getCostUsd)
                .sum();
        double learnerCostUsd = totalCostUsd - creationCostUsd;

        // All-time burn per learning-hour — both sides all-time so the ratio is honest.
        double allTimeCostUsd = allEvents.stream().mapToDouble(AiUsageEvent::getCostUsd).sum();
        double allTimeLearningHours = allLectureProgress.stream()
                .mapToLong(p -> p.getTimeSpent() != null ? p.getTimeSpent() : 0)
                .sum() / 3600.0;
        Double costPerHourUsd = allTimeLearningHours > 0 ? round(allTimeCostUsd / allTimeLearningHours) : null;

        Map<String, String> courseNames = courseRepository.findAll().stream()
                .collect(Collectors.toMap(Course::getId,
                        c -> c.getName() != null ? c.getName() : c.getId(),
                        (a, b) -> a));

        Map<String, List<AiUsageEvent>> byCourse = periodEvents.stream()
                .collect(Collectors.groupingBy(e ->
                        e.getCourseId() != null && !e.getCourseId().isBlank()
                                ? e.getCourseId()
                                : UNATTRIBUTED_COURSE_KEY));

        List<InvestorMetricsDTO.CourseCostRowDTO> perCourse = byCourse.entrySet().stream()
                .map(entry -> {
                    List<AiUsageEvent> events = entry.getValue();
                    double courseCostUsd = events.stream().mapToDouble(AiUsageEvent::getCostUsd).sum();
                    double courseCreationUsd = events.stream()
                            .filter(e -> e.getEndpoint() != null
                                    && CONTENT_CREATION_ENDPOINTS.contains(e.getEndpoint()))
                            .mapToDouble(AiUsageEvent::getCostUsd)
                            .sum();
                    long users = events.stream()
                            .map(AiUsageEvent::getUserId)
                            .filter(id -> id != null && !id.isBlank())
                            .distinct()
                            .count();
                    String courseId = entry.getKey();
                    String name = UNATTRIBUTED_COURSE_KEY.equals(courseId)
                            ? "Unattributed / platform"
                            : courseNames.getOrDefault(courseId, courseId);
                    return InvestorMetricsDTO.CourseCostRowDTO.builder()
                            .courseId(UNATTRIBUTED_COURSE_KEY.equals(courseId) ? null : courseId)
                            .courseName(name)
                            .totalCostUsd(round(courseCostUsd))
                            .totalCostInr(round(courseCostUsd * usdToInr))
                            .creationCostUsd(round(courseCreationUsd))
                            .learnerCostUsd(round(courseCostUsd - courseCreationUsd))
                            .distinctUsers(users)
                            .avgCostPerUserUsd(users > 0 ? round(courseCostUsd / users) : null)
                            .totalTokens(events.stream().mapToLong(AiUsageEvent::getTotalTokens).sum())
                            .build();
                })
                .sorted(Comparator.comparingDouble(InvestorMetricsDTO.CourseCostRowDTO::getTotalCostUsd).reversed())
                .collect(Collectors.toList());

        return InvestorMetricsDTO.CostSection.builder()
                .totalAiCostUsd(round(totalCostUsd))
                .totalAiCostInr(round(totalCostUsd * usdToInr))
                .totalTokens(totalTokens)
                .activeAiUsers(activeAiUsers)
                .avgCostPerActiveUserUsd(avgPerUserUsd)
                .avgCostPerActiveUserInr(avgPerUserUsd != null ? round(avgPerUserUsd * usdToInr) : null)
                .costPerLearningHourUsd(costPerHourUsd)
                .costPerLearningHourInr(costPerHourUsd != null ? round(costPerHourUsd * usdToInr) : null)
                .contentCreationCostUsd(round(creationCostUsd))
                .learnerServingCostUsd(round(learnerCostUsd))
                .perCourse(perCourse)
                .build();
    }

    private InvestorMetricsDTO.UsersSection buildUsersSection(List<User> learners,
                                                              List<UserLectureProgress> allLectureProgress,
                                                              DashboardStatsDTO dashboard) {
        double totalHours = allLectureProgress.stream()
                .mapToLong(p -> p.getTimeSpent() != null ? p.getTimeSpent() : 0)
                .sum() / 3600.0;
        long learnersWithActivity = allLectureProgress.stream()
                .filter(p -> p.getTimeSpent() != null && p.getTimeSpent() > 0)
                .map(UserLectureProgress::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .count();
        long onboarded = learners.stream()
                .filter(u -> Boolean.TRUE.equals(u.getOnboardingCompleted()))
                .count();
        return InvestorMetricsDTO.UsersSection.builder()
                .totalLearningHoursAllTime(round(totalHours))
                .learnersWithLearningActivity(learnersWithActivity)
                .avgLearningHoursPerLearner(learnersWithActivity > 0
                        ? round(totalHours / learnersWithActivity) : null)
                .onboardingCompletionRatePercent(!learners.isEmpty()
                        ? round(onboarded * 100.0 / learners.size()) : null)
                .averageTopicTimeSeconds(dashboard.getAverageTopicTimeSeconds())
                .build();
    }

    private InvestorMetricsDTO.TrafficSection buildTrafficSection(List<User> learners, LocalDateTime now) {
        long dau = userLoginEventRepository.countDistinctUsersSince(now.minusDays(1));
        long wau = userLoginEventRepository.countDistinctUsersSince(now.minusDays(7));
        long mau = userLoginEventRepository.countDistinctUsersSince(now.minusDays(30));
        long signups7 = learners.stream()
                .filter(u -> u.getCreatedAt() != null && u.getCreatedAt().isAfter(now.minusDays(7)))
                .count();
        long signups30 = learners.stream()
                .filter(u -> u.getCreatedAt() != null && u.getCreatedAt().isAfter(now.minusDays(30)))
                .count();
        return InvestorMetricsDTO.TrafficSection.builder()
                .dau(dau)
                .wau(wau)
                .mau(mau)
                .dauMauRatioPercent(mau > 0 ? round(dau * 100.0 / mau) : null)
                .signupsLast7Days(signups7)
                .signupsLast30Days(signups30)
                .totalLearners(learners.size())
                .activeLearners(learners.stream().filter(User::isActive).count())
                .build();
    }

    private InvestorMetricsDTO.ActivitySection buildActivitySection(List<AiUsageEvent> periodEvents,
                                                                    LocalDateTime periodStart,
                                                                    LocalDateTime periodEnd,
                                                                    DashboardStatsDTO dashboard) {
        List<Course> courses = courseRepository.findAll().stream()
                .filter(c -> !c.isDeleted())
                .collect(Collectors.toList());
        long coursesCreatedThisPeriod = courses.stream()
                .filter(c -> c.getCreatedAt() != null
                        && !c.getCreatedAt().isBefore(periodStart)
                        && !c.getCreatedAt().isAfter(periodEnd))
                .count();

        List<UserCourseEnrollment> enrollments = enrollmentRepository.findAll();
        Map<String, Long> byType = enrollments.stream()
                .collect(Collectors.groupingBy(
                        e -> e.getEnrollmentType() != null ? e.getEnrollmentType().name() : "UNKNOWN",
                        TreeMap::new,
                        Collectors.counting()));

        List<ModuleQuizAttempt> quizzes = moduleQuizAttemptRepository.findAll().stream()
                .filter(q -> inPeriod(q.getSubmittedAt(), periodStart, periodEnd))
                .collect(Collectors.toList());
        long quizPassed = quizzes.stream().filter(q -> Boolean.TRUE.equals(q.getPassed())).count();
        Double quizAvg = average(quizzes.stream()
                .map(ModuleQuizAttempt::getPercentage)
                .filter(Objects::nonNull)
                .collect(Collectors.toList()));

        List<ExamAttempt> exams = examAttemptRepository.findAll().stream()
                .filter(e -> inPeriod(e.getSubmittedAt(), periodStart, periodEnd))
                .collect(Collectors.toList());
        Double examAvg = average(exams.stream()
                .map(ExamAttempt::getPercentage)
                .filter(Objects::nonNull)
                .collect(Collectors.toList()));

        long doubts = periodEvents.stream()
                .filter(e -> e.getEndpoint() != null && DOUBT_ENDPOINTS.contains(e.getEndpoint()))
                .count();

        AiAnswerFeedbackService.HelpfulRate helpful =
                aiAnswerFeedbackService.helpfulRate(periodStart, periodEnd);

        return InvestorMetricsDTO.ActivitySection.builder()
                .totalCourses(courses.size())
                .coursesCreatedThisPeriod(coursesCreatedThisPeriod)
                .totalEnrollments(enrollments.size())
                .enrollmentsByType(new LinkedHashMap<>(byType))
                .averageCourseProgressPercent(dashboard.getAverageProgress())
                .quizzesTakenThisPeriod(quizzes.size())
                .quizPassRatePercent(!quizzes.isEmpty() ? round(quizPassed * 100.0 / quizzes.size()) : null)
                .quizAvgScorePercent(quizAvg)
                .examsTakenThisPeriod(exams.size())
                .examAvgScorePercent(examAvg)
                .doubtsResolvedThisPeriod(doubts)
                .averageQueryResponseTimeMs(dashboard.getAverageQueryResponseTimeMs())
                .aiHelpfulRatePercent(helpful.helpfulRatePercent())
                .aiFeedbackVotesThisPeriod(helpful.totalVotes())
                .aiFeedbackVotesAllTime(aiAnswerFeedbackService.totalVotes())
                .build();
    }

    private InvestorMetricsDTO.GrowthSection buildGrowthSection(List<User> learners,
                                                                LocalDateTime periodStart,
                                                                LocalDateTime periodEnd,
                                                                LocalDateTime now) {
        List<ReferralConversionEvent> conversions = referralConversionEventRepository.findAll();
        long conversionsThisPeriod = conversions.stream()
                .filter(c -> inPeriod(c.getCreatedAt(), periodStart, periodEnd))
                .count();
        long referredLearners = learners.stream()
                .filter(u -> u.getReferredBy() != null && !u.getReferredBy().isBlank())
                .count();

        Map<String, Long> sharesByChannel = referralShareEventRepository.findAll().stream()
                .collect(Collectors.groupingBy(
                        s -> s.getChannel() != null ? s.getChannel() : "OTHER",
                        TreeMap::new,
                        Collectors.counting()));
        Map<String, Long> courseSharesByPlatform = courseShareEventRepository.findAll().stream()
                .collect(Collectors.groupingBy(
                        s -> s.getPlatform() != null ? s.getPlatform() : "OTHER",
                        TreeMap::new,
                        Collectors.counting()));

        // Retention from raw login events, per-user.
        Map<String, List<LocalDateTime>> loginsByUser = userLoginEventRepository.findAll().stream()
                .filter(e -> e.getUserId() != null && e.getLoggedInAt() != null)
                .collect(Collectors.groupingBy(UserLoginEvent::getUserId,
                        Collectors.mapping(UserLoginEvent::getLoggedInAt, Collectors.toList())));

        Retention d7 = retention(learners, loginsByUser, now, 7, 37);
        Retention d30 = retention(learners, loginsByUser, now, 30, 60);

        return InvestorMetricsDTO.GrowthSection.builder()
                .referralConversionsAllTime(conversions.size())
                .referralConversionsThisPeriod(conversionsThisPeriod)
                .referralSignupSharePercent(!learners.isEmpty()
                        ? round(referredLearners * 100.0 / learners.size()) : null)
                .referralSharesByChannel(new LinkedHashMap<>(sharesByChannel))
                .courseSharesByPlatform(new LinkedHashMap<>(courseSharesByPlatform))
                .d7RetentionPercent(d7.ratePercent())
                .d7CohortSize(d7.cohortSize())
                .d30RetentionPercent(d30.ratePercent())
                .d30CohortSize(d30.cohortSize())
                .build();
    }

    /**
     * Rolling-cohort retention: learners who signed up between {@code maxAgeDays} and
     * {@code minAgeDays} ago, retained = any login at least {@code minAgeDays} after signup.
     */
    private Retention retention(List<User> learners,
                                Map<String, List<LocalDateTime>> loginsByUser,
                                LocalDateTime now, int minAgeDays, int maxAgeDays) {
        List<User> cohort = learners.stream()
                .filter(u -> u.getCreatedAt() != null)
                .filter(u -> u.getCreatedAt().isBefore(now.minusDays(minAgeDays))
                        && u.getCreatedAt().isAfter(now.minusDays(maxAgeDays)))
                .collect(Collectors.toList());
        if (cohort.isEmpty()) {
            return new Retention(0, null);
        }
        long retained = cohort.stream()
                .filter(u -> loginsByUser.getOrDefault(u.getId(), List.of()).stream()
                        .anyMatch(login -> login.isAfter(u.getCreatedAt().plusDays(minAgeDays))))
                .count();
        return new Retention(cohort.size(), round(retained * 100.0 / cohort.size()));
    }

    private record Retention(long cohortSize, Double ratePercent) {}

    private boolean inPeriod(LocalDateTime ts, LocalDateTime start, LocalDateTime end) {
        return ts != null && !ts.isBefore(start) && !ts.isAfter(end);
    }

    private Double average(List<Double> values) {
        if (values.isEmpty()) {
            return null;
        }
        return round(values.stream().mapToDouble(Double::doubleValue).average().orElse(0));
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
