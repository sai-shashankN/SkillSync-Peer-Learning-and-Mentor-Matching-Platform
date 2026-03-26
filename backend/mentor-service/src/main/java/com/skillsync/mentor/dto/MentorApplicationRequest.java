package com.skillsync.mentor.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MentorApplicationRequest {

    private String headline;

    @NotBlank(message = "Bio is required")
    private String bio;

    @Min(value = 0, message = "Experience years must be 0 or greater")
    private Integer experienceYears;

    private String experience;

    @NotNull(message = "Hourly rate is required")
    @DecimalMin(value = "1.00", message = "Hourly rate must be at least 1.00")
    private BigDecimal hourlyRate;

    private Set<Long> skillIds;
}
