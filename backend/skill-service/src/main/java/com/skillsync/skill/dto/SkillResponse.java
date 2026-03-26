package com.skillsync.skill.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillResponse {

    private Long id;
    private String name;
    private Integer categoryId;
    private String categoryName;
    private String description;
    private String slug;
    private Boolean isActive;
    private Instant createdAt;
    private Instant updatedAt;
}
