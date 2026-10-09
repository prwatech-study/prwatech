package com.prwatech.skillama.controller;

import com.prwatech.common.Constants;
import com.prwatech.skillama.exception.FeatureNotLiveException;
import com.prwatech.skillama.exception.SkillamaExceptionHandler;
import com.prwatech.skillama.service.AiInterviewService;
import com.prwatech.skillama.service.PlatformFeatureRolloutService;
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
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AiInterviewFeatureRolloutControllerTest {

    private MockMvc mockMvc;

    @Mock private AiInterviewService aiInterviewService;
    @Mock private SkillamaAuthSupport skillamaAuthSupport;
    @Mock private PlatformFeatureRolloutService platformFeatureRolloutService;

    @BeforeEach
    void setUp() {
        AiInterviewController controller = new AiInterviewController(
                aiInterviewService, skillamaAuthSupport, platformFeatureRolloutService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new SkillamaExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    @Test
    void learnerListReturns403WhenUpcoming() throws Exception {
        when(skillamaAuthSupport.resolveUserIdFromRequest(any())).thenReturn("user-1");
        doThrow(new FeatureNotLiveException(
                        PlatformFeatureRolloutService.AI_INTERVIEW,
                        "This feature is not available yet. Check back soon."))
                .when(platformFeatureRolloutService)
                .assertAccessible(eq(PlatformFeatureRolloutService.AI_INTERVIEW), eq("user-1"));

        mockMvc.perform(get("/skillama/ai-interview/my")
                        .header(Constants.AUTH, "Bearer token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(FeatureNotLiveException.CODE))
                .andExpect(jsonPath("$.featureCode").value("ai_interview"));
    }

    @Test
    void invitePreviewReturns403WhenNotPubliclyLive() throws Exception {
        doThrow(new FeatureNotLiveException(
                        PlatformFeatureRolloutService.AI_INTERVIEW,
                        "This feature is not available yet. Check back soon."))
                .when(platformFeatureRolloutService)
                .assertPubliclyLive(PlatformFeatureRolloutService.AI_INTERVIEW);

        mockMvc.perform(get("/skillama/ai-interview/invite/opaque-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(FeatureNotLiveException.CODE));
    }

    @Test
    void learnerListOkWhenAccessible() throws Exception {
        when(skillamaAuthSupport.resolveUserIdFromRequest(any())).thenReturn("user-1");
        doNothing()
                .when(platformFeatureRolloutService)
                .assertAccessible(eq(PlatformFeatureRolloutService.AI_INTERVIEW), eq("user-1"));
        when(aiInterviewService.listMine("user-1")).thenReturn(List.of(Map.of("id", "s1")));

        mockMvc.perform(get("/skillama/ai-interview/my")
                        .header(Constants.AUTH, "Bearer token")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("s1"));
    }
}
