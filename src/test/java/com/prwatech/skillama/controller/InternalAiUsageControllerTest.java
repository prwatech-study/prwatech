package com.prwatech.skillama.controller;

import com.prwatech.skillama.dto.AiUsageRecordRequestDTO;
import com.prwatech.skillama.model.AiUsageEvent;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import com.prwatech.skillama.service.AiUsageService;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class InternalAiUsageControllerTest {

    private MockMvc mockMvc;

    @Mock private AiUsageService aiUsageService;
    @Mock private SkillamaUserRepository userRepository;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new InternalAiUsageController(aiUsageService, userRepository))
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    @Test
    void record_missingKey_returns401() throws Exception {
        when(aiUsageService.isValidInternalApiKey(null)).thenReturn(false);

        mockMvc.perform(post("/skillama/internal/ai-usage/record")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endpoint\":\"/generate_lecture\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("error"));

        verify(aiUsageService, never()).recordUsage(any());
    }

    @Test
    void record_invalidKey_returns401() throws Exception {
        when(aiUsageService.isValidInternalApiKey("wrong")).thenReturn(false);

        mockMvc.perform(post("/skillama/internal/ai-usage/record")
                        .header("X-AI-Usage-Key", "wrong")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endpoint\":\"/generate_lecture\"}"))
                .andExpect(status().isUnauthorized());

        verify(aiUsageService, never()).recordUsage(any());
    }

    @Test
    void record_validKey_returns200() throws Exception {
        when(aiUsageService.isValidInternalApiKey("secret")).thenReturn(true);
        when(aiUsageService.recordUsage(any(AiUsageRecordRequestDTO.class)))
                .thenReturn(AiUsageEvent.builder().id("evt-1").build());

        mockMvc.perform(post("/skillama/internal/ai-usage/record")
                        .header("X-AI-Usage-Key", "secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endpoint\":\"/generate_lecture\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }
}
