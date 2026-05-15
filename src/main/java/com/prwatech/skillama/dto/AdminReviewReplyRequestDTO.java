package com.prwatech.skillama.dto;

import com.prwatech.skillama.model.Review;
import lombok.Data;

@Data
public class AdminReviewReplyRequestDTO {
    private String teamReply;
    private Review.ReviewStatus status;
}
