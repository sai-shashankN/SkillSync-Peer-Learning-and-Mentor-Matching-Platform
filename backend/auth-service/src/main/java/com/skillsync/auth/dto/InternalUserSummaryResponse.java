package com.skillsync.auth.dto;

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
public class InternalUserSummaryResponse {

    private Long userId;
    private String email;
    private String name;
    private List<String> roles;
    private boolean active;
    private Instant createdAt;
}
