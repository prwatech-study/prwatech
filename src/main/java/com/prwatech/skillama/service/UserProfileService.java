package com.prwatech.skillama.service;

import com.prwatech.common.exception.NotFoundException;
import com.prwatech.skillama.dto.*;
import com.prwatech.skillama.model.Course;
import com.prwatech.skillama.model.CourseCurriculum;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.model.UserProfile;
import com.prwatech.skillama.repository.CourseRepository;
import com.prwatech.skillama.repository.CourseCurriculumRepository;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import com.prwatech.skillama.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import com.prwatech.skillama.util.IndiaTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserProfileService {
    
    private static final int MAX_GUEST_QUESTIONS = 5;
    private static final int GUEST_SESSION_EXPIRY_DAYS = 7;
    
    private final UserProfileRepository userProfileRepository;
    private final CourseRepository courseRepository;
    private final CourseCurriculumRepository curriculumRepository;
    private final CourseService courseService;
    private final FreemiumService freemiumService;
    private final SkillamaUserRepository userRepository;
    private final MongoTemplate skillamaMongoTemplate;
    private final UserCourseService userCourseService;
    private final ModuleQuizService moduleQuizService;
    
    // ========== Session Management ==========
    
    /**
     * Initialize guest session for non-logged-in users
     */
    public Map<String, Object> initializeGuestSession(InitGuestSessionRequestDTO request) {
        String sessionId = "guest-session-" + UUID.randomUUID().toString();
        LocalDateTime expiresAt = IndiaTime.now().plusDays(GUEST_SESSION_EXPIRY_DAYS);
        
        // Get guest course
        Course guestCourse = courseService.getGuestCourseOrThrow();
        
        UserProfile profile = UserProfile.builder()
                .sessionId(sessionId)
                .userId(null)
                .isGuest(true)
                .accessibleCourses(List.of(guestCourse.getId()))
                .currentCourseId(guestCourse.getId())
                .completedLectures(new ArrayList<>())
                .inProgressLectures(new ArrayList<>())
                .chatInteractions(new ArrayList<>())
                .totalQuestionsAsked(0)
                .createdAt(IndiaTime.now())
                .updatedAt(IndiaTime.now())
                .lastActivityAt(IndiaTime.now())
                .sessionExpiresAt(expiresAt)
                .build();
        
        profile = userProfileRepository.save(profile);
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("sessionId", sessionId);
        response.put("sessionExpiresAt", expiresAt);
        
        // Build initial feature access
        FeatureAccessDTO features = buildFeatureAccess(profile, null);
        Map<String, Object> profileData = new HashMap<>();
        profileData.put("isGuest", true);
        profileData.put("accessibleCourses", List.of(guestCourse.getId()));
        profileData.put("currentCourseId", guestCourse.getId());
        profileData.put("features", features);
        response.put("profile", profileData);
        
        return response;
    }
    
    /**
     * Get or create user profile by session ID or user ID
     */
    public UserProfile getOrCreateProfile(String sessionId, String userId) {
        if (userId != null) {
            return userProfileRepository.findByUserId(userId)
                    .map(this::ensureRegisteredProfile)
                    .orElseGet(() -> createUserProfile(userId));
        }
        
        if (sessionId != null) {
            return userProfileRepository.findBySessionId(sessionId)
                    .orElseGet(() -> {
                        // Create new guest session
                        Course guestCourse = courseService.getGuestCourseOrThrow();
                        LocalDateTime expiresAt = IndiaTime.now().plusDays(GUEST_SESSION_EXPIRY_DAYS);
                        
                        return UserProfile.builder()
                                .sessionId(sessionId)
                                .userId(null)
                                .isGuest(true)
                                .accessibleCourses(List.of(guestCourse.getId()))
                                .currentCourseId(guestCourse.getId())
                                .completedLectures(new ArrayList<>())
                                .inProgressLectures(new ArrayList<>())
                                .chatInteractions(new ArrayList<>())
                                .totalQuestionsAsked(0)
                                .createdAt(IndiaTime.now())
                                .updatedAt(IndiaTime.now())
                                .lastActivityAt(IndiaTime.now())
                                .sessionExpiresAt(expiresAt)
                                .build();
                    });
        }
        
        throw new NotFoundException("Session ID or User ID required");
    }
    
    private UserProfile ensureRegisteredProfile(UserProfile profile) {
        if (Boolean.TRUE.equals(profile.getIsGuest())) {
            profile.setIsGuest(false);
            profile.setUpdatedAt(IndiaTime.now());
            return userProfileRepository.save(profile);
        }
        return profile;
    }

    private UserProfile createUserProfile(String userId) {
        String sessionId = "user-session-" + UUID.randomUUID().toString();

        return UserProfile.builder()
                .userId(userId)
                .sessionId(sessionId)
                .isGuest(false)
                .accessibleCourses(new ArrayList<>())
                .completedLectures(new ArrayList<>())
                .inProgressLectures(new ArrayList<>())
                .chatInteractions(new ArrayList<>())
                .totalQuestionsAsked(0)
                .createdAt(IndiaTime.now())
                .updatedAt(IndiaTime.now())
                .lastActivityAt(IndiaTime.now())
                .build();
    }
    
    // ========== Access Control ==========
    
    /**
     * Get complete access control information for user
     */
    public AccessControlResponseDTO getAccessControl(String sessionId, String userId, String courseId) {
        UserProfile profile = getOrCreateProfile(sessionId, userId);
        
        // Use provided courseId or profile's current course
        String targetCourseId = courseId != null ? courseId : profile.getCurrentCourseId();
        if (targetCourseId == null) {
            if (userId != null) {
                throw new NotFoundException(
                        "courseId is required. No active course on profile for user: " + userId);
            }
            Course guestCourse = courseService.getGuestCourseOrThrow();
            targetCourseId = guestCourse.getId();
            profile.setCurrentCourseId(targetCourseId);
            userProfileRepository.save(profile);
        }
        
        // Validate course exists - store in final variable for lambda
        final String finalCourseId = targetCourseId;
        Course course = courseRepository.findById(finalCourseId)
                .orElseThrow(() -> new NotFoundException("Course not found with ID: " + finalCourseId));
        
        List<CourseCurriculum> curriculum = courseService.getCurriculumByCourseIdOrdered(targetCourseId, false, false);
        
        // Build module access
        List<ModuleAccessDTO> modules = buildModuleAccess(profile, curriculum, targetCourseId);
        
        User user = profile.getUserId() != null
                ? userRepository.findById(profile.getUserId()).orElse(null)
                : null;

        FeatureAccessDTO features = buildFeatureAccess(profile, user);
        ProgressSummaryDTO progress = buildProgressSummary(profile, curriculum, targetCourseId);
        QueryCreditsDTO queryCredits = user != null && !freemiumService.isLegacyUser(user)
                ? freemiumService.getQueryCredits(user)
                : null;

        return AccessControlResponseDTO.builder()
                .userId(profile.getUserId())
                .sessionId(profile.getSessionId())
                .isGuest(profile.getIsGuest())
                .courseId(targetCourseId)
                .courseName(course.getName())
                .planTier(user != null && user.getPlanTier() != null ? user.getPlanTier() : null)
                .modules(modules)
                .features(features)
                .progress(progress)
                .queryCredits(queryCredits)
                .build();
    }
    
    private List<ModuleAccessDTO> buildModuleAccess(UserProfile profile, List<CourseCurriculum> curriculum, String courseId) {
        List<ModuleAccessDTO> modules = new ArrayList<>();
        
        for (int i = 0; i < curriculum.size(); i++) {
            CourseCurriculum module = curriculum.get(i);
            ModuleAccessDTO moduleAccess = buildModuleAccessDTO(profile, module, i, courseId, curriculum);
            modules.add(moduleAccess);
        }
        
        return modules;
    }
    
    private ModuleAccessDTO buildModuleAccessDTO(UserProfile profile, CourseCurriculum module, int moduleIndex, 
                                                  String courseId, List<CourseCurriculum> allModules) {
        // For guest users: Show all modules but lock all except first module (first module's first lecture is unlocked)
        // For logged-in users: Progressive unlocking based on completion (lectures + module quiz)
        boolean isModuleAccessible;
        boolean isModuleLocked;
        String lockReason = null;
        
        if (profile.getIsGuest()) {
            // Guest users: First module is accessible (but only first lecture is unlocked), rest are locked
            isModuleAccessible = moduleIndex == 0;
            isModuleLocked = moduleIndex > 0;
            if (isModuleLocked) {
                lockReason = "Login required to access additional modules";
            }
        } else {
            // Logged-in users: Progressive unlocking
            if (moduleIndex == 0) {
                isModuleAccessible = true;
                isModuleLocked = false;
            } else {
                CourseCurriculum previousModule = allModules.get(moduleIndex - 1);
                boolean previousModuleCompleted = isModuleCompletedForUnlock(
                        profile, previousModule, courseId);
                isModuleAccessible = previousModuleCompleted;
                isModuleLocked = !previousModuleCompleted;
                if (isModuleLocked) {
                    boolean lecturesDone = isModuleLecturesCompleted(profile, previousModule, courseId);
                    if (lecturesDone
                            && !moduleQuizService.hasPassedModuleQuiz(
                                    profile, courseId, previousModule.getModuleName())) {
                        lockReason = "Complete the module quiz to unlock";
                    } else {
                        lockReason = "Previous module must be completed";
                    }
                }
            }
        }

        boolean moduleLecturesDone = isModuleLecturesCompleted(profile, module, courseId);
        boolean quizPassed = moduleQuizService.hasPassedModuleQuiz(profile, courseId, module.getModuleName());
        Integer quizBestScore = moduleQuizService.getBestQuizScore(profile, courseId, module.getModuleName());
        Boolean quizRequired = moduleLecturesDone && !quizPassed && !profile.getIsGuest();
        String quizLockReason = null;
        if (Boolean.TRUE.equals(quizRequired)) {
            quizLockReason = "Pass the module quiz (70%+) to continue";
        }
        
        // Build lecture access
        List<LectureAccessDTO> lectures = new ArrayList<>();
        if (module.getSubmodules() != null) {
            for (int j = 0; j < module.getSubmodules().size(); j++) {
                CourseCurriculum.Submodule submodule = module.getSubmodules().get(j);
                LectureAccessDTO lectureAccess = buildLectureAccessDTO(profile, submodule, module, 
                                                                        moduleIndex, j, courseId, allModules);
                lectures.add(lectureAccess);
            }
        }
        
        return ModuleAccessDTO.builder()
                .moduleId(module.getId())
                .moduleName(module.getModuleName())
                .moduleIndex(moduleIndex)
                .isAccessible(isModuleAccessible && !isModuleLocked)
                .isLocked(isModuleLocked)
                .lockReason(lockReason)
                .lectures(lectures)
                .quizRequired(quizRequired)
                .quizPassed(quizPassed)
                .quizBestScore(quizBestScore)
                .quizLockReason(quizLockReason)
                .build();
    }
    
    private LectureAccessDTO buildLectureAccessDTO(UserProfile profile, CourseCurriculum.Submodule submodule,
                                                   CourseCurriculum module, int moduleIndex, int lectureIndex,
                                                   String courseId, List<CourseCurriculum> allModules) {
        String lectureLabel = submodule.getLabel();
        
        boolean isAccessible;
        boolean isLocked;
        String lockReason = null;
        
        if (profile.getIsGuest()) {
            // Guest users: Only first lecture (first submodule of first module) is unlocked
            // All other lectures are locked to create "teaser" effect
            if (moduleIndex == 0 && lectureIndex == 0) {
                isAccessible = true;
                isLocked = false;
            } else {
                isAccessible = false;
                isLocked = true;
                if (moduleIndex == 0) {
                    lockReason = "Complete previous lectures to unlock this content";
                } else {
                    lockReason = "Login required to access additional modules";
                }
            }
        } else {
            // Logged-in users: Progressive unlocking
            // Rule 1: First lecture in first module is always accessible
            if (moduleIndex == 0 && lectureIndex == 0) {
                isAccessible = true;
                isLocked = false;
            } else {
                // Rule 2: Progressive unlocking - previous lecture must be completed
                CourseCurriculum.Submodule previousSubmodule = getPreviousSubmodule(module, lectureIndex, allModules, moduleIndex);
                if (previousSubmodule != null) {
                    boolean previousCompleted = isLectureCompleted(profile, previousSubmodule.getLabel(), courseId);
                    if (previousCompleted) {
                        isAccessible = true;
                        isLocked = false;
                    } else {
                        isAccessible = false;
                        isLocked = true;
                        lockReason = "Previous lecture '" + previousSubmodule.getLabel() + "' must be completed first";
                    }
                } else {
                    isAccessible = false;
                    isLocked = true;
                    lockReason = "Previous lecture must be completed first";
                }
            }

            if (!isLocked && moduleIndex > 0 && isFirstEnabledLectureInModule(module, lectureIndex)) {
                CourseCurriculum previousModule = allModules.get(moduleIndex - 1);
                if (!isModuleCompletedForUnlock(profile, previousModule, courseId)) {
                    isAccessible = false;
                    isLocked = true;
                    boolean lecturesDone = isModuleLecturesCompleted(profile, previousModule, courseId);
                    if (lecturesDone
                            && !moduleQuizService.hasPassedModuleQuiz(
                                    profile, courseId, previousModule.getModuleName())) {
                        lockReason = "Complete the module quiz to unlock";
                    } else {
                        lockReason = "Previous module must be completed";
                    }
                }
            }
        }
        
        boolean isCompleted = isLectureCompleted(profile, lectureLabel, courseId);
        boolean isInProgress = isLectureInProgress(profile, lectureLabel, courseId);
        Integer completionPercentage = getLectureProgress(profile, lectureLabel, courseId);
        LocalDateTime completedAt = getLectureCompletedAt(profile, lectureLabel, courseId);
        LocalDateTime unlockedAt = getLectureUnlockedAt(profile, lectureLabel);

        // Completed lectures stay accessible after reload even if unlock chain metadata is stale.
        if (isCompleted) {
            isAccessible = true;
            isLocked = false;
            lockReason = null;
        }
        
        return LectureAccessDTO.builder()
                .lectureLabel(lectureLabel)
                .lectureId(submodule.getLabel()) // Using label as ID
                .isAccessible(isAccessible && !isLocked)
                .isLocked(isLocked)
                .isCompleted(isCompleted)
                .isInProgress(isInProgress)
                .lockReason(lockReason)
                .completionPercentage(completionPercentage)
                .unlockedAt(unlockedAt)
                .completedAt(completedAt)
                .build();
    }
    
    private CourseCurriculum.Submodule getPreviousSubmodule(CourseCurriculum currentModule, int currentIndex,
                                                           List<CourseCurriculum> allModules, int moduleIndex) {
        if (currentIndex > 0) {
            // Previous submodule in same module
            return currentModule.getSubmodules().get(currentIndex - 1);
        } else if (moduleIndex > 0) {
            // First submodule of previous module
            CourseCurriculum previousModule = allModules.get(moduleIndex - 1);
            if (previousModule.getSubmodules() != null && !previousModule.getSubmodules().isEmpty()) {
                return previousModule.getSubmodules().get(previousModule.getSubmodules().size() - 1);
            }
        }
        return null;
    }
    
    private boolean isModuleLecturesCompleted(UserProfile profile, CourseCurriculum module, String courseId) {
        if (module.getSubmodules() == null || module.getSubmodules().isEmpty()) {
            return false;
        }

        for (CourseCurriculum.Submodule submodule : module.getSubmodules()) {
            if (submodule.getEnabled() != null && !submodule.getEnabled()) {
                continue;
            }
            if (!isLectureCompleted(profile, submodule.getLabel(), courseId)) {
                return false;
            }
        }
        return true;
    }

    private boolean isModuleCompletedForUnlock(
            UserProfile profile, CourseCurriculum module, String courseId) {
        if (!isModuleLecturesCompleted(profile, module, courseId)) {
            return false;
        }
        return moduleQuizService.hasPassedModuleQuiz(profile, courseId, module.getModuleName());
    }

    private boolean isModuleCompleted(UserProfile profile, CourseCurriculum module, String courseId) {
        return isModuleLecturesCompleted(profile, module, courseId);
    }

    private boolean isFirstEnabledLectureInModule(CourseCurriculum module, int lectureIndex) {
        if (module.getSubmodules() == null || module.getSubmodules().isEmpty()) {
            return lectureIndex == 0;
        }
        for (int i = 0; i < module.getSubmodules().size(); i++) {
            CourseCurriculum.Submodule sub = module.getSubmodules().get(i);
            if (sub.getEnabled() != null && !sub.getEnabled()) {
                continue;
            }
            return i == lectureIndex;
        }
        return false;
    }
    
    private boolean isLectureCompleted(UserProfile profile, String lectureLabel, String courseId) {
        return profile.getCompletedLectures().stream()
                .anyMatch(cl -> cl.getLectureLabel().equals(lectureLabel) && cl.getCourseId().equals(courseId));
    }
    
    private boolean isLectureInProgress(UserProfile profile, String lectureLabel, String courseId) {
        return profile.getInProgressLectures().stream()
                .anyMatch(il -> il.getLectureLabel().equals(lectureLabel) && il.getCourseId().equals(courseId));
    }
    
    private Integer getLectureProgress(UserProfile profile, String lectureLabel, String courseId) {
        return profile.getInProgressLectures().stream()
                .filter(il -> il.getLectureLabel().equals(lectureLabel) && il.getCourseId().equals(courseId))
                .findFirst()
                .map(UserProfile.InProgressLecture::getProgressPercentage)
                .orElse(0);
    }
    
    private LocalDateTime getLectureCompletedAt(UserProfile profile, String lectureLabel, String courseId) {
        return profile.getCompletedLectures().stream()
                .filter(cl -> cl.getLectureLabel().equals(lectureLabel) && cl.getCourseId().equals(courseId))
                .findFirst()
                .map(UserProfile.CompletedLecture::getCompletedAt)
                .orElse(null);
    }
    
    private LocalDateTime getLectureUnlockedAt(UserProfile profile, String lectureLabel) {
        if (profile.getUnlockedLectures().contains(lectureLabel)) {
            return IndiaTime.now(); // Simplified - could track actual unlock time
        }
        return null;
    }
    
    private FeatureAccessDTO buildFeatureAccess(UserProfile profile, User user) {
        FeatureAccessDTO.ChatFeatureDTO chatFeature = buildChatFeatureAccess(profile, user);
        FeatureAccessDTO.CodeExecutionFeatureDTO codeExecutionFeature = buildCodeExecutionAccess(profile, user);
        FeatureAccessDTO.DebugFeatureDTO debugFeature = buildDebugAccess(profile, user);

        return FeatureAccessDTO.builder()
                .chat(chatFeature)
                .codeExecution(codeExecutionFeature)
                .debug(debugFeature)
                .build();
    }

    private FeatureAccessDTO.CodeExecutionFeatureDTO buildCodeExecutionAccess(UserProfile profile, User user) {
        if (profile.getIsGuest()) {
            return FeatureAccessDTO.CodeExecutionFeatureDTO.builder()
                    .enabled(false)
                    .accessible(false)
                    .reason("Login required to access Code Execution")
                    .build();
        }
        if (user == null) {
            return FeatureAccessDTO.CodeExecutionFeatureDTO.builder()
                    .enabled(false)
                    .accessible(false)
                    .reason("LOGIN_REQUIRED")
                    .build();
        }
        if (freemiumService.isUnlimited(user) || freemiumService.hasModule(user, "Code Execution")) {
            return FeatureAccessDTO.CodeExecutionFeatureDTO.builder()
                    .enabled(true)
                    .accessible(true)
                    .build();
        }
        return FeatureAccessDTO.CodeExecutionFeatureDTO.builder()
                .enabled(false)
                .accessible(false)
                .reason("FREEMIUM_UPGRADE")
                .build();
    }

    private FeatureAccessDTO.DebugFeatureDTO buildDebugAccess(UserProfile profile, User user) {
        if (profile.getIsGuest()) {
            return FeatureAccessDTO.DebugFeatureDTO.builder()
                    .enabled(false)
                    .accessible(false)
                    .reason("Login required to access Debug")
                    .build();
        }
        if (user == null) {
            return FeatureAccessDTO.DebugFeatureDTO.builder()
                    .enabled(false)
                    .accessible(false)
                    .reason("LOGIN_REQUIRED")
                    .build();
        }
        if (freemiumService.isUnlimited(user) || freemiumService.hasModule(user, "Debug")) {
            return FeatureAccessDTO.DebugFeatureDTO.builder()
                    .enabled(true)
                    .accessible(true)
                    .build();
        }
        return FeatureAccessDTO.DebugFeatureDTO.builder()
                .enabled(false)
                .accessible(false)
                .reason("FREEMIUM_UPGRADE")
                .build();
    }

    private FeatureAccessDTO.ChatFeatureDTO buildChatFeatureAccess(UserProfile profile, User user) {
        if (profile.getIsGuest()) {
            int questionsRemaining = Math.max(0, MAX_GUEST_QUESTIONS - profile.getTotalQuestionsAsked());
            boolean limitReached = questionsRemaining <= 0;

            return FeatureAccessDTO.ChatFeatureDTO.builder()
                    .enabled(!limitReached)
                    .accessible(!limitReached)
                    .questionsRemaining(questionsRemaining)
                    .limitReached(limitReached)
                    .build();
        }

        if (user == null) {
            return FeatureAccessDTO.ChatFeatureDTO.builder()
                    .enabled(false)
                    .accessible(false)
                    .limitReached(true)
                    .questionsRemaining(0)
                    .build();
        }

        if (freemiumService.isUnlimited(user)) {
            return FeatureAccessDTO.ChatFeatureDTO.builder()
                    .enabled(true)
                    .accessible(true)
                    .questionsRemaining(null)
                    .limitReached(false)
                    .build();
        }

        int remaining = freemiumService.remainingQueries(user);
        boolean limitReached = remaining <= 0;
        return FeatureAccessDTO.ChatFeatureDTO.builder()
                .enabled(!limitReached && freemiumService.hasModule(user, "Ai-Tutor"))
                .accessible(!limitReached && freemiumService.hasModule(user, "Ai-Tutor"))
                .questionsRemaining(remaining)
                .limitReached(limitReached)
                .build();
    }
    
    /**
     * Progress counts are scoped to {@code courseId} so switching courses does not leak completion %.
     */
    private ProgressSummaryDTO buildProgressSummary(
            UserProfile profile, List<CourseCurriculum> curriculum, String courseId) {
        int totalLectures = CourseService.countEnabledLectures(curriculum);

        int completedLectures = (int) profile.getCompletedLectures().stream()
                .filter(cl -> courseId != null && courseId.equals(cl.getCourseId()))
                .count();
        int inProgressLectures = (int) profile.getInProgressLectures().stream()
                .filter(il -> courseId != null && courseId.equals(il.getCourseId()))
                .count();
        int lockedLectures = Math.max(0, totalLectures - completedLectures - inProgressLectures);

        int totalModuleQuizzes = countEnabledModules(curriculum);
        int passedModuleQuizzes = countPassedModuleQuizzesForCourse(profile, courseId);
        int pendingModuleQuizzes = countPendingModuleQuizzes(profile, curriculum, courseId);

        int completionDenominator = totalLectures + totalModuleQuizzes;
        int completionNumerator = completedLectures + passedModuleQuizzes;
        int completionPercentage = completionDenominator > 0
                ? Math.min(100, (completionNumerator * 100) / completionDenominator)
                : 0;

        return ProgressSummaryDTO.builder()
                .totalLectures(totalLectures)
                .completedLectures(completedLectures)
                .inProgressLectures(inProgressLectures)
                .lockedLectures(lockedLectures)
                .completionPercentage(completionPercentage)
                .totalModuleQuizzes(totalModuleQuizzes)
                .passedModuleQuizzes(passedModuleQuizzes)
                .pendingModuleQuizzes(pendingModuleQuizzes)
                .build();
    }

    private int countEnabledModules(List<CourseCurriculum> curriculum) {
        if (curriculum == null) {
            return 0;
        }
        int count = 0;
        for (CourseCurriculum module : curriculum) {
            if (module.getSubmodules() == null) {
                continue;
            }
            boolean hasEnabled = module.getSubmodules().stream()
                    .anyMatch(sub -> sub.getEnabled() == null || sub.getEnabled());
            if (hasEnabled) {
                count++;
            }
        }
        return count;
    }

    private int countPassedModuleQuizzesForCourse(UserProfile profile, String courseId) {
        if (profile.getPassedModuleQuizzes() == null || courseId == null) {
            return 0;
        }
        return (int) profile.getPassedModuleQuizzes().stream()
                .filter(pq -> courseId.equals(pq.getCourseId()))
                .count();
    }

    private int countPendingModuleQuizzes(
            UserProfile profile, List<CourseCurriculum> curriculum, String courseId) {
        if (curriculum == null || profile.getIsGuest()) {
            return 0;
        }
        int pending = 0;
        for (CourseCurriculum module : curriculum) {
            if (!isModuleLecturesCompleted(profile, module, courseId)) {
                continue;
            }
            if (!moduleQuizService.hasPassedModuleQuiz(profile, courseId, module.getModuleName())) {
                pending++;
            }
        }
        return pending;
    }
    
    // ========== Lecture Tracking ==========
    
    /**
     * Mark lecture as completed
     */
    public Map<String, Object> completeLecture(String sessionId, String userId, CompleteLectureRequestDTO request) {
        UserProfile profile = getOrCreateProfile(sessionId, userId);
        
        String lectureLabel = request.getLectureLabel();
        String courseId = request.getCourseId();
        
        // Remove from in-progress if exists
        profile.getInProgressLectures().removeIf(il -> 
            il.getLectureLabel().equals(lectureLabel) && il.getCourseId().equals(courseId));
        
        // Add to completed if not already completed
        boolean alreadyCompleted = profile.getCompletedLectures().stream()
                .anyMatch(cl -> cl.getLectureLabel().equals(lectureLabel) && cl.getCourseId().equals(courseId));
        
        if (!alreadyCompleted) {
            UserProfile.CompletedLecture completedLecture = UserProfile.CompletedLecture.builder()
                    .lectureLabel(lectureLabel)
                    .courseId(courseId)
                    .moduleName(request.getModuleName())
                    .completedAt(request.getCompletedAt() != null ? request.getCompletedAt() : IndiaTime.now())
                    .timeSpent(request.getTimeSpent())
                    .completionPercentage(request.getCompletionPercentage() != null ? request.getCompletionPercentage() : 100)
                    .build();
            
            profile.getCompletedLectures().add(completedLecture);
            
            // Unlock next lecture
            List<String> unlocked = unlockNextLectures(profile, lectureLabel, courseId);
            profile.getUnlockedLectures().addAll(unlocked);
        }
        
        profile.setLastActivityAt(IndiaTime.now());
        profile.setUpdatedAt(IndiaTime.now());
        userProfileRepository.save(profile);

        // Keep dashboard progress (UserLectureProgress) in sync with profiling completions.
        syncDashboardProgress(userId, courseId, lectureLabel, request.getTimeSpent());
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Lecture marked as completed");
        response.put("unlockedLectures", profile.getUnlockedLectures().stream()
                .filter(l -> !profile.getUnlockedLectures().contains(l))
                .collect(Collectors.toList()));
        
        return response;
    }
    
    /**
     * Mirror lecture completion into the dashboard progress store for logged-in users.
     */
    private void syncDashboardProgress(String userId, String courseId, String lectureLabel, Integer timeSpent) {
        if (userId == null || userId.isBlank() || courseId == null || courseId.isBlank() || lectureLabel == null) {
            return;
        }
        try {
            userCourseService.updateProgress(userId, courseId, lectureLabel, true, timeSpent);
        } catch (Exception e) {
            log.warn(
                    "Dashboard progress sync failed for user={}, course={}, lecture={}: {}",
                    userId,
                    courseId,
                    lectureLabel,
                    e.getMessage());
        }
    }

    /**
     * Update lecture progress (in-progress)
     */
    public Map<String, Object> updateLectureProgress(String sessionId, String userId, UpdateLectureProgressRequestDTO request) {
        UserProfile profile = getOrCreateProfile(sessionId, userId);
        
        String lectureLabel = request.getLectureLabel();
        String courseId = request.getCourseId();
        
        // Find existing in-progress lecture or create new
        Optional<UserProfile.InProgressLecture> existing = profile.getInProgressLectures().stream()
                .filter(il -> il.getLectureLabel().equals(lectureLabel) && il.getCourseId().equals(courseId))
                .findFirst();
        
        if (existing.isPresent()) {
            UserProfile.InProgressLecture inProgress = existing.get();
            inProgress.setProgressPercentage(request.getProgressPercentage());
            inProgress.setTimeSpent(inProgress.getTimeSpent() + request.getTimeSpent());
            inProgress.setLastAccessedAt(request.getLastAccessedAt() != null ? request.getLastAccessedAt() : IndiaTime.now());
        } else {
            UserProfile.InProgressLecture inProgress = UserProfile.InProgressLecture.builder()
                    .lectureLabel(lectureLabel)
                    .courseId(courseId)
                    .startedAt(IndiaTime.now())
                    .lastAccessedAt(request.getLastAccessedAt() != null ? request.getLastAccessedAt() : IndiaTime.now())
                    .progressPercentage(request.getProgressPercentage())
                    .timeSpent(request.getTimeSpent())
                    .build();
            
            profile.getInProgressLectures().add(inProgress);
        }
        
        profile.setLastActivityAt(IndiaTime.now());
        profile.setUpdatedAt(IndiaTime.now());
        userProfileRepository.save(profile);
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Progress updated");
        
        return response;
    }
    
    private List<String> unlockNextLectures(UserProfile profile, String completedLectureLabel, String courseId) {
        List<CourseCurriculum> curriculum =
                courseService.getCurriculumByCourseIdOrdered(courseId, false, false);

        List<String> unlocked = new ArrayList<>();

        for (CourseCurriculum module : curriculum) {
            if (module.getSubmodules() == null) {
                continue;
            }
            List<CourseCurriculum.Submodule> submodules = module.getSubmodules();
            for (int i = 0; i < submodules.size(); i++) {
                CourseCurriculum.Submodule submodule = submodules.get(i);
                if (!submodule.getLabel().equals(completedLectureLabel)) {
                    continue;
                }
                if (i + 1 < submodules.size()) {
                    String nextLabel = submodules.get(i + 1).getLabel();
                    if (!profile.getUnlockedLectures().contains(nextLabel)) {
                        unlocked.add(nextLabel);
                    }
                }
                break;
            }
        }
        
        return unlocked;
    }
    
    // ========== Chat Tracking ==========

    /**
     * Paginated chat history for the current session/user (lightweight — no audio URLs).
     */
    public List<ChatHistoryItemDTO> getChatHistory(
            String sessionId, String userId, String courseId, int page, int size) {
        Optional<UserProfile> profileOpt = resolveProfileForRead(sessionId, userId);
        if (profileOpt.isEmpty()) {
            return List.of();
        }
        int limit = Math.min(Math.max(size, 1), 50);
        int pageNum = Math.max(page, 0);
        List<UserProfile.ChatInteraction> interactions =
                profileOpt.get().getChatInteractions() != null
                        ? profileOpt.get().getChatInteractions()
                        : List.of();

        return interactions.stream()
                .filter(c -> courseId == null || courseId.isBlank() || courseId.equals(c.getCourseId()))
                .sorted(Comparator.comparing(
                        UserProfile.ChatInteraction::getTimestamp,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .skip((long) pageNum * limit)
                .limit(limit)
                .map(c -> ChatHistoryItemDTO.builder()
                        .id(c.getId())
                        .question(c.getQuestion())
                        .answer(c.getAnswer())
                        .answerAudioUrl(c.getAudioUrl())
                        .timestamp(c.getTimestamp())
                        .lectureContext(c.getLectureContext())
                        .courseId(c.getCourseId())
                        .questionType(c.getQuestionType())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Admin monitor: paginated AI chat exchanges across all learners/guests.
     */
    public Page<AdminChatInteractionDTO> listAdminChatInteractions(
            int page, int size, String userId, String courseId, String email) {
        int limit = Math.min(Math.max(size, 1), 100);
        int pageNum = Math.max(page, 0);

        String emailFilter = email != null ? email.trim().toLowerCase() : null;
        Set<String> allowedUserIds = null;
        if (emailFilter != null && !emailFilter.isBlank()) {
            allowedUserIds = userRepository.findAll().stream()
                    .filter(u -> u.getEmail() != null
                            && u.getEmail().toLowerCase().contains(emailFilter))
                    .map(User::getId)
                    .collect(Collectors.toSet());
            if (allowedUserIds.isEmpty()) {
                return new PageImpl<>(List.of(), PageRequest.of(pageNum, limit), 0);
            }
        }

        Map<String, User> userCache = new HashMap<>();
        Map<String, String> courseNameCache = new HashMap<>();
        List<AdminChatInteractionDTO> rows = new ArrayList<>();

        for (UserProfile profile : userProfileRepository.findAll()) {
            if (profile.getChatInteractions() == null || profile.getChatInteractions().isEmpty()) {
                continue;
            }
            if (userId != null && !userId.isBlank()
                    && (profile.getUserId() == null || !userId.equals(profile.getUserId()))) {
                continue;
            }
            if (allowedUserIds != null
                    && (profile.getUserId() == null || !allowedUserIds.contains(profile.getUserId()))) {
                continue;
            }

            User user = null;
            if (profile.getUserId() != null) {
                user = userCache.computeIfAbsent(
                        profile.getUserId(),
                        id -> userRepository.findById(id).orElse(null));
            }

            for (UserProfile.ChatInteraction chat : profile.getChatInteractions()) {
                if (courseId != null && !courseId.isBlank()
                        && (chat.getCourseId() == null || !courseId.equals(chat.getCourseId()))) {
                    continue;
                }

                String courseName = null;
                if (chat.getCourseId() != null) {
                    courseName = courseNameCache.computeIfAbsent(chat.getCourseId(), cid ->
                            courseRepository.findById(cid).map(Course::getName).orElse(null));
                }

                rows.add(AdminChatInteractionDTO.builder()
                        .interactionId(chat.getId())
                        .userId(profile.getUserId())
                        .userName(user != null ? user.getName() : null)
                        .userEmail(user != null ? user.getEmail() : null)
                        .isGuest(profile.getUserId() == null || Boolean.TRUE.equals(profile.getIsGuest()))
                        .sessionId(profile.getSessionId())
                        .courseId(chat.getCourseId())
                        .courseName(courseName)
                        .question(chat.getQuestion())
                        .answer(chat.getAnswer())
                        .answerAudioUrl(chat.getAudioUrl())
                        .lectureContext(chat.getLectureContext())
                        .questionType(chat.getQuestionType())
                        .timestamp(chat.getTimestamp())
                        .build());
            }
        }

        rows.sort(Comparator.comparing(
                AdminChatInteractionDTO::getTimestamp,
                Comparator.nullsLast(Comparator.reverseOrder())));

        int total = rows.size();
        int from = pageNum * limit;
        if (from >= total) {
            return new PageImpl<>(List.of(), PageRequest.of(pageNum, limit), total);
        }
        int to = Math.min(from + limit, total);
        return new PageImpl<>(rows.subList(from, to), PageRequest.of(pageNum, limit), total);
    }

    private Optional<UserProfile> resolveProfileForRead(String sessionId, String userId) {
        if (userId != null) {
            return userProfileRepository.findByUserId(userId);
        }
        if (sessionId != null) {
            return userProfileRepository.findBySessionId(sessionId);
        }
        return Optional.empty();
    }
    
    /**
     * Track chat question/answer
     */
    public Map<String, Object> trackChat(String sessionId, String userId, TrackChatRequestDTO request) {
        UserProfile profile = getOrCreateProfile(sessionId, userId);
        
        // Check if chat limit reached for guests
        if (profile.getIsGuest() && profile.getTotalQuestionsAsked() >= MAX_GUEST_QUESTIONS) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Chat limit reached. Please login to continue.");
            return response;
        }
        
        // Create chat interaction
        UserProfile.ChatInteraction interaction = UserProfile.ChatInteraction.builder()
                .id(UUID.randomUUID().toString())
                .question(request.getQuestion())
                .answer(request.getAnswer())
                .audioUrl(request.getAnswerAudioUrl())
                .timestamp(request.getTimestamp() != null ? request.getTimestamp() : IndiaTime.now())
                .lectureContext(request.getLectureContext())
                .courseId(request.getCourseId())
                .questionType(request.getQuestionType() != null ? request.getQuestionType() : "text")
                .responseTimeMs(request.getResponseTimeMs())
                .userSpeakDurationSeconds(request.getUserSpeakDurationSeconds())
                .answerAudioDurationSeconds(request.getAnswerAudioDurationSeconds())
                .build();
        
        profile.getChatInteractions().add(interaction);
        profile.setTotalQuestionsAsked(profile.getTotalQuestionsAsked() + 1);
        profile.setLastActivityAt(IndiaTime.now());
        profile.setUpdatedAt(IndiaTime.now());
        userProfileRepository.save(profile);
        
        // Build chat status
        User chatUser = profile.getUserId() != null
                ? userRepository.findById(profile.getUserId()).orElse(null)
                : null;
        FeatureAccessDTO.ChatFeatureDTO chatStatus = buildChatFeatureAccess(profile, chatUser);
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Question tracked");
        
        Map<String, Object> chatStatusMap = new HashMap<>();
        chatStatusMap.put("totalQuestions", profile.getTotalQuestionsAsked());
        chatStatusMap.put("questionsRemaining", chatStatus.getQuestionsRemaining());
        chatStatusMap.put("limitReached", chatStatus.getLimitReached());
        chatStatusMap.put("canContinueChatting", !chatStatus.getLimitReached());
        if (chatStatus.getLimitReached()) {
            chatStatusMap.put("message", "You've reached the limit of " + MAX_GUEST_QUESTIONS + " questions. Please login to continue.");
        }
        response.put("chatStatus", chatStatusMap);
        
        return response;
    }
    
    // ========== Session Migration ==========
    
    /**
     * Migrate guest session to user account
     */
    public Map<String, Object> migrateGuestSession(String userId, String guestSessionId) {
        UserProfile guestProfile = userProfileRepository.findBySessionId(guestSessionId)
                .orElseThrow(() -> new NotFoundException("Guest session not found"));
        
        if (!guestProfile.getIsGuest()) {
            throw new RuntimeException("Session is not a guest session");
        }
        
        // Get or create user profile
        UserProfile userProfile = userProfileRepository.findByUserId(userId)
                .orElseGet(() -> createUserProfile(userId));
        
        // Merge guest data into user profile
        userProfile.getCompletedLectures().addAll(guestProfile.getCompletedLectures());
        userProfile.getInProgressLectures().addAll(guestProfile.getInProgressLectures());
        userProfile.getChatInteractions().addAll(guestProfile.getChatInteractions());
        userProfile.setTotalQuestionsAsked(userProfile.getTotalQuestionsAsked() + guestProfile.getTotalQuestionsAsked());
        
        // Update accessible courses
        if (guestProfile.getCurrentCourseId() != null) {
            if (!userProfile.getAccessibleCourses().contains(guestProfile.getCurrentCourseId())) {
                userProfile.getAccessibleCourses().add(guestProfile.getCurrentCourseId());
            }
            userProfile.setCurrentCourseId(guestProfile.getCurrentCourseId());
        }
        
        userProfile.setLastActivityAt(IndiaTime.now());
        userProfile.setUpdatedAt(IndiaTime.now());
        userProfileRepository.save(userProfile);
        
        // Delete guest session
        userProfileRepository.delete(guestProfile);
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Guest session migrated to user account");
        
        Map<String, Object> migratedData = new HashMap<>();
        migratedData.put("completedLectures", guestProfile.getCompletedLectures().size());
        migratedData.put("chatInteractions", guestProfile.getChatInteractions().size());
        migratedData.put("timeSpent", guestProfile.getCompletedLectures().stream()
                .mapToInt(cl -> cl.getTimeSpent() != null ? cl.getTimeSpent() : 0)
                .sum());
        response.put("migratedData", migratedData);
        
        return response;
    }
    
    // ========== Check Lecture Access ==========
    
    /**
     * Check if specific lecture is accessible
     */
    public LectureAccessDTO checkLectureAccess(String sessionId, String userId, String lectureLabel, String courseId) {
        AccessControlResponseDTO accessControl = getAccessControl(sessionId, userId, courseId);
        
        return accessControl.getModules().stream()
                .flatMap(m -> m.getLectures().stream())
                .filter(l -> l.getLectureLabel().equals(lectureLabel))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Lecture not found: " + lectureLabel));
    }
}

