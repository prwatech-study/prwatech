package com.prwatech.skillama.controller;

import com.prwatech.common.Constants;
import com.prwatech.skillama.dto.AskDoubtRequestDTO;
import com.prwatech.skillama.dto.DoubtMessageDTO;
import com.prwatech.skillama.dto.DoubtResponseDTO;
import com.prwatech.skillama.model.DoubtStatus;
import com.prwatech.skillama.service.DoubtService;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DoubtControllerTest {

    private MockMvc mockMvc;

    @Mock private DoubtService doubtService;
    @Mock private SkillamaAuthSupport skillamaAuthSupport;

    private static final String TOKEN = "Bearer valid.jwt.token";

    @BeforeEach
    void setUp() {
        DoubtController controller = new DoubtController(doubtService, skillamaAuthSupport);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    @Test
    void askDoubt_withoutAuth_returns401() throws Exception {
        mockMvc.perform(post("/skillama/ai-mentor/doubts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":\"course-1\",\"question\":\"What is a DataFrame?\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void askDoubt_withBearerToken_createsDoubt() throws Exception {
        when(skillamaAuthSupport.resolveUserIdFromRequest(any())).thenReturn("u1");
        when(doubtService.askDoubt(eq("u1"), any(AskDoubtRequestDTO.class))).thenReturn(
                DoubtResponseDTO.builder()
                        .id("doubt-1")
                        .courseId("course-1")
                        .status(DoubtStatus.PENDING)
                        .messages(List.of(
                                DoubtMessageDTO.builder().sender("USER").content("What is a DataFrame?").build(),
                                DoubtMessageDTO.builder().sender("AI").content("A 2D labeled structure.").build()))
                        .build());

        mockMvc.perform(post("/skillama/ai-mentor/doubts")
                        .header(Constants.AUTH, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":\"course-1\",\"question\":\"What is a DataFrame?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("doubt-1"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.messages[0].content").value("What is a DataFrame?"));
    }

    @Test
    void listMyDoubts_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/skillama/ai-mentor/doubts"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listMyDoubts_withBearerToken_returnsCourseScopedDoubts() throws Exception {
        when(skillamaAuthSupport.resolveUserIdFromRequest(any())).thenReturn("u1");
        when(doubtService.listMyDoubts(eq("u1"), eq("course-1"))).thenReturn(List.of(
                DoubtResponseDTO.builder().id("doubt-1").courseId("course-1").status(DoubtStatus.PENDING).build()));

        mockMvc.perform(get("/skillama/ai-mentor/doubts")
                        .param("courseId", "course-1")
                        .header(Constants.AUTH, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("doubt-1"));
    }
}
