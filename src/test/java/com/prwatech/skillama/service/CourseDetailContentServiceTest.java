package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.CourseOutlineModuleDTO;
import com.prwatech.skillama.dto.CourseShareMetadataDTO;
import com.prwatech.skillama.dto.GeneratedCourseDetailDTO;
import com.prwatech.skillama.model.Course;
import com.prwatech.skillama.model.CourseCurriculum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CourseDetailContentServiceTest {

    @Mock private CourseService courseService;
    @Mock private SkillamaAiClient skillamaAiClient;
    @Mock private UserService userService;

    private CourseDetailContentService service;

    @BeforeEach
    void setUp() {
        service = new CourseDetailContentService(courseService, skillamaAiClient, userService);
    }

    private static CourseCurriculum.Submodule lecture(String label, Boolean enabled) {
        CourseCurriculum.Submodule s = new CourseCurriculum.Submodule();
        s.setLabel(label);
        s.setEnabled(enabled);
        s.setScriptText("SECRET SCRIPT — must never appear on the public outline");
        return s;
    }

    private static CourseCurriculum module(String name, CourseCurriculum.Submodule... lectures) {
        return CourseCurriculum.builder().moduleName(name).submodules(List.of(lectures)).build();
    }

    private static GeneratedCourseDetailDTO draft() {
        return GeneratedCourseDetailDTO.builder()
                .overview("This course covers the listed lectures with an AI tutor that explains each topic.")
                .highlights(List.of("Functions", "Loops", "Returns"))
                .objectives(List.of("Write functions", "Use loops"))
                .outcomes(List.of("Build small programs", "Read Python"))
                .aiTutorHelp("The AI Tutor explains each listed lecture and can run practice code.")
                .build();
    }

    @Test
    void buildOutline_usesLabelsOnlyAndSkipsDisabled() {
        CourseCurriculum module = module("Functions",
                lecture("What is a function", true),
                lecture("Hidden lecture", false),
                lecture("  Return values  ", null));

        List<CourseOutlineModuleDTO> outline = CourseDetailContentService.buildOutline(List.of(module));

        assertEquals(1, outline.size());
        assertEquals("Functions", outline.get(0).getModuleName());
        assertEquals(List.of("What is a function", "Return values"), outline.get(0).getLectures());
        assertTrue(outline.get(0).getLectures().stream().noneMatch(l -> l.contains("SECRET")));
    }

    @Test
    void curriculumHash_changesWhenLectureAdded() {
        List<CourseOutlineModuleDTO> a = CourseDetailContentService.buildOutline(List.of(
                module("Functions", lecture("What is a function", true))));
        List<CourseOutlineModuleDTO> b = CourseDetailContentService.buildOutline(List.of(
                module("Functions", lecture("What is a function", true), lecture("Return values", true))));

        assertEquals(CourseDetailContentService.curriculumHash(a), CourseDetailContentService.curriculumHash(a));
        assertNotEquals(CourseDetailContentService.curriculumHash(a), CourseDetailContentService.curriculumHash(b));
    }

    @Test
    void getShareMetadata_generatesAndSavesOnFirstRequest() {
        Course empty = Course.builder().id("c1").name("Python").description("Catalog").build();
        List<CourseCurriculum> curriculum = List.of(module("Functions", lecture("What is a function", true)));
        String hash = CourseDetailContentService.curriculumHash(CourseDetailContentService.buildOutline(curriculum));
        Course saved = storedCourse(hash);
        saved.setDetailOverview("Generated overview for this curriculum.");

        when(courseService.findActiveById("c1")).thenReturn(Optional.of(empty));
        when(courseService.getCurriculumByCourseIdOrdered("c1", false, false)).thenReturn(curriculum);
        when(skillamaAiClient.generateCourseDetail(any(), eq("c1"), any(), any(), any())).thenReturn(draft());
        when(courseService.saveAiGeneratedDetail(eq("c1"), any(), eq(hash))).thenReturn(saved);

        Optional<CourseShareMetadataDTO> dto = service.getShareMetadata("c1", "https://skillama.co.in");

        assertTrue(dto.isPresent());
        assertEquals("Generated overview for this curriculum.", dto.get().getOverview());
        verify(skillamaAiClient, times(1)).generateCourseDetail(any(), eq("c1"), any(), any(), any());
        verify(courseService, times(1)).saveAiGeneratedDetail(eq("c1"), any(), eq(hash));
    }

    @Test
    void getShareMetadata_regeneratesWhenCurriculumHashDiffers() {
        Course stale = storedCourse("old-hash");
        List<CourseCurriculum> curriculum = List.of(module("Functions", lecture("What is a function", true)));
        String hash = CourseDetailContentService.curriculumHash(CourseDetailContentService.buildOutline(curriculum));
        Course saved = storedCourse(hash);
        saved.setDetailOverview("Regenerated overview for the new outline.");

        when(courseService.findActiveById("c1")).thenReturn(Optional.of(stale));
        when(courseService.getCurriculumByCourseIdOrdered("c1", false, false)).thenReturn(curriculum);
        when(skillamaAiClient.generateCourseDetail(any(), eq("c1"), any(), any(), any())).thenReturn(draft());
        when(courseService.saveAiGeneratedDetail(eq("c1"), any(), eq(hash))).thenReturn(saved);

        Optional<CourseShareMetadataDTO> dto = service.getShareMetadata("c1", "https://skillama.co.in");

        assertTrue(dto.isPresent());
        assertEquals("Regenerated overview for the new outline.", dto.get().getOverview());
        verify(skillamaAiClient, times(1)).generateCourseDetail(any(), eq("c1"), any(), any(), any());
        verify(courseService, times(1)).saveAiGeneratedDetail(eq("c1"), any(), eq(hash));
    }

    @Test
    void getShareMetadata_skipsAiWhenHashMatches() {
        Course course = storedCourse("abc");
        List<CourseCurriculum> curriculum = List.of(module("Functions", lecture("What is a function", true)));
        String hash = CourseDetailContentService.curriculumHash(CourseDetailContentService.buildOutline(curriculum));
        course.setDetailCurriculumHash(hash);

        when(courseService.findActiveById("c1")).thenReturn(Optional.of(course));
        when(courseService.getCurriculumByCourseIdOrdered("c1", false, false)).thenReturn(curriculum);

        Optional<CourseShareMetadataDTO> dto = service.getShareMetadata("c1", "https://skillama.co.in");

        assertTrue(dto.isPresent());
        assertEquals("Stored overview for this curriculum.", dto.get().getOverview());
        verify(skillamaAiClient, times(0)).generateCourseDetail(any(), anyString(), any(), any(), any());
    }

    @Test
    void getShareMetadata_retainsPreviousCopyWhenAiFails() {
        Course course = storedCourse("stale-hash");
        List<CourseCurriculum> curriculum = List.of(module("Functions", lecture("What is a function", true)));
        when(courseService.findActiveById("c1")).thenReturn(Optional.of(course));
        when(courseService.getCurriculumByCourseIdOrdered("c1", false, false)).thenReturn(curriculum);
        when(skillamaAiClient.generateCourseDetail(any(), eq("c1"), any(), any(), any()))
                .thenThrow(new IllegalStateException("AI down"));

        Optional<CourseShareMetadataDTO> dto = service.getShareMetadata("c1", "https://skillama.co.in");

        assertTrue(dto.isPresent());
        assertEquals("Stored overview for this curriculum.", dto.get().getOverview());
        verify(courseService, times(0)).saveAiGeneratedDetail(anyString(), any(), anyString());
    }

    @Test
    void getShareMetadata_generatesOnceUnderConcurrentLoad() throws Exception {
        Course empty = Course.builder().id("c1").name("Python").description("Catalog").build();
        List<CourseCurriculum> curriculum = List.of(module("Functions", lecture("What is a function", true)));
        String hash = CourseDetailContentService.curriculumHash(CourseDetailContentService.buildOutline(curriculum));
        java.util.concurrent.atomic.AtomicReference<Course> store =
                new java.util.concurrent.atomic.AtomicReference<>(empty);

        when(courseService.findActiveById("c1")).thenAnswer(inv -> Optional.of(store.get()));
        when(courseService.getCurriculumByCourseIdOrdered("c1", false, false)).thenReturn(curriculum);
        when(courseService.saveAiGeneratedDetail(eq("c1"), any(), eq(hash))).thenAnswer(inv -> {
            Course next = storedCourse(hash);
            next.setDetailOverview("Generated overview for this curriculum.");
            store.set(next);
            return next;
        });

        CountDownLatch started = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        when(skillamaAiClient.generateCourseDetail(any(), eq("c1"), any(), any(), any())).thenAnswer(inv -> {
            calls.incrementAndGet();
            started.countDown();
            TimeUnit.MILLISECONDS.sleep(80);
            return draft();
        });

        Thread t1 = new Thread(() -> service.getShareMetadata("c1", "https://skillama.co.in"));
        Thread t2 = new Thread(() -> {
            try {
                assertTrue(started.await(2, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            service.getShareMetadata("c1", "https://skillama.co.in");
        });
        t1.start();
        t2.start();
        t1.join(3000);
        t2.join(3000);

        assertEquals(1, calls.get());
    }

    private static Course storedCourse(String hash) {
        return Course.builder()
                .id("c1")
                .name("Python")
                .description("Catalog blurb")
                .detailOverview("Stored overview for this curriculum.")
                .detailOutcomes(List.of("Build small programs"))
                .detailCurriculumHash(hash)
                .build();
    }
}
