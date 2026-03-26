package com.skillsync.session.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
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
public class CreateHoldRequest {

    @NotNull
    private Long mentorId;

    @NotNull
    private Long skillId;

    @NotNull
    @Future
    private Instant startAt;

    @NotNull
    @Min(30)
    private Integer durationMinutes;
}
