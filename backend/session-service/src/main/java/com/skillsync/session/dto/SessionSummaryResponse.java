package com.skillsync.session.dto;

import com.skillsync.session.model.enums.SessionStatus;
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
public class SessionSummaryResponse {

    private Long id;
    private String bookingReference;
    private Long mentorId;
    private Long learnerId;
    private Instant startAt;
    private Integer durationMinutes;
    private String topic;
    private SessionStatus status;
    private BigDecimal amount;
}
