package com.prwatech.skillama.controller;

import com.prwatech.common.Constants;
import com.prwatech.skillama.dto.GlobalAiExamCourseDTO;
import com.prwatech.skillama.dto.StartExamResponseDTO;
import com.prwatech.skillama.model.ExamDifficulty;
import com.prwatech.skillama.model.ExamType;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.service.AdminPermissionService;
import com.prwatech.skillama.service.ExamService;
import com.prwatech.skillama.service.GlobalAiExamCourseService;
import com.prwatech.skillama.service.SkillamaAuthSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ExamControllerTest {

    private MockMvc mockMvc;

    @Mock private ExamService examService;
    @Mock private SkillamaAuthSupport skillamaAuthSupport;
    @Mock private GlobalAiExamCourseService globalAiExamCourseService;
    @Mock private AdminPermissionService adminPermissionService;

    private static final String TOKEN = "Bearer valid.jwt.token";

    @BeforeEach
    void setUp() {
        ExamController controller = new ExamController(
                examService, skillamaAuthSupport, globalAiExamCourseService, adminPermissionService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    @Test
    void startExam_withoutAuth_returns401() throws Exception {
        mockMvc.perform(post("/skillama/ai-exam/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":\"c1\",\"difficulty\":\"BEGINNER\",\"examType\":\"PRACTICE\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void startExam_withBearerToken_startsExam() throws Exception {
        when(skillamaAuthSupport.resolveUserIdFromRequest(any())).thenReturn("u1");
        when(examService.startExam(org.mockito.ArgumentMatchers.eq("u1"), any())).thenReturn(
                StartExamResponseDTO.builder()
                        .examSessionId("exam-1")
                        .examTitle("AI Exam: Python")
                        .totalQuestions(5)
                        .timeLimitSeconds(450)
                        .difficulty(ExamDifficulty.BEGINNER)
                        .examType(ExamType.PRACTICE)
                        .build());

        mockMvc.perform(post("/skillama/ai-exam/start")
                        .header(Constants.AUTH, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":\"c1\",\"difficulty\":\"BEGINNER\",\"examType\":\"PRACTICE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.examSessionId").value("exam-1"))
                .andExpect(jsonPath("$.timeLimitSeconds").value(450));
    }

    @Test
    void listMyAttempts_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/skillama/ai-exam/attempts"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listGlobalCourses_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/skillama/ai-exam/global-courses"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listGlobalCourses_asLearner_returnsConfiguredCourses() throws Exception {
        when(skillamaAuthSupport.resolveUserIdFromRequest(any())).thenReturn("u1");
        when(globalAiExamCourseService.listAll()).thenReturn(List.of(
                GlobalAiExamCourseDTO.builder()
                        .id("cfg-1")
                        .courseId("c1")
                        .name("Python")
                        .available(true)
                        .build()));

        mockMvc.perform(get("/skillama/ai-exam/global-courses")
                        .header(Constants.AUTH, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].courseId").value("c1"))
                .andExpect(jsonPath("$[0].name").value("Python"));

        verify(adminPermissionService, never()).requireAdminOrOwner(any());
    }

    @Test
    void addGlobalCourse_asLearner_returns403() throws Exception {
        when(skillamaAuthSupport.resolveUserIdFromRequest(any())).thenReturn("u1");
        doThrow(new RuntimeException("Admin access required"))
                .when(adminPermissionService).requireAdminOrOwner("u1");

        mockMvc.perform(post("/skillama/ai-exam/global-courses")
                        .header(Constants.AUTH, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":\"c1\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Admin or Owner access required"));

        verify(globalAiExamCourseService, never()).create(any(), any());
    }

    @Test
    void addGlobalCourse_duplicate_returns409() throws Exception {
        when(skillamaAuthSupport.resolveUserIdFromRequest(any())).thenReturn("admin-1");
        when(adminPermissionService.requireAdminOrOwner("admin-1"))
                .thenReturn(User.builder().id("admin-1").role(User.UserRole.ADMIN).build());
        when(globalAiExamCourseService.create(any(), eq("admin-1")))
                .thenThrow(new IllegalStateException(GlobalAiExamCourseService.DUPLICATE_MESSAGE));

        mockMvc.perform(post("/skillama/ai-exam/global-courses")
                        .header(Constants.AUTH, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":\"c1\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(GlobalAiExamCourseService.DUPLICATE_MESSAGE));
    }

    @Test
    void deleteGlobalCourse_asAdmin_removesConfig() throws Exception {
        when(skillamaAuthSupport.resolveUserIdFromRequest(any())).thenReturn("admin-1");
        when(adminPermissionService.requireAdminOrOwner("admin-1"))
                .thenReturn(User.builder().id("admin-1").role(User.UserRole.ADMIN).build());

        mockMvc.perform(delete("/skillama/ai-exam/global-courses/cfg-1")
                        .header(Constants.AUTH, TOKEN))
                .andExpect(status().isNoContent());

        verify(globalAiExamCourseService).delete("cfg-1");
    }

    @Test
    void updateGlobalCourse_asLearner_returns403() throws Exception {
        when(skillamaAuthSupport.resolveUserIdFromRequest(any())).thenReturn("u1");
        doThrow(new RuntimeException("Admin access required"))
                .when(adminPermissionService).requireAdminOrOwner("u1");

        mockMvc.perform(put("/skillama/ai-exam/global-courses/cfg-1")
                        .header(Constants.AUTH, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":\"c2\"}"))
                .andExpect(status().isForbidden());
    }
}
