package com.prwatech.skillama.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prwatech.skillama.dto.BillingCheckoutResponseDTO;
import com.prwatech.skillama.dto.SubscriptionPlanDTO;
import com.prwatech.skillama.dto.UserSubscriptionDTO;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.exception.SkillamaAuthException;
import com.prwatech.skillama.service.SkillamaAuthSupport;
import com.prwatech.skillama.service.SubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class BillingControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock private SubscriptionService subscriptionService;
    @Mock private SkillamaAuthSupport skillamaAuthSupport;

    @BeforeEach
    void setUp() {
        BillingController controller = new BillingController(subscriptionService, skillamaAuthSupport);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void listPlansReturnsOk() throws Exception {
        when(subscriptionService.listActivePlans())
                .thenReturn(List.of(SubscriptionPlanDTO.builder().code("PULSE").build()));

        mockMvc.perform(get("/skillama/billing/plans").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("PULSE"));
    }

    @Test
    void getSubscriptionResolvesUserAndReturnsOk() throws Exception {
        when(skillamaAuthSupport.resolveUserIdFromRequest(any())).thenReturn("u1");
        when(subscriptionService.getCurrentSubscription("u1"))
                .thenReturn(UserSubscriptionDTO.builder().planCode("SPARK").build());

        mockMvc.perform(get("/skillama/billing/subscription").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planCode").value("SPARK"));
    }

    @Test
    void getSubscriptionWithoutAuthReturns401() throws Exception {
        when(skillamaAuthSupport.resolveUserIdFromRequest(any()))
                .thenThrow(new SkillamaAuthException("Session expired. Please sign in again."));

        mockMvc.perform(get("/skillama/billing/subscription").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void checkoutReturnsOkWithOrder() throws Exception {
        when(skillamaAuthSupport.resolveUserIdFromRequest(any())).thenReturn("u1");
        when(subscriptionService.checkout(eq("u1"), any()))
                .thenReturn(BillingCheckoutResponseDTO.builder().orderId("ord_1").provider("MOCK").build());

        mockMvc.perform(post("/skillama/billing/checkout")
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("planCode", "PULSE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("ord_1"));
    }

    @Test
    void checkoutInvalidPlanReturns400() throws Exception {
        when(skillamaAuthSupport.resolveUserIdFromRequest(any())).thenReturn("u1");
        when(subscriptionService.checkout(eq("u1"), any()))
                .thenThrow(new IllegalArgumentException("Spark is free — no checkout required"));

        mockMvc.perform(post("/skillama/billing/checkout")
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("planCode", "SPARK"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void confirmPaymentFailureReturns402() throws Exception {
        when(skillamaAuthSupport.resolveUserIdFromRequest(any())).thenReturn("u1");
        when(subscriptionService.confirm(eq("u1"), any()))
                .thenThrow(new IllegalStateException("Payment failed"));

        mockMvc.perform(post("/skillama/billing/confirm")
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("orderId", "ord_1"))))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.error").value("Payment failed"));
    }

    @Test
    void confirmUnknownOrderReturns404() throws Exception {
        when(skillamaAuthSupport.resolveUserIdFromRequest(any())).thenReturn("u1");
        when(subscriptionService.confirm(eq("u1"), any()))
                .thenThrow(new ResourceNotFoundException("Payment order not found"));

        mockMvc.perform(post("/skillama/billing/confirm")
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("orderId", "ord_x"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void confirmSuccessReturnsOk() throws Exception {
        when(skillamaAuthSupport.resolveUserIdFromRequest(any())).thenReturn("u1");
        when(subscriptionService.confirm(eq("u1"), any()))
                .thenReturn(UserSubscriptionDTO.builder().planCode("PULSE").build());

        mockMvc.perform(post("/skillama/billing/confirm")
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("orderId", "ord_1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planCode").value("PULSE"));
    }

    @Test
    void cancelReturnsOk() throws Exception {
        when(skillamaAuthSupport.resolveUserIdFromRequest(any())).thenReturn("u1");
        when(subscriptionService.cancel("u1"))
                .thenReturn(UserSubscriptionDTO.builder().status("CANCELLED").build());

        mockMvc.perform(post("/skillama/billing/cancel").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancelWithoutActiveSubscriptionReturns404() throws Exception {
        when(skillamaAuthSupport.resolveUserIdFromRequest(any())).thenReturn("u1");
        when(subscriptionService.cancel("u1"))
                .thenThrow(new ResourceNotFoundException("No active subscription to cancel"));

        mockMvc.perform(post("/skillama/billing/cancel").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }
}
