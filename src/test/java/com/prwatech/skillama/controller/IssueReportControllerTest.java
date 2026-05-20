package com.prwatech.skillama.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prwatech.skillama.dto.IssueReportResponseDTO;
import com.prwatech.skillama.service.IssueReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class IssueReportControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private IssueReportService issueReportService;

    @BeforeEach
    void setUp() {
        IssueReportController controller = new IssueReportController(issueReportService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void postReportReturns201() throws Exception {
        when(issueReportService.submit(any(), any()))
                .thenReturn(IssueReportResponseDTO.builder().id("rid-1").message("ok").build());

        String body = objectMapper.writeValueAsString(
                java.util.Map.of(
                        "userDescription", "Audio failed during practical",
                        "issueCategory", "TEXT_TO_AUDIO",
                        "courseId", "c1"));

        String content = mockMvc.perform(
                        post("/skillama/issues/report")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertTrue(content.contains("rid-1"));
        assertTrue(content.contains("ok"));
    }
}
