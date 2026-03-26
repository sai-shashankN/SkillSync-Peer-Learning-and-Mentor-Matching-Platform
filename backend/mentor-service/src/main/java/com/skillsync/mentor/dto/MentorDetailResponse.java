package com.skillsync.mentor.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MentorDetailResponse {

    private Long id;
    private Long userId;
    private String userName;
    private String headline;
    private String bio;
    private Integer experienceYears;
    private BigDecimal hourlyRate;
    private BigDecimal avgRating;
    private Integer totalSessions;
    private Integer totalReviews;
    private String status;
    private Set<Long> skillIds;
    private List<AvailabilityResponse> availability;
    private Instant createdAt;
}
