package com.skillsync.mentor.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMentorRequest {

    private String bio;
    private String headline;

    @Min(value = 0, message = "Experience years must be 0 or greater")
    private Integer experienceYears;

    @DecimalMin(value = "1.00", message = "Hourly rate must be at least 1.00")
    private BigDecimal hourlyRate;
}
