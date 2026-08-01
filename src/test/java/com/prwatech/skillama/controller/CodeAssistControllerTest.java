package com.prwatech.skillama.controller;

import com.prwatech.common.Constants;
import com.prwatech.common.exception.NotFoundException;
import com.prwatech.skillama.dto.CodeAssistRequestDTO;
import com.prwatech.skillama.dto.CodeAssistResponseDTO;
import com.prwatech.skillama.service.CodeAssistService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CodeAssistControllerTest {

    private MockMvc mockMvc;

    @Mock private CodeAssistService codeAssistService;
    @Mock private SkillamaAuthSupport skillamaAuthSupport;

    private static final String TOKEN = "Bearer valid.jwt.token";

    @BeforeEach
    void setUp() {
        CodeAssistController controller = new CodeAssistController(codeAssistService, skillamaAuthSupport);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    @Test
    void debug_withoutAuth_returns401() throws Exception {
        mockMvc.perform(post("/skillama/code-assist/debug")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"print(1)\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void debug_withBearerToken_returnsResponse() throws Exception {
        when(skillamaAuthSupport.resolveUserIdFromRequest(any())).thenReturn("u1");
        when(codeAssistService.runDebug(eq("u1"), any(CodeAssistRequestDTO.class))).thenReturn(
                CodeAssistResponseDTO.builder()
                        .interactionId("interaction-1")
                        .codeOutput("Hello")
                        .responseText("Looks good.")
                        .hasAudio(true)
                        .build());

        mockMvc.perform(post("/skillama/code-assist/debug")
                        .header(Constants.AUTH, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"print(1)\",\"courseId\":\"course-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interactionId").value("interaction-1"))
                .andExpect(jsonPath("$.codeOutput").value("Hello"))
                .andExpect(jsonPath("$.hasAudio").value(true));
    }

    @Test
    void execute_withoutAuth_returns401() throws Exception {
        mockMvc.perform(post("/skillama/code-assist/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"print(1)\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void execute_withBearerToken_returnsResponse() throws Exception {
        when(skillamaAuthSupport.resolveUserIdFromRequest(any())).thenReturn("u1");
        when(codeAssistService.runCodeExecution(eq("u1"), any(CodeAssistRequestDTO.class))).thenReturn(
                CodeAssistResponseDTO.builder()
                        .interactionId("interaction-2")
                        .codeOutput("Hello")
                        .hasAudio(false)
                        .build());

        mockMvc.perform(post("/skillama/code-assist/execute")
                        .header(Constants.AUTH, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"print(1)\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interactionId").value("interaction-2"));
    }

    @Test
    void getInteractionAudio_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/skillama/code-assist/interactions/interaction-1/audio"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getInteractionAudio_notFound_returns404() throws Exception {
        when(skillamaAuthSupport.resolveUserIdFromRequest(any())).thenReturn("u1");
        when(codeAssistService.getInteractionAudio("u1", "interaction-1"))
                .thenThrow(new NotFoundException("Interaction not found"));

        mockMvc.perform(get("/skillama/code-assist/interactions/interaction-1/audio")
                        .header(Constants.AUTH, TOKEN))
                .andExpect(status().isNotFound());
    }
}
