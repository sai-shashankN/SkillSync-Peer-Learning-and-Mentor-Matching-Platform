package com.skillsync.audit.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminKpiResponse {
    private Long totalUsers;
    private Long newUsersToday;
    private Long totalSessions;
    private Long completedSessions;
    private Long cancelledSessions;
    private BigDecimal totalRevenue;
    private BigDecimal totalRefunds;
    private Long activeMentors;
    private Long newReviews;
    private Long badgesAwarded;
}
