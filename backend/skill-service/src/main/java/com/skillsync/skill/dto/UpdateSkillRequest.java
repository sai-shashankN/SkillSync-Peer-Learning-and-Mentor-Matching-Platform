package com.skillsync.skill.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSkillRequest {

    @Size(max = 100, message = "Name must not exceed 100 characters")
    private String name;

    private Integer categoryId;

    private String description;

    @Size(max = 120, message = "Slug must not exceed 120 characters")
    private String slug;

    private Boolean isActive;
}
