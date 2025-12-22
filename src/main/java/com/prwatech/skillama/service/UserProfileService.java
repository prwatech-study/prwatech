package com.prwatech.skillama.service;

import com.prwatech.common.exception.NotFoundException;
import com.prwatech.skillama.dto.*;
import com.prwatech.skillama.model.Course;
import com.prwatech.skillama.model.CourseCurriculum;
import com.prwatech.skillama.model.UserProfile;
import com.prwatech.skillama.repository.CourseRepository;
import com.prwatech.skillama.repository.CourseCurriculumRepository;
import com.prwatech.skillama.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserProfileService {
    
    private static final int MAX_GUEST_QUESTIONS = 5;
    private static final int GUEST_SESSION_EXPIRY_DAYS = 7;
    
    private final UserProfileRepository userProfileRepository;
    private final CourseRepository courseRepository;
    private final CourseCurriculumRepository curriculumRepository;
    private final CourseService courseService;
    private final MongoTemplate skillamaMongoTemplate;
    
    // ========== Session Management ==========
    
    /**
     * Initialize guest session for non-logged-in users
     */
    public Map<String, Object> initializeGuestSession(InitGuestSessionRequestDTO request) {
        String sessionId = "guest-session-" + UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(GUEST_SESSION_EXPIRY_DAYS);
        
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
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .lastActivityAt(LocalDateTime.now())
                .sessionExpiresAt(expiresAt)
                .build();
        
        profile = userProfileRepository.save(profile);
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("sessionId", sessionId);
        response.put("sessionExpiresAt", expiresAt);
        
        // Build initial feature access
        FeatureAccessDTO features = buildFeatureAccess(profile);
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
                    .orElseGet(() -> createUserProfile(userId));
        }
        
        if (sessionId != null) {
            return userProfileRepository.findBySessionId(sessionId)
                    .orElseGet(() -> {
                        // Create new guest session
                        Course guestCourse = courseService.getGuestCourseOrThrow();
                        LocalDateTime expiresAt = LocalDateTime.now().plusDays(GUEST_SESSION_EXPIRY_DAYS);
                        
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
                                .createdAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .lastActivityAt(LocalDateTime.now())
                                .sessionExpiresAt(expiresAt)
                                .build();
                    });
        }
        
        throw new NotFoundException("Session ID or User ID required");
    }
    
    private UserProfile createUserProfile(String userId) {
        Course guestCourse = courseService.getGuestCourseOrThrow();
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
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .lastActivityAt(LocalDateTime.now())
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
            Course guestCourse = courseService.getGuestCourseOrThrow();
            targetCourseId = guestCourse.getId();
            profile.setCurrentCourseId(targetCourseId);
            userProfileRepository.save(profile);
        }
        
        // Validate course exists
        Course course = courseRepository.findById(targetCourseId)
                .orElseThrow(() -> new NotFoundException("Course not found with ID: " + targetCourseId));
        
        List<CourseCurriculum> curriculum = courseService.getCurriculumByCourseIdOrdered(targetCourseId);
        
        // Build module access
        List<ModuleAccessDTO> modules = buildModuleAccess(profile, curriculum, targetCourseId);
        
        // Build feature access
        FeatureAccessDTO features = buildFeatureAccess(profile);
        
        // Build progress summary
        ProgressSummaryDTO progress = buildProgressSummary(profile, curriculum);
        
        return AccessControlResponseDTO.builder()
                .userId(profile.getUserId())
                .sessionId(profile.getSessionId())
                .isGuest(profile.getIsGuest())
                .courseId(targetCourseId)
                .courseName(course.getName())
                .modules(modules)
                .features(features)
                .progress(progress)
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
        // For logged-in users: Progressive unlocking based on completion
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
                // Check if previous module is completed
                CourseCurriculum previousModule = allModules.get(moduleIndex - 1);
                boolean previousModuleCompleted = isModuleCompleted(profile, previousModule, courseId);
                isModuleAccessible = previousModuleCompleted;
                isModuleLocked = !previousModuleCompleted;
                if (isModuleLocked) {
                    lockReason = "Previous module must be completed";
                }
            }
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
        }
        
        boolean isCompleted = isLectureCompleted(profile, lectureLabel, courseId);
        boolean isInProgress = isLectureInProgress(profile, lectureLabel, courseId);
        Integer completionPercentage = getLectureProgress(profile, lectureLabel, courseId);
        LocalDateTime completedAt = getLectureCompletedAt(profile, lectureLabel, courseId);
        LocalDateTime unlockedAt = getLectureUnlockedAt(profile, lectureLabel);
        
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
    
    private boolean isModuleCompleted(UserProfile profile, CourseCurriculum module, String courseId) {
        if (module.getSubmodules() == null || module.getSubmodules().isEmpty()) {
            return false;
        }
        
        for (CourseCurriculum.Submodule submodule : module.getSubmodules()) {
            if (!isLectureCompleted(profile, submodule.getLabel(), courseId)) {
                return false;
            }
        }
        return true;
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
            return LocalDateTime.now(); // Simplified - could track actual unlock time
        }
        return null;
    }
    
    private FeatureAccessDTO buildFeatureAccess(UserProfile profile) {
        // Chat access
        FeatureAccessDTO.ChatFeatureDTO chatFeature = buildChatFeatureAccess(profile);
        
        // Code execution access
        FeatureAccessDTO.CodeExecutionFeatureDTO codeExecutionFeature = FeatureAccessDTO.CodeExecutionFeatureDTO.builder()
                .accessible(!profile.getIsGuest())
                .reason(profile.getIsGuest() ? "Login required to access Code Execution" : null)
                .build();
        
        // Debug access
        FeatureAccessDTO.DebugFeatureDTO debugFeature = FeatureAccessDTO.DebugFeatureDTO.builder()
                .accessible(!profile.getIsGuest())
                .reason(profile.getIsGuest() ? "Login required to access Debug" : null)
                .build();
        
        return FeatureAccessDTO.builder()
                .chat(chatFeature)
                .codeExecution(codeExecutionFeature)
                .debug(debugFeature)
                .build();
    }
    
    private FeatureAccessDTO.ChatFeatureDTO buildChatFeatureAccess(UserProfile profile) {
        if (profile.getIsGuest()) {
            int questionsRemaining = Math.max(0, MAX_GUEST_QUESTIONS - profile.getTotalQuestionsAsked());
            boolean limitReached = questionsRemaining <= 0;
            
            return FeatureAccessDTO.ChatFeatureDTO.builder()
                    .accessible(!limitReached)
                    .questionsRemaining(questionsRemaining)
                    .limitReached(limitReached)
                    .build();
        }
        
        // Logged-in users have unlimited chat
        return FeatureAccessDTO.ChatFeatureDTO.builder()
                .accessible(true)
                .questionsRemaining(null)
                .limitReached(false)
                .build();
    }
    
    private ProgressSummaryDTO buildProgressSummary(UserProfile profile, List<CourseCurriculum> curriculum) {
        int totalLectures = curriculum.stream()
                .mapToInt(m -> m.getSubmodules() != null ? m.getSubmodules().size() : 0)
                .sum();
        
        int completedLectures = profile.getCompletedLectures().size();
        int inProgressLectures = profile.getInProgressLectures().size();
        int lockedLectures = totalLectures - completedLectures - inProgressLectures;
        int completionPercentage = totalLectures > 0 ? (completedLectures * 100 / totalLectures) : 0;
        
        return ProgressSummaryDTO.builder()
                .totalLectures(totalLectures)
                .completedLectures(completedLectures)
                .inProgressLectures(inProgressLectures)
                .lockedLectures(lockedLectures)
                .completionPercentage(completionPercentage)
                .build();
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
                    .completedAt(request.getCompletedAt() != null ? request.getCompletedAt() : LocalDateTime.now())
                    .timeSpent(request.getTimeSpent())
                    .completionPercentage(request.getCompletionPercentage() != null ? request.getCompletionPercentage() : 100)
                    .build();
            
            profile.getCompletedLectures().add(completedLecture);
            
            // Unlock next lecture
            List<String> unlocked = unlockNextLectures(profile, lectureLabel, courseId);
            profile.getUnlockedLectures().addAll(unlocked);
        }
        
        profile.setLastActivityAt(LocalDateTime.now());
        profile.setUpdatedAt(LocalDateTime.now());
        userProfileRepository.save(profile);
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Lecture marked as completed");
        response.put("unlockedLectures", profile.getUnlockedLectures().stream()
                .filter(l -> !profile.getUnlockedLectures().contains(l))
                .collect(Collectors.toList()));
        
        return response;
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
            inProgress.setLastAccessedAt(request.getLastAccessedAt() != null ? request.getLastAccessedAt() : LocalDateTime.now());
        } else {
            UserProfile.InProgressLecture inProgress = UserProfile.InProgressLecture.builder()
                    .lectureLabel(lectureLabel)
                    .courseId(courseId)
                    .startedAt(LocalDateTime.now())
                    .lastAccessedAt(request.getLastAccessedAt() != null ? request.getLastAccessedAt() : LocalDateTime.now())
                    .progressPercentage(request.getProgressPercentage())
                    .timeSpent(request.getTimeSpent())
                    .build();
            
            profile.getInProgressLectures().add(inProgress);
        }
        
        profile.setLastActivityAt(LocalDateTime.now());
        profile.setUpdatedAt(LocalDateTime.now());
        userProfileRepository.save(profile);
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Progress updated");
        
        return response;
    }
    
    private List<String> unlockNextLectures(UserProfile profile, String completedLectureLabel, String courseId) {
        // Get curriculum to find next lecture
        List<CourseCurriculum> curriculum = courseService.getCurriculumByCourseIdOrdered(courseId);
        
        List<String> unlocked = new ArrayList<>();
        
        for (CourseCurriculum module : curriculum) {
            if (module.getSubmodules() != null) {
                for (int i = 0; i < module.getSubmodules().size(); i++) {
                    CourseCurriculum.Submodule submodule = module.getSubmodules().get(i);
                    if (submodule.getLabel().equals(completedLectureLabel) && i + 1 < module.getSubmodules().size()) {
                        // Unlock next lecture in same module
                        String nextLabel = module.getSubmodules().get(i + 1).getLabel();
                        if (!profile.getUnlockedLectures().contains(nextLabel)) {
                            unlocked.add(nextLabel);
                        }
                    }
                }
            }
        }
        
        return unlocked;
    }
    
    // ========== Chat Tracking ==========
    
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
                .timestamp(request.getTimestamp() != null ? request.getTimestamp() : LocalDateTime.now())
                .lectureContext(request.getLectureContext())
                .courseId(request.getCourseId())
                .questionType(request.getQuestionType() != null ? request.getQuestionType() : "text")
                .build();
        
        profile.getChatInteractions().add(interaction);
        profile.setTotalQuestionsAsked(profile.getTotalQuestionsAsked() + 1);
        profile.setLastActivityAt(LocalDateTime.now());
        profile.setUpdatedAt(LocalDateTime.now());
        userProfileRepository.save(profile);
        
        // Build chat status
        FeatureAccessDTO.ChatFeatureDTO chatStatus = buildChatFeatureAccess(profile);
        
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
        
        userProfile.setLastActivityAt(LocalDateTime.now());
        userProfile.setUpdatedAt(LocalDateTime.now());
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

