package com.skillsync.user.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddUserSkillRequest {

    @NotNull(message = "Skill ID is required")
    private Long skillId;

    @NotNull(message = "Proficiency is required")
    @Pattern(
            regexp = "BEGINNER|INTERMEDIATE|ADVANCED",
            message = "Proficiency must be BEGINNER, INTERMEDIATE, or ADVANCED"
    )
    private String proficiency;
}
