package com.skillsync.session.dto;

import com.skillsync.session.model.enums.FeedbackType;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackResponse {

    private Long id;
    private Long sessionId;
    private Long userId;
    private Integer rating;
    private String comment;
    private FeedbackType feedbackType;
    private String submittedByRole;
    private Instant createdAt;
}
