package com.prwatech.skillama.controller;

import com.prwatech.authentication.security.JwtUtils;
import com.prwatech.common.Constants;
import com.prwatech.skillama.model.Review;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.service.ReviewService;
import com.prwatech.skillama.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ReviewControllerIdorTest {

    private MockMvc mockMvc;

    @Mock private ReviewService reviewService;
    @Mock private UserService userService;
    @Mock private JwtUtils jwtUtils;

    private static final String TOKEN = "Bearer valid.jwt.token";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ReviewController(reviewService, userService, jwtUtils))
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    @Test
    void createReview_withoutAuth_returns401() throws Exception {
        mockMvc.perform(post("/skillama/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"victim\",\"comment\":\"hi\",\"rating\":5}"))
                .andExpect(status().isUnauthorized());
        verify(reviewService, never()).saveReview(any(Review.class));
    }

    @Test
    void createReview_ignoresBodyUserId() throws Exception {
        when(jwtUtils.extractUsername("valid.jwt.token")).thenReturn("learner@example.com");
        when(userService.findByEmail("learner@example.com"))
                .thenReturn(Optional.of(User.builder().id("caller").email("learner@example.com").build()));
        when(reviewService.saveReview(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/skillama/review")
                        .header(Constants.AUTH, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"victim\",\"comment\":\"great\",\"rating\":5}"))
                .andExpect(status().isOk());

        ArgumentCaptor<Review> captor = ArgumentCaptor.forClass(Review.class);
        verify(reviewService).saveReview(captor.capture());
        assertEquals("caller", captor.getValue().getUserId());
    }
}
