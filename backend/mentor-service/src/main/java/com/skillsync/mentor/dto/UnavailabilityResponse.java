package com.skillsync.mentor.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnavailabilityResponse {

    private Long id;
    private Instant blockedFrom;
    private Instant blockedTo;
    private String reason;
}
