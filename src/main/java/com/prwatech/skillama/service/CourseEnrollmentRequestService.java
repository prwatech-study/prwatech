package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.CourseCatalogItemDTO;
import com.prwatech.skillama.dto.CourseEnrollmentRequestDTO;
import com.prwatech.skillama.dto.CreateCourseEnrollmentRequestDTO;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.Course;
import com.prwatech.skillama.model.CourseEnrollmentRequest;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.model.UserCourseEnrollment;
import com.prwatech.skillama.repository.CourseEnrollmentRequestRepository;
import com.prwatech.skillama.repository.CourseRepository;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import com.prwatech.skillama.repository.UserCourseEnrollmentRepository;
import com.prwatech.skillama.util.IndiaTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Explore-catalog enrollment requests: learners browse ASSIGNABLE courses
 * (active, not archived) and request enrollment. Organization members are
 * approved by the org owner or org admin; individual (B2C) learners stay on
 * the Skillama admin queue. Learners never enroll themselves directly.
 */
@Service
@RequiredArgsConstructor
public class CourseEnrollmentRequestService {

    private final CourseEnrollmentRequestRepository requestRepository;
    private final CourseRepository courseRepository;
    private final SkillamaUserRepository userRepository;
    private final UserCourseEnrollmentRepository enrollmentRepository;
    private final UserCourseAccessService userCourseAccessService;

    /** Assignable = visible to learners: not archived and active. */
    private boolean isCatalogEligible(Course course) {
        return course != null && CourseService.isAvailableToLearner(course);
    }

    public List<CourseCatalogItemDTO> listCatalog(String userId) {
        Set<String> enrolledCourseIds = enrollmentRepository
                .findByUserIdAndStatus(userId, UserCourseEnrollment.EnrollmentStatus.ACTIVE)
                .stream()
                .map(UserCourseEnrollment::getCourseId)
                .collect(Collectors.toSet());

        // Popularity signal for "Recommended" ordering on the client.
        Map<String, Long> enrollmentCounts = enrollmentRepository.findAll().stream()
                .filter(e -> e.getStatus() == UserCourseEnrollment.EnrollmentStatus.ACTIVE)
                .collect(Collectors.groupingBy(UserCourseEnrollment::getCourseId, Collectors.counting()));

        // Latest request per course decides the card's status chip.
        Map<String, CourseEnrollmentRequest> latestRequestByCourse = requestRepository
                .findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .collect(Collectors.toMap(
                        CourseEnrollmentRequest::getCourseId,
                        Function.identity(),
                        (newer, older) -> newer));

        return courseRepository.findAll().stream()
                .filter(this::isCatalogEligible)
                .sorted(Comparator.comparing(
                        c -> c.getName() != null ? c.getName().toLowerCase() : ""))
                .map(course -> {
                    CourseEnrollmentRequest request = latestRequestByCourse.get(course.getId());
                    return CourseCatalogItemDTO.builder()
                            .courseId(course.getId())
                            .name(course.getName())
                            .description(course.getDescription())
                            .thumbnail(course.getThumbnail())
                            .enrolled(enrolledCourseIds.contains(course.getId()))
                            .requestStatus(request != null ? request.getStatus().name() : null)
                            .decisionReason(request != null
                                    && request.getStatus() == CourseEnrollmentRequest.RequestStatus.DENIED
                                    ? request.getDecisionReason() : null)
                            .enrollmentCount(enrollmentCounts.getOrDefault(course.getId(), 0L))
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public CourseEnrollmentRequest createRequest(String userId, CreateCourseEnrollmentRequestDTO body) {
        if (body == null || body.getCourseId() == null || body.getCourseId().isBlank()) {
            throw new IllegalArgumentException("courseId is required");
        }
        Course course = courseRepository.findById(body.getCourseId())
                .orElseThrow(() -> new ResourceNotFoundException("Course not found"));
        if (!isCatalogEligible(course)) {
            throw new IllegalArgumentException("This course is not open for enrollment requests");
        }
        if (userCourseAccessService.hasActiveEnrollment(userId, course.getId())) {
            throw new IllegalArgumentException("You are already enrolled in this course");
        }
        if (requestRepository.findFirstByUserIdAndCourseIdAndStatus(
                userId, course.getId(), CourseEnrollmentRequest.RequestStatus.PENDING).isPresent()) {
            throw new IllegalArgumentException("You already have a pending request for this course");
        }

        User requester = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        CourseEnrollmentRequest request = new CourseEnrollmentRequest();
        request.setUserId(userId);
        request.setCourseId(course.getId());
        request.setOrganizationId(blankToNull(requester.getOrganizationId()));
        request.setNote(body.getNote() != null ? body.getNote().trim() : null);
        return requestRepository.save(request);
    }

    public List<CourseEnrollmentRequestDTO> listMyRequests(String userId) {
        return toDtos(requestRepository.findByUserIdOrderByCreatedAtDesc(userId));
    }

    /** Skillama admin queue: individual / B2C learners only. */
    public List<CourseEnrollmentRequestDTO> listRequests(CourseEnrollmentRequest.RequestStatus status) {
        return toDtos(filterByOrganization(loadRequests(status), null));
    }

    /** Organization owner/admin queue for one tenant. */
    public List<CourseEnrollmentRequestDTO> listForOrganization(
            String organizationId, CourseEnrollmentRequest.RequestStatus status) {
        if (organizationId == null || organizationId.isBlank()) {
            throw new IllegalArgumentException("organizationId is required");
        }
        return toDtos(filterByOrganization(loadRequests(status), organizationId));
    }

    @Transactional
    public CourseEnrollmentRequestDTO approve(String requestId, String adminId) {
        CourseEnrollmentRequest request = requirePending(requestId);
        assertPlatformScoped(request);
        return markApproved(request, adminId);
    }

    @Transactional
    public CourseEnrollmentRequestDTO approveForOrganization(
            String requestId, String actorId, String organizationId) {
        CourseEnrollmentRequest request = requirePending(requestId);
        assertBelongsToOrganization(request, organizationId);
        return markApproved(request, actorId);
    }

    @Transactional
    public CourseEnrollmentRequestDTO deny(String requestId, String adminId, String reason) {
        CourseEnrollmentRequest request = requirePending(requestId);
        assertPlatformScoped(request);
        return markDenied(request, adminId, reason);
    }

    @Transactional
    public CourseEnrollmentRequestDTO denyForOrganization(
            String requestId, String actorId, String organizationId, String reason) {
        CourseEnrollmentRequest request = requirePending(requestId);
        assertBelongsToOrganization(request, organizationId);
        return markDenied(request, actorId, reason);
    }

    /** Pending individual / B2C requests on the Skillama admin queue. */
    public long pendingCount() {
        return filterByOrganization(
                requestRepository.findByStatusOrderByCreatedAtDesc(
                        CourseEnrollmentRequest.RequestStatus.PENDING),
                null).size();
    }

    private List<CourseEnrollmentRequest> loadRequests(CourseEnrollmentRequest.RequestStatus status) {
        return status != null
                ? requestRepository.findByStatusOrderByCreatedAtDesc(status)
                : requestRepository.findAllByOrderByCreatedAtDesc();
    }

    /**
     * {@code organizationId == null} keeps individual / B2C requests.
     * Legacy rows without the field still count as org-scoped when the user has an org.
     */
    private List<CourseEnrollmentRequest> filterByOrganization(
            List<CourseEnrollmentRequest> requests, String organizationId) {
        Map<String, User> users = usersById(requests);
        return requests.stream()
                .filter(r -> {
                    String resolved = resolvedOrganizationId(r, users.get(r.getUserId()));
                    if (organizationId == null) {
                        return resolved == null;
                    }
                    return organizationId.equals(resolved);
                })
                .collect(Collectors.toList());
    }

    private Map<String, User> usersById(List<CourseEnrollmentRequest> requests) {
        Set<String> userIds = requests.stream()
                .map(CourseEnrollmentRequest::getUserId)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return StreamSupport.stream(userRepository.findAllById(userIds).spliterator(), false)
                .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));
    }

    private String resolvedOrganizationId(CourseEnrollmentRequest request, User user) {
        String stamped = request != null ? blankToNull(request.getOrganizationId()) : null;
        if (stamped != null) {
            return stamped;
        }
        return user != null ? blankToNull(user.getOrganizationId()) : null;
    }

    private void assertPlatformScoped(CourseEnrollmentRequest request) {
        User requester = userRepository.findById(request.getUserId()).orElse(null);
        if (resolvedOrganizationId(request, requester) != null) {
            throw new IllegalStateException(
                    "Organization enrollment requests are approved by the organization owner or admin");
        }
    }

    private void assertBelongsToOrganization(CourseEnrollmentRequest request, String organizationId) {
        User requester = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        String resolved = resolvedOrganizationId(request, requester);
        if (resolved == null || !resolved.equals(organizationId)) {
            throw new IllegalStateException("Cross-organization access denied");
        }
    }

    private CourseEnrollmentRequestDTO markApproved(CourseEnrollmentRequest request, String actorId) {
        Course course = courseRepository.findById(request.getCourseId())
                .orElseThrow(() -> new ResourceNotFoundException("Course not found"));
        if (!isCatalogEligible(course)) {
            throw new IllegalStateException("Course is no longer available — deny the request instead");
        }
        userCourseAccessService.enrollIfAbsent(request.getUserId(), request.getCourseId(),
                UserCourseEnrollment.EnrollmentType.REQUEST_APPROVED);
        request.setStatus(CourseEnrollmentRequest.RequestStatus.APPROVED);
        request.setDecidedBy(actorId);
        request.setDecidedAt(IndiaTime.now());
        request.setUpdatedAt(IndiaTime.now());
        requestRepository.save(request);
        return toDtos(List.of(request)).get(0);
    }

    private CourseEnrollmentRequestDTO markDenied(
            CourseEnrollmentRequest request, String actorId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason is required");
        }
        request.setStatus(CourseEnrollmentRequest.RequestStatus.DENIED);
        request.setDecisionReason(reason.trim());
        request.setDecidedBy(actorId);
        request.setDecidedAt(IndiaTime.now());
        request.setUpdatedAt(IndiaTime.now());
        requestRepository.save(request);
        return toDtos(List.of(request)).get(0);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private CourseEnrollmentRequest requirePending(String requestId) {
        CourseEnrollmentRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Request not found"));
        if (request.getStatus() != CourseEnrollmentRequest.RequestStatus.PENDING) {
            throw new IllegalStateException("Request is already " + request.getStatus().name().toLowerCase());
        }
        return request;
    }

    private List<CourseEnrollmentRequestDTO> toDtos(List<CourseEnrollmentRequest> requests) {
        Set<String> userIds = requests.stream()
                .map(CourseEnrollmentRequest::getUserId).collect(Collectors.toSet());
        Set<String> courseIds = requests.stream()
                .map(CourseEnrollmentRequest::getCourseId).collect(Collectors.toSet());
        Map<String, User> users = StreamSupport
                .stream(userRepository.findAllById(userIds).spliterator(), false)
                .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));
        Map<String, Course> courses = StreamSupport
                .stream(courseRepository.findAllById(courseIds).spliterator(), false)
                .collect(Collectors.toMap(Course::getId, Function.identity(), (a, b) -> a));

        return requests.stream().map(r -> {
            User user = users.get(r.getUserId());
            Course course = courses.get(r.getCourseId());
            return CourseEnrollmentRequestDTO.builder()
                    .id(r.getId())
                    .userId(r.getUserId())
                    .userName(user != null ? user.getName() : null)
                    .userEmail(user != null ? user.getEmail() : null)
                    .organizationId(resolvedOrganizationId(r, user))
                    .courseId(r.getCourseId())
                    .courseName(course != null ? course.getName() : r.getCourseId())
                    .note(r.getNote())
                    .status(r.getStatus().name())
                    .decisionReason(r.getDecisionReason())
                    .decidedBy(r.getDecidedBy())
                    .decidedAt(r.getDecidedAt())
                    .createdAt(r.getCreatedAt())
                    .build();
        }).collect(Collectors.toList());
    }
}
