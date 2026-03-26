package com.skillsync.session.service;

import com.skillsync.common.exception.BadRequestException;
import com.skillsync.common.exception.ConflictException;
import com.skillsync.common.exception.UnauthorizedException;
import com.skillsync.session.dto.CreateFeedbackRequest;
import com.skillsync.session.dto.FeedbackResponse;
import com.skillsync.session.mapper.SessionMapper;
import com.skillsync.session.model.Session;
import com.skillsync.session.model.SessionFeedback;
import com.skillsync.session.model.enums.FeedbackType;
import com.skillsync.session.model.enums.SessionStatus;
import com.skillsync.session.repository.SessionFeedbackRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final SessionService sessionService;
    private final SessionFeedbackRepository feedbackRepository;
    private final SessionMapper sessionMapper;
    private final EventPublisherService eventPublisherService;

    @Transactional
    public FeedbackResponse submitFeedback(Long sessionId, Long userId, CreateFeedbackRequest request) {
        Session session = sessionService.getRequiredSession(sessionId);
        if (session.getStatus() != SessionStatus.COMPLETED) {
            throw new BadRequestException("Feedback can only be submitted for completed sessions");
        }

        String submittedByRole = resolveRole(session, userId);
        validateFeedbackType(request.getFeedbackType(), submittedByRole);

        if (feedbackRepository.existsBySessionIdAndUserId(sessionId, userId)) {
            throw new ConflictException("Feedback has already been submitted for this session");
        }

        SessionFeedback feedback = SessionFeedback.builder()
                .sessionId(sessionId)
                .userId(userId)
                .rating(request.getRating())
                .comment(request.getComment())
                .feedbackType(request.getFeedbackType())
                .submittedByRole(submittedByRole)
                .build();

        SessionFeedback saved = feedbackRepository.save(feedback);
        if (request.getFeedbackType() == FeedbackType.LEARNER_TO_MENTOR) {
            eventPublisherService.publishReviewSubmitted(saved, session);
        }
        return sessionMapper.toFeedbackResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<FeedbackResponse> getFeedbackForSession(Long sessionId) {
        sessionService.getRequiredSession(sessionId);
        return feedbackRepository.findBySessionId(sessionId).stream()
                .map(sessionMapper::toFeedbackResponse)
                .toList();
    }

    private String resolveRole(Session session, Long userId) {
        if (session.getLearnerId().equals(userId)) {
            return "LEARNER";
        }
        if (session.getMentorId().equals(userId)) {
            return "MENTOR";
        }
        throw new UnauthorizedException("You are not allowed to submit feedback for this session");
    }

    private void validateFeedbackType(FeedbackType feedbackType, String submittedByRole) {
        if ("LEARNER".equals(submittedByRole) && feedbackType != FeedbackType.LEARNER_TO_MENTOR) {
            throw new BadRequestException("Learners can only submit LEARNER_TO_MENTOR feedback");
        }
        if ("MENTOR".equals(submittedByRole) && feedbackType != FeedbackType.MENTOR_TO_LEARNER) {
            throw new BadRequestException("Mentors can only submit MENTOR_TO_LEARNER feedback");
        }
    }
}
