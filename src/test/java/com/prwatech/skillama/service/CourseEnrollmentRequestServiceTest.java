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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CourseEnrollmentRequestServiceTest {

    @Mock private CourseEnrollmentRequestRepository requestRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private SkillamaUserRepository userRepository;
    @Mock private UserCourseEnrollmentRepository enrollmentRepository;
    @Mock private UserCourseAccessService userCourseAccessService;

    private CourseEnrollmentRequestService service;

    @BeforeEach
    void setUp() {
        service = new CourseEnrollmentRequestService(requestRepository, courseRepository,
                userRepository, enrollmentRepository, userCourseAccessService);
        when(requestRepository.save(any(CourseEnrollmentRequest.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findAllById(any())).thenReturn(List.of());
        when(courseRepository.findAllById(any())).thenReturn(List.of());
    }

    private Course course(String id, boolean active, boolean deleted) {
        Course c = Course.builder().id(id).name("Course " + id).active(active).build();
        if (deleted) {
            c.setDeletedAt(IndiaTime.now());
        }
        return c;
    }

    private CourseEnrollmentRequest pending(String userId, String courseId) {
        CourseEnrollmentRequest r = new CourseEnrollmentRequest();
        r.setId("r1");
        r.setUserId(userId);
        r.setCourseId(courseId);
        return r;
    }

    // ---------- catalog ----------

    @Test
    void catalogShowsOnlyAssignableCoursesWithEnrollmentAndRequestState() {
        when(courseRepository.findAll()).thenReturn(List.of(
                course("c1", true, false),   // enrolled
                course("c2", true, false),   // pending request
                course("c3", false, false),  // inactive → hidden
                course("c4", true, true)));  // archived → hidden
        UserCourseEnrollment enrollment = new UserCourseEnrollment();
        enrollment.setCourseId("c1");
        when(enrollmentRepository.findByUserIdAndStatus("u1", UserCourseEnrollment.EnrollmentStatus.ACTIVE))
                .thenReturn(List.of(enrollment));
        when(requestRepository.findByUserIdOrderByCreatedAtDesc("u1"))
                .thenReturn(List.of(pending("u1", "c2")));

        List<CourseCatalogItemDTO> catalog = service.listCatalog("u1");

        assertEquals(2, catalog.size());
        CourseCatalogItemDTO first = catalog.get(0); // sorted by name: c1 before c2
        assertTrue(first.isEnrolled());
        assertNull(first.getRequestStatus());
        CourseCatalogItemDTO second = catalog.get(1);
        assertFalse(second.isEnrolled());
        assertEquals("PENDING", second.getRequestStatus());
    }

    // ---------- create ----------

    @Test
    void createRejectsIneligibleAlreadyEnrolledAndDuplicatePending() {
        CreateCourseEnrollmentRequestDTO dto = new CreateCourseEnrollmentRequestDTO();
        dto.setCourseId("c1");

        when(courseRepository.findById("c1")).thenReturn(Optional.of(course("c1", false, false)));
        assertThrows(IllegalArgumentException.class, () -> service.createRequest("u1", dto));

        when(courseRepository.findById("c1")).thenReturn(Optional.of(course("c1", true, false)));
        when(userCourseAccessService.hasActiveEnrollment("u1", "c1")).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> service.createRequest("u1", dto));

        when(userCourseAccessService.hasActiveEnrollment("u1", "c1")).thenReturn(false);
        when(requestRepository.findFirstByUserIdAndCourseIdAndStatus(
                "u1", "c1", CourseEnrollmentRequest.RequestStatus.PENDING))
                .thenReturn(Optional.of(pending("u1", "c1")));
        assertThrows(IllegalArgumentException.class, () -> service.createRequest("u1", dto));

        when(courseRepository.findById("missing")).thenReturn(Optional.empty());
        dto.setCourseId("missing");
        assertThrows(ResourceNotFoundException.class, () -> service.createRequest("u1", dto));
    }

    @Test
    void createSavesPendingRequest() {
        when(courseRepository.findById("c1")).thenReturn(Optional.of(course("c1", true, false)));
        when(userCourseAccessService.hasActiveEnrollment("u1", "c1")).thenReturn(false);
        when(requestRepository.findFirstByUserIdAndCourseIdAndStatus(any(), any(), any()))
                .thenReturn(Optional.empty());

        CreateCourseEnrollmentRequestDTO dto = new CreateCourseEnrollmentRequestDTO();
        dto.setCourseId("c1");
        dto.setNote("  please  ");

        CourseEnrollmentRequest saved = service.createRequest("u1", dto);

        assertEquals(CourseEnrollmentRequest.RequestStatus.PENDING, saved.getStatus());
        assertEquals("please", saved.getNote());
    }

    // ---------- approve / deny ----------

    @Test
    void approveEnrollsWithRequestApprovedTypeAndMarksApproved() {
        CourseEnrollmentRequest request = pending("u1", "c1");
        when(requestRepository.findById("r1")).thenReturn(Optional.of(request));
        when(courseRepository.findById("c1")).thenReturn(Optional.of(course("c1", true, false)));

        CourseEnrollmentRequestDTO result = service.approve("r1", "admin1");

        verify(userCourseAccessService).enrollIfAbsent(
                eq("u1"), eq("c1"), eq(UserCourseEnrollment.EnrollmentType.REQUEST_APPROVED));
        assertEquals("APPROVED", result.getStatus());
        assertEquals("admin1", result.getDecidedBy());
    }

    @Test
    void approveRefusesWhenCourseNoLongerAvailable() {
        when(requestRepository.findById("r1")).thenReturn(Optional.of(pending("u1", "c1")));
        when(courseRepository.findById("c1")).thenReturn(Optional.of(course("c1", false, false)));

        assertThrows(IllegalStateException.class, () -> service.approve("r1", "admin1"));
        verify(userCourseAccessService, never()).enrollIfAbsent(any(), any(), any());
    }

    @Test
    void decidedRequestsCannotBeReDecided() {
        CourseEnrollmentRequest approved = pending("u1", "c1");
        approved.setStatus(CourseEnrollmentRequest.RequestStatus.APPROVED);
        when(requestRepository.findById("r1")).thenReturn(Optional.of(approved));

        assertThrows(IllegalStateException.class, () -> service.approve("r1", "admin1"));
        assertThrows(IllegalStateException.class, () -> service.deny("r1", "admin1", "reason"));
    }

    @Test
    void denyRequiresReasonAndRecordsDecision() {
        when(requestRepository.findById("r1")).thenReturn(Optional.of(pending("u1", "c1")));

        assertThrows(IllegalArgumentException.class, () -> service.deny("r1", "admin1", "  "));

        CourseEnrollmentRequestDTO result = service.deny("r1", "admin1", "Not in your plan");
        assertEquals("DENIED", result.getStatus());
        assertEquals("Not in your plan", result.getDecisionReason());
        verify(userCourseAccessService, never()).enrollIfAbsent(any(), any(), any());
    }
}
