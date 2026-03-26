package com.skillsync.user.dto;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSummaryResponse {

    private Long userId;
    private String email;
    private String name;
    private String avatarUrl;
    private List<String> roles;
    private boolean isActive;
    private Instant createdAt;
}
