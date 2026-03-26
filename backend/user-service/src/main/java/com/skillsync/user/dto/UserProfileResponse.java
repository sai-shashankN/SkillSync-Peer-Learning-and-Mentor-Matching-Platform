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
public class UserProfileResponse {

    private Long id;
    private Long userId;
    private String email;
    private String name;
    private String bio;
    private String phone;
    private String avatarUrl;
    private String timezone;
    private String language;
    private boolean darkMode;
    private boolean marketingOptIn;
    private String profileVisibility;
    private List<String> roles;
    private List<UserSkillResponse> skills;
    private Instant createdAt;
}
