package com.prwatech.skillama.controller;

import com.prwatech.common.Constants;
import com.prwatech.skillama.dto.StartExamResponseDTO;
import com.prwatech.skillama.model.ExamDifficulty;
import com.prwatech.skillama.model.ExamType;
import com.prwatech.skillama.service.ExamService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ExamControllerTest {

    private MockMvc mockMvc;

    @Mock private ExamService examService;
    @Mock private SkillamaAuthSupport skillamaAuthSupport;

    private static final String TOKEN = "Bearer valid.jwt.token";

    @BeforeEach
    void setUp() {
        ExamController controller = new ExamController(examService, skillamaAuthSupport);
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
}
