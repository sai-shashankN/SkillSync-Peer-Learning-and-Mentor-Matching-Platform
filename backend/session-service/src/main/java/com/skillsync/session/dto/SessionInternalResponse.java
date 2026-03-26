package com.skillsync.session.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionInternalResponse {

    private Long id;
    private Long mentorId;
    private Long learnerId;
    private Long skillId;
    private Instant startAt;
    private Instant endAt;
    private String status;
    private String zoomLink;
    private String calendarEventId;
}
