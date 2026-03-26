package com.skillsync.review.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewResponse {

    private Long id;
    private Long mentorId;
    private Long learnerId;
    private Long sessionId;
    private Integer rating;
    private String comment;
    private Boolean isVisible;
    private Instant createdAt;
}
