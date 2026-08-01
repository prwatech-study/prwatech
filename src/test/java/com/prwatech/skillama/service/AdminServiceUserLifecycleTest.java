package com.prwatech.skillama.service;

import com.prwatech.common.configuration.PasswordEncode;
import com.prwatech.skillama.dto.CreateUserRequest;
import com.prwatech.skillama.dto.UpdateUserRequest;
import com.prwatech.skillama.dto.UserDTO;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.AdminAuditLogRepository;
import com.prwatech.skillama.repository.CourseRepository;
import com.prwatech.skillama.repository.DeletedSkillamaUserRepository;
import com.prwatech.skillama.repository.IssueReportRepository;
import com.prwatech.skillama.repository.ModuleQuizAttemptRepository;
import com.prwatech.skillama.repository.QueryActivityLogRepository;
import com.prwatech.skillama.repository.ReviewRepository;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import com.prwatech.skillama.repository.UserCourseEnrollmentRepository;
import com.prwatech.skillama.repository.UserCourseProgressRepository;
import com.prwatech.skillama.repository.UserLectureProgressRepository;
import com.prwatech.skillama.repository.UserLoginEventRepository;
import com.prwatech.skillama.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminServiceUserLifecycleTest {

    @Mock private SkillamaUserRepository userRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private UserCourseEnrollmentRepository enrollmentRepository;
    @Mock private UserCourseProgressRepository progressRepository;
    @Mock private UserLectureProgressRepository lectureProgressRepository;
    @Mock private UserProfileRepository userProfileRepository;
    @Mock private QueryActivityLogRepository queryActivityLogRepository;
    @Mock private UserLoginEventRepository userLoginEventRepository;
    @Mock private ReviewRepository reviewRepository;
    @Mock private IssueReportRepository issueReportRepository;
    @Mock private UserCourseAccessService userCourseAccessService;
    @Mock private CourseAssignmentNotificationService courseAssignmentNotificationService;
    @Mock private PasswordEncode passwordEncode;
    @Mock private AdminAuditService adminAuditService;
    @Mock private DeletedSkillamaUserRepository deletedSkillamaUserRepository;
    @Mock private FreemiumService freemiumService;
    @Mock private AiUsageService aiUsageService;
    @Mock private ModuleQuizAttemptRepository moduleQuizAttemptRepository;
    @Mock private ModuleQuizService moduleQuizService;
    @Mock private AdminAuditLogRepository adminAuditLogRepository;

    @InjectMocks private AdminService service;

    private User owner() { return User.builder().id("owner").email("o@x.com").role(User.UserRole.OWNER).active(true).build(); }
    private User admin() { return User.builder().id("admin").email("a@x.com").role(User.UserRole.ADMIN).active(true).build(); }
    private User learner(String id) { return User.builder().id(id).email(id + "@x.com").role(User.UserRole.USER).active(true).build(); }

    @BeforeEach
    void setUp() {
        // convertToUserDTO reads these; keep them safe.
        when(progressRepository.findByUserId(any())).thenReturn(List.of());
        when(passwordEncode.getEncryptedPassword(any())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            if (u.getId() == null) {
                u.setId("new-user");
            }
            return u;
        });
    }

    private CreateUserRequest createRequest(User.UserRole role) {
        return new CreateUserRequest("New", "new@x.com", "pw", role, true, null);
    }

    // ---------- createUser ----------

    @Test
    void createUserUnknownCreatorThrows() {
        when(userRepository.findById("ghost")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> service.createUser(createRequest(User.UserRole.USER), "ghost"));
    }

    @Test
    void nonOwnerCannotCreateAdmin() {
        when(userRepository.findById("admin")).thenReturn(Optional.of(admin()));
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.createUser(createRequest(User.UserRole.ADMIN), "admin"));
        assertTrue(ex.getMessage().contains("Only OWNER"));
    }

    @Test
    void createUserRejectsDuplicateEmail() {
        when(userRepository.findById("owner")).thenReturn(Optional.of(owner()));
        when(userRepository.findByEmail("new@x.com")).thenReturn(Optional.of(learner("existing")));
        assertThrows(IllegalStateException.class,
                () -> service.createUser(createRequest(User.UserRole.USER), "owner"));
    }

    @Test
    void createUserSuccessSavesAndAudits() {
        when(userRepository.findById("owner")).thenReturn(Optional.of(owner()));
        when(userRepository.findByEmail("new@x.com")).thenReturn(Optional.empty());

        UserDTO dto = service.createUser(createRequest(User.UserRole.USER), "owner");

        assertEquals("new@x.com", dto.getEmail());
        assertEquals(User.UserRole.USER, dto.getRole());
        verify(passwordEncode).getEncryptedPassword("pw"); // password is hashed, never stored raw
        verify(adminAuditService).log(eq("owner"), eq(AdminAuditService.USER_CREATE), any(), any(), any(), any());
    }

    @Test
    void createAdminUserRequiresOwnerCaller() {
        when(userRepository.findById("admin")).thenReturn(Optional.of(admin()));
        assertThrows(RuntimeException.class,
                () -> service.createAdminUser(createRequest(User.UserRole.ADMIN), "admin"));
    }

    // ---------- deleteUser (soft-delete only) ----------

    @Test
    void hardDeleteIsDisabled() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.deleteUser("u2", "owner", true, "cleanup"));
        assertTrue(ex.getMessage().toLowerCase().contains("permanent delete is disabled"));
    }

    @Test
    void cannotDeleteOwner() {
        when(userRepository.findById("owner")).thenReturn(Optional.of(owner()));
        when(userRepository.findById("owner2")).thenReturn(Optional.of(
                User.builder().id("owner2").email("o2@x.com").role(User.UserRole.OWNER).build()));
        assertThrows(RuntimeException.class, () -> service.deleteUser("owner2", "owner", false, null));
    }

    @Test
    void adminCannotDeleteAnotherAdmin() {
        when(userRepository.findById("admin")).thenReturn(Optional.of(admin()));
        when(userRepository.findById("admin2")).thenReturn(Optional.of(
                User.builder().id("admin2").email("a2@x.com").role(User.UserRole.ADMIN).build()));
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.deleteUser("admin2", "admin", false, null));
        assertTrue(ex.getMessage().contains("Only OWNER"));
    }

    @Test
    void cannotDeleteOwnAccount() {
        when(userRepository.findById("admin")).thenReturn(Optional.of(admin()));
        assertThrows(RuntimeException.class, () -> service.deleteUser("admin", "admin", false, null));
    }

    @Test
    void deleteUserSoftDeactivatesLearnerAndAudits() {
        when(userRepository.findById("owner")).thenReturn(Optional.of(owner()));
        User target = learner("u2");
        when(userRepository.findById("u2")).thenReturn(Optional.of(target));

        service.deleteUser("u2", "owner", false, "spam");

        assertFalse(target.isActive()); // soft delete
        verify(userRepository).save(target);
        verify(userRepository, never()).delete(any());
        verify(adminAuditService).log(eq("owner"), eq(AdminAuditService.USER_DELETE), any(), eq("u2"), any(), any());
    }

    // ---------- updateUser role guards ----------

    @Test
    void nonOwnerCannotPromoteToAdmin() {
        when(userRepository.findById("target")).thenReturn(Optional.of(learner("target")));
        when(userRepository.findById("admin")).thenReturn(Optional.of(admin()));
        UpdateUserRequest req = new UpdateUserRequest(null, null, User.UserRole.ADMIN, null, null);
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.updateUser("target", req, "admin"));
        assertTrue(ex.getMessage().contains("Only OWNER"));
    }

    @Test
    void cannotChangeOwnerRole() {
        when(userRepository.findById("owner2")).thenReturn(Optional.of(
                User.builder().id("owner2").email("o2@x.com").role(User.UserRole.OWNER).build()));
        when(userRepository.findById("owner")).thenReturn(Optional.of(owner()));
        UpdateUserRequest req = new UpdateUserRequest(null, null, User.UserRole.USER, null, null);
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.updateUser("owner2", req, "owner"));
        assertTrue(ex.getMessage().contains("Cannot change OWNER role"));
    }

    // ---------- promoteUserToTester / demoteTesterToUser ----------

    @Test
    void adminCanPromoteUserToTester() {
        when(userRepository.findById("admin")).thenReturn(Optional.of(admin()));
        User target = learner("target");
        when(userRepository.findById("target")).thenReturn(Optional.of(target));

        UserDTO dto = service.promoteUserToTester("target", "admin");

        assertEquals(User.UserRole.TESTER, dto.getRole());
    }

    @Test
    void learnerCannotPromoteAnotherUserToTester() {
        when(userRepository.findById("learner")).thenReturn(Optional.of(learner("learner")));
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.promoteUserToTester("target", "learner"));
        assertTrue(ex.getMessage().contains("Admin access required"));
    }

    @Test
    void promoteToTesterRejectsNonUserTarget() {
        when(userRepository.findById("admin")).thenReturn(Optional.of(admin()));
        when(userRepository.findById("target")).thenReturn(Optional.of(
                User.builder().id("target").email("t@x.com").role(User.UserRole.ADMIN).build()));
        assertThrows(IllegalArgumentException.class,
                () -> service.promoteUserToTester("target", "admin"));
    }

    @Test
    void adminCanDemoteTesterToUser() {
        when(userRepository.findById("admin")).thenReturn(Optional.of(admin()));
        User target = User.builder().id("target").email("t@x.com").role(User.UserRole.TESTER).active(true).build();
        when(userRepository.findById("target")).thenReturn(Optional.of(target));

        UserDTO dto = service.demoteTesterToUser("target", "admin");

        assertEquals(User.UserRole.USER, dto.getRole());
    }

    @Test
    void demoteFromTesterRejectsNonTesterTarget() {
        when(userRepository.findById("admin")).thenReturn(Optional.of(admin()));
        when(userRepository.findById("target")).thenReturn(Optional.of(learner("target")));
        assertThrows(IllegalArgumentException.class,
                () -> service.demoteTesterToUser("target", "admin"));
    }
}
