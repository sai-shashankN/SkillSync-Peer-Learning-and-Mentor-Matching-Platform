package com.skillsync.session.mapper;

import com.skillsync.session.dto.FeedbackResponse;
import com.skillsync.session.dto.SessionDetailResponse;
import com.skillsync.session.dto.SessionHoldResponse;
import com.skillsync.session.dto.SessionResponse;
import com.skillsync.session.dto.SessionSummaryResponse;
import com.skillsync.session.model.Session;
import com.skillsync.session.model.SessionBookingHold;
import com.skillsync.session.model.SessionFeedback;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface SessionMapper {

    SessionHoldResponse toHoldResponse(SessionBookingHold hold);

    SessionResponse toSessionResponse(Session session);

    SessionDetailResponse toSessionDetailResponse(Session session);

    SessionSummaryResponse toSessionSummaryResponse(Session session);

    FeedbackResponse toFeedbackResponse(SessionFeedback feedback);
}
