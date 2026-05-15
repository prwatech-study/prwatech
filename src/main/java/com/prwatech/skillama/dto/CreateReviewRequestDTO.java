package com.prwatech.skillama.dto;

import com.prwatech.skillama.model.Review;
import lombok.Data;

@Data
public class CreateReviewRequestDTO {
    private String courseId;
    private int rating;
    private String comment;
    private Review.ReviewScope scope;
}
