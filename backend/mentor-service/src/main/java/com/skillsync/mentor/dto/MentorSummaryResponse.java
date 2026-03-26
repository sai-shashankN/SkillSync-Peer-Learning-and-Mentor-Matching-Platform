package com.skillsync.mentor.dto;

import java.math.BigDecimal;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MentorSummaryResponse {

    private Long id;
    private Long userId;
    private String userName;
    private String headline;
    private BigDecimal hourlyRate;
    private BigDecimal avgRating;
    private Integer totalSessions;
    private Integer totalReviews;
    private Set<Long> skillIds;
}
