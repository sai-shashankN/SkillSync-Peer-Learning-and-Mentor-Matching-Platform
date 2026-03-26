package com.skillsync.mentor.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnavailabilityRequest {

    @NotNull(message = "Blocked from is required")
    @Future(message = "Blocked from must be in the future")
    private Instant blockedFrom;

    @NotNull(message = "Blocked to is required")
    @Future(message = "Blocked to must be in the future")
    private Instant blockedTo;

    private String reason;
}
