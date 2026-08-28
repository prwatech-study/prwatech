package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.ProgressSummaryDTO;
import com.prwatech.skillama.model.CourseCurriculum;
import com.prwatech.skillama.model.UserProfile;
import com.prwatech.skillama.util.IndiaTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;

/**
 * buildProgressSummary must count only labels/modules that still exist in the CURRENT
 * curriculum. Profile completion rows are append-only, so an actively edited course leaves
 * stale rows behind — previously inflating completionPercentage to 100% while the lecture
 * count itself read 220/223 with quizzes still pending (LMS header vs dashboard mismatch).
 */
@ExtendWith(MockitoExtension.class)
class UserProfileServiceProgressSummaryTest {

    @Mock private ModuleQuizService moduleQuizService;

    @InjectMocks
    private UserProfileService userProfileService;

    private static CourseCurriculum module(String name, CourseCurriculum.Submodule... subs) {
        return CourseCurriculum.builder()
                .moduleName(name)
                .submodules(List.of(subs))
                .build();
    }

    private static CourseCurriculum.Submodule lecture(String label) {
        CourseCurriculum.Submodule sub = new CourseCurriculum.Submodule();
        sub.setLabel(label);
        sub.setEnabled(true);
        return sub;
    }

    private static CourseCurriculum.Submodule disabledLecture(String label) {
        CourseCurriculum.Submodule sub = new CourseCurriculum.Submodule();
        sub.setLabel(label);
        sub.setEnabled(false);
        return sub;
    }

    private static UserProfile.CompletedLecture completed(String label, String courseId) {
        return UserProfile.CompletedLecture.builder()
                .lectureLabel(label)
                .courseId(courseId)
                .completedAt(IndiaTime.now())
                .build();
    }

    private static UserProfile.PassedModuleQuiz passedQuiz(String moduleName, String courseId) {
        return UserProfile.PassedModuleQuiz.builder()
                .moduleName(moduleName)
                .courseId(courseId)
                .passedAt(IndiaTime.now())
                .build();
    }

    @Test
    void staleAndForeignRowsDoNotInflateCompletionPercentage() {
        // Current curriculum: 3 enabled lectures across 2 modules (+1 disabled, not counted).
        List<CourseCurriculum> curriculum = List.of(
                module("Module A", lecture("L1"), lecture("L2")),
                module("Module B", lecture("L3"), disabledLecture("LX"))
        );

        UserProfile profile = UserProfile.builder()
                .userId("u1")
                .isGuest(false)
                .completedLectures(new ArrayList<>(List.of(
                        completed("L1", "c1"),
                        completed("L2", "c1"),
                        completed("Old Renamed Lecture", "c1"), // stale — no longer in curriculum
                        completed("Removed Topic", "c1"),       // stale
                        completed("L1", "c1"),                  // duplicate row
                        completed("L3", "other-course")         // different course
                )))
                .inProgressLectures(new ArrayList<>(List.of(
                        UserProfile.InProgressLecture.builder()
                                .lectureLabel("Ghost Lecture") // stale — must not suppress lockedLectures
                                .courseId("c1")
                                .build()
                )))
                .passedModuleQuizzes(new ArrayList<>(List.of(
                        passedQuiz("Module A", "c1"),
                        passedQuiz("Deleted Module", "c1") // stale module
                )))
                .build();

        // Module A's lectures are all complete and its quiz passed; Module B incomplete.
        lenient().when(moduleQuizService.hasPassedModuleQuiz(any(), eq("c1"), eq("Module A")))
                .thenReturn(true);

        ProgressSummaryDTO summary = userProfileService.buildProgressSummary(profile, curriculum, "c1");

        assertEquals(3, summary.getTotalLectures());
        assertEquals(2, summary.getCompletedLectures()); // L1 + L2 only — stale/dup/foreign dropped
        assertEquals(2, summary.getTotalModuleQuizzes());
        assertEquals(1, summary.getPassedModuleQuizzes()); // "Deleted Module" pass dropped
        // Old behavior: numerator 5+2=7 over 5 -> capped 100%. Correct: (2+1)/5 = 60%.
        assertEquals(60, summary.getCompletionPercentage());
        assertEquals(0, summary.getInProgressLectures()); // ghost row dropped
        assertEquals(1, summary.getLockedLectures()); // L3
    }

    @Test
    void fullyCompleteCourseStillReads100() {
        List<CourseCurriculum> curriculum = List.of(module("Module A", lecture("L1"), lecture("L2")));

        UserProfile profile = UserProfile.builder()
                .userId("u1")
                .isGuest(false)
                .completedLectures(new ArrayList<>(List.of(
                        completed("L1", "c1"),
                        completed("L2", "c1")
                )))
                .inProgressLectures(new ArrayList<>())
                .passedModuleQuizzes(new ArrayList<>(List.of(passedQuiz("Module A", "c1"))))
                .build();

        lenient().when(moduleQuizService.hasPassedModuleQuiz(any(), eq("c1"), eq("Module A")))
                .thenReturn(true);

        ProgressSummaryDTO summary = userProfileService.buildProgressSummary(profile, curriculum, "c1");

        assertEquals(100, summary.getCompletionPercentage());
        assertEquals(0, summary.getPendingModuleQuizzes());
    }

    @Test
    void skippedQuizCountsTowardCompletionButNotAsPassed() {
        List<CourseCurriculum> curriculum = List.of(module("Module A", lecture("L1"), lecture("L2")));

        UserProfile profile = UserProfile.builder()
                .userId("u1")
                .isGuest(false)
                .completedLectures(new ArrayList<>(List.of(
                        completed("L1", "c1"),
                        completed("L2", "c1")
                )))
                .inProgressLectures(new ArrayList<>())
                .passedModuleQuizzes(new ArrayList<>())
                .skippedModuleQuizzes(new ArrayList<>(List.of(
                        UserProfile.SkippedModuleQuiz.builder()
                                .moduleName("Module A")
                                .courseId("c1")
                                .build()
                )))
                .build();

        ProgressSummaryDTO summary = userProfileService.buildProgressSummary(profile, curriculum, "c1");

        // Skip unlocks the next module, so it satisfies the module for the % too —
        // otherwise a learner who ever skipped could never reach 100%.
        assertEquals(100, summary.getCompletionPercentage());
        assertEquals(0, summary.getPassedModuleQuizzes());
    }
}
