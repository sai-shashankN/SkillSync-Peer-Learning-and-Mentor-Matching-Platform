package com.skillsync.session.service;

import com.skillsync.common.dto.PagedResponse;
import com.skillsync.common.exception.BadRequestException;
import com.skillsync.common.exception.ConflictException;
import com.skillsync.common.exception.ResourceNotFoundException;
import com.skillsync.common.exception.UnauthorizedException;
import com.skillsync.session.dto.CreateSessionRequest;
import com.skillsync.session.dto.SessionDetailResponse;
import com.skillsync.session.dto.SessionInternalResponse;
import com.skillsync.session.dto.SessionResponse;
import com.skillsync.session.dto.SessionSummaryResponse;
import com.skillsync.session.mapper.SessionMapper;
import com.skillsync.session.model.Session;
import com.skillsync.session.model.SessionBookingHold;
import com.skillsync.session.model.enums.HoldStatus;
import com.skillsync.session.model.enums.SessionStatus;
import com.skillsync.session.repository.SessionBookingHoldRepository;
import com.skillsync.session.repository.SessionRepository;
import com.skillsync.session.util.BookingReferenceGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SessionService {

    private final SessionRepository sessionRepository;
    private final SessionBookingHoldRepository holdRepository;
    private final SessionMapper sessionMapper;
    private final BookingReferenceGenerator bookingReferenceGenerator;
    private final EventPublisherService eventPublisherService;

    @Transactional
    public SessionResponse createSession(Long learnerId, CreateSessionRequest request) {
        SessionBookingHold hold = holdRepository.findById(request.getHoldId())
                .orElseThrow(() -> new ResourceNotFoundException("SessionBookingHold", "id", request.getHoldId()));

        if (!hold.getLearnerId().equals(learnerId)) {
            throw new UnauthorizedException("You are not allowed to create a session for this hold");
        }
        if (hold.getStatus() != HoldStatus.ACTIVE) {
            throw new BadRequestException("Hold is no longer active");
        }
        if (!hold.getExpiresAt().isAfter(Instant.now())) {
            hold.setStatus(HoldStatus.EXPIRED);
            holdRepository.save(hold);
            throw new BadRequestException("Hold has expired");
        }

        Session session = Session.builder()
                .holdId(hold.getId())
                .mentorId(hold.getMentorId())
                .learnerId(hold.getLearnerId())
                .skillId(hold.getSkillId())
                .bookingReference(bookingReferenceGenerator.generate())
                .startAt(hold.getStartAt())
                .endAt(hold.getEndAt())
                .durationMinutes(Math.toIntExact(Duration.between(hold.getStartAt(), hold.getEndAt()).toMinutes()))
                .topic(request.getTopic())
                .notes(request.getNotes())
                .status(SessionStatus.PAYMENT_PENDING)
                .paymentDeadlineAt(hold.getExpiresAt())
                .amount(hold.getQuotedAmount())
                .learnerTimezone(request.getLearnerTimezone())
                .build();

        hold.setStatus(HoldStatus.CONVERTED);
        Session savedSession = sessionRepository.save(session);
        holdRepository.save(hold);
        eventPublisherService.publishSessionBooked(savedSession);
        return sessionMapper.toSessionResponse(savedSession);
    }

    @Transactional(readOnly = true)
    public SessionDetailResponse getSessionById(Long id, Long userId, boolean admin) {
        Session session = getRequiredSession(id);
        assertAccessible(session, userId, admin);
        return sessionMapper.toSessionDetailResponse(session);
    }

    @Transactional(readOnly = true)
    public SessionDetailResponse getSessionByBookingReference(String bookingReference) {
        Session session = sessionRepository.findByBookingReference(bookingReference)
                .orElseThrow(() -> new ResourceNotFoundException("Session", "bookingReference", bookingReference));
        return sessionMapper.toSessionDetailResponse(session);
    }

    @Transactional(readOnly = true)
    public PagedResponse<SessionSummaryResponse> getMySessions(
            Long userId,
            String role,
            SessionStatus status,
            Pageable pageable
    ) {
        Page<Session> page = resolveRole(role).equals("MENTOR")
                ? getMentorSessions(userId, status, pageable)
                : getLearnerSessions(userId, status, pageable);
        return mapPage(page);
    }

    @Transactional(readOnly = true)
    public PagedResponse<SessionSummaryResponse> getSessionsForUser(Long userId, Pageable pageable) {
        return mapPage(sessionRepository.findByUserId(userId, pageable));
    }

    @Transactional(readOnly = true)
    public PagedResponse<SessionSummaryResponse> getSessionsAdmin(
            SessionStatus status,
            Long mentorId,
            Long learnerId,
            Instant from,
            Instant to,
            Pageable pageable
    ) {
        return mapPage(sessionRepository.findByFilters(status, mentorId, learnerId, from, to, pageable));
    }

    @Transactional
    public SessionResponse markPaid(Long sessionId) {
        Session session = getRequiredSession(sessionId);
        transition(session, List.of(SessionStatus.PAYMENT_PENDING), SessionStatus.PAID, "Only pending sessions can be marked as paid");
        session.setStatusReason(null);
        return sessionMapper.toSessionResponse(saveStateChange(session));
    }

    @Transactional
    public SessionResponse markPaymentFailed(Long sessionId) {
        Session session = getRequiredSession(sessionId);
        transition(
                session,
                List.of(SessionStatus.PAYMENT_PENDING),
                SessionStatus.PAYMENT_FAILED,
                "Only pending sessions can be marked as payment failed"
        );
        session.setStatusReason("Payment verification failed");
        return sessionMapper.toSessionResponse(saveStateChange(session));
    }

    @Transactional
    public SessionResponse acceptSession(Long sessionId, Long mentorId) {
        Session session = getRequiredSession(sessionId);
        assertMentorOwner(session, mentorId);
        transition(session, List.of(SessionStatus.PAID), SessionStatus.ACCEPTED, "Only paid sessions can be accepted");
        session.setAcceptedAt(Instant.now());
        Session updated = saveStateChange(session);
        eventPublisherService.publishSessionAccepted(updated);
        return sessionMapper.toSessionResponse(updated);
    }

    @Transactional
    public SessionResponse rejectSession(Long sessionId, Long mentorId, String reason) {
        Session session = getRequiredSession(sessionId);
        assertMentorOwner(session, mentorId);
        transition(session, List.of(SessionStatus.PAID), SessionStatus.REJECTED, "Only paid sessions can be rejected");
        session.setStatusReason(reason);
        Session updated = saveStateChange(session);
        eventPublisherService.publishSessionRejected(updated);
        return sessionMapper.toSessionResponse(updated);
    }

    @Transactional
    public SessionResponse cancelSession(Long sessionId, Long userId, boolean admin, String reason) {
        Session session = getRequiredSession(sessionId);
        assertOwnerOrAdmin(session, userId, admin);
        transition(
                session,
                List.of(SessionStatus.PAYMENT_PENDING, SessionStatus.PAID, SessionStatus.ACCEPTED),
                SessionStatus.CANCELLED,
                "Session cannot be cancelled in its current state"
        );
        session.setCancelledAt(Instant.now());
        session.setCancelledByUserId(userId);
        session.setStatusReason(reason);
        Session updated = saveStateChange(session);
        eventPublisherService.publishSessionCancelled(updated, reason);
        return sessionMapper.toSessionResponse(updated);
    }

    @Transactional
    public SessionResponse completeSession(Long sessionId, Long userId, boolean admin) {
        Session session = getRequiredSession(sessionId);
        if (!admin) {
            assertMentorOwner(session, userId);
        }
        transition(session, List.of(SessionStatus.ACCEPTED), SessionStatus.COMPLETED, "Only accepted sessions can be completed");
        session.setCompletedAt(Instant.now());
        Session updated = saveStateChange(session);
        eventPublisherService.publishSessionCompleted(updated);
        return sessionMapper.toSessionResponse(updated);
    }

    @Transactional
    public void updateIntegrationFields(Long sessionId, String zoomLink, String calendarEventId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session", "id", sessionId));
        if (zoomLink != null) {
            session.setZoomLink(zoomLink);
        }
        if (calendarEventId != null) {
            session.setCalendarEventId(calendarEventId);
        }
        sessionRepository.save(session);
    }

    @Transactional
    @Scheduled(fixedRate = 60000)
    public void expireSessions() {
        List<Session> expiredSessions = sessionRepository.findByStatusAndPaymentDeadlineAtBefore(
                SessionStatus.PAYMENT_PENDING,
                Instant.now()
        );
        for (Session session : expiredSessions) {
            session.setStatus(SessionStatus.EXPIRED);
            session.setStatusReason("Payment deadline expired");
        }
        sessionRepository.saveAll(expiredSessions);
    }

    @Transactional(readOnly = true)
    public Session getRequiredSession(Long id) {
        return sessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Session", "id", id));
    }

    @Transactional(readOnly = true)
    public long countByLearnerIdAndSkillIdAndStatus(Long learnerId, Long skillId, SessionStatus status) {
        return sessionRepository.countByLearnerIdAndSkillIdAndStatus(learnerId, skillId, status);
    }

    @Transactional(readOnly = true)
    public List<SessionInternalResponse> getUpcomingSessions(int withinMinutes) {
        Instant now = Instant.now();
        Instant cutoff = now.plus(withinMinutes, java.time.temporal.ChronoUnit.MINUTES);
        List<Session> sessions = sessionRepository.findByStatusAndStartAtBetween(SessionStatus.ACCEPTED, now, cutoff);
        return sessions.stream()
                .map(session -> new SessionInternalResponse(
                        session.getId(),
                        session.getMentorId(),
                        session.getLearnerId(),
                        session.getSkillId(),
                        session.getStartAt(),
                        session.getEndAt(),
                        session.getStatus().name(),
                        session.getZoomLink(),
                        session.getCalendarEventId()
                ))
                .toList();
    }

    private Page<Session> getLearnerSessions(Long userId, SessionStatus status, Pageable pageable) {
        return status == null
                ? sessionRepository.findByLearnerId(userId, pageable)
                : sessionRepository.findByLearnerIdAndStatusIn(userId, List.of(status), pageable);
    }

    private Page<Session> getMentorSessions(Long userId, SessionStatus status, Pageable pageable) {
        return status == null
                ? sessionRepository.findByMentorId(userId, pageable)
                : sessionRepository.findByMentorIdAndStatusIn(userId, List.of(status), pageable);
    }

    private PagedResponse<SessionSummaryResponse> mapPage(Page<Session> page) {
        return PagedResponse.<SessionSummaryResponse>builder()
                .content(page.getContent().stream().map(sessionMapper::toSessionSummaryResponse).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    private String resolveRole(String role) {
        if (role == null || role.isBlank() || "LEARNER".equalsIgnoreCase(role)) {
            return "LEARNER";
        }
        if ("MENTOR".equalsIgnoreCase(role)) {
            return "MENTOR";
        }
        throw new BadRequestException("Role must be either LEARNER or MENTOR");
    }

    private void transition(Session session, List<SessionStatus> allowedStatuses, SessionStatus targetStatus, String message) {
        if (!allowedStatuses.contains(session.getStatus())) {
            throw new BadRequestException(message);
        }
        session.setStatus(targetStatus);
    }

    private Session saveStateChange(Session session) {
        try {
            return sessionRepository.saveAndFlush(session);
        } catch (OptimisticLockingFailureException ex) {
            throw new ConflictException("Session was updated by another request");
        }
    }

    private void assertAccessible(Session session, Long userId, boolean admin) {
        if (!admin && !session.getLearnerId().equals(userId) && !session.getMentorId().equals(userId)) {
            throw new UnauthorizedException("You are not allowed to access this session");
        }
    }

    private void assertOwnerOrAdmin(Session session, Long userId, boolean admin) {
        if (!admin && !session.getLearnerId().equals(userId) && !session.getMentorId().equals(userId)) {
            throw new UnauthorizedException("You are not allowed to update this session");
        }
    }

    private void assertMentorOwner(Session session, Long mentorId) {
        if (!session.getMentorId().equals(mentorId)) {
            throw new UnauthorizedException("You are not allowed to update this session");
        }
    }
}
