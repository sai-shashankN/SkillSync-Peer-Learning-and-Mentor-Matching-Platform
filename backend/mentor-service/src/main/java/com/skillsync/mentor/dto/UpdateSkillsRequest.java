package com.skillsync.mentor.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSkillsRequest {

    @NotEmpty(message = "At least one skill is required")
    private Set<Long> skillIds;
}
