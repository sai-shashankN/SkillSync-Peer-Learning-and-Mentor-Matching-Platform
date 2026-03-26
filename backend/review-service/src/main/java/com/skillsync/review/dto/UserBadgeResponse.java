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
public class UserBadgeResponse {

    private Long userId;
    private Integer badgeId;
    private String badgeName;
    private Long skillId;
    private String tier;
    private String iconUrl;
    private Long awardedForSkillId;
    private Instant earnedAt;
}
