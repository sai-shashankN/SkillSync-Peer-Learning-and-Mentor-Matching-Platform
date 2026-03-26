package com.skillsync.review.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RatingAverageResponse {

    private Long mentorId;
    private Double averageRating;
    private Long totalReviews;
}
