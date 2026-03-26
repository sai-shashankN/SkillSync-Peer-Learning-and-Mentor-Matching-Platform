package com.skillsync.review.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BadgeResponse {

    private Integer id;
    private String name;
    private Long skillId;
    private String tier;
    private String description;
    private String iconUrl;
    private Integer requiredSessions;
}
