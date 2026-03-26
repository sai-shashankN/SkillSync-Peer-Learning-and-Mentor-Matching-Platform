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
public class SessionDetailResponse {

    private Long id;
    private String bookingReference;
    private Long mentorId;
    private Long learnerId;
    private Long skillId;
    private Instant startAt;
    private Instant endAt;
    private Integer durationMinutes;
    private String topic;
    private SessionStatus status;
    private BigDecimal amount;
    private Integer version;
    private String notes;
    private String statusReason;
    private Instant paymentDeadlineAt;
    private String zoomLink;
    private String calendarEventId;
    private String learnerTimezone;
    private String mentorTimezone;
    private Instant acceptedAt;
    private Instant completedAt;
    private Instant cancelledAt;
    private Long cancelledByUserId;
    private Instant createdAt;
}
