package com.skillsync.session.dto;

import com.skillsync.session.model.enums.HoldStatus;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionHoldResponse {

    private Long id;
    private Long mentorId;
    private Long learnerId;
    private Long skillId;
    private Instant startAt;
    private Instant endAt;
    private BigDecimal quotedAmount;
    private Instant expiresAt;
    private HoldStatus status;
    private String idempotencyKey;
}
