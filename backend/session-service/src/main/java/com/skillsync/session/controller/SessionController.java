package com.skillsync.session.controller;

import com.skillsync.common.dto.ApiResponse;
import com.skillsync.common.dto.PagedResponse;
import com.skillsync.common.exception.BadRequestException;
import com.skillsync.common.exception.UnauthorizedException;
import com.skillsync.session.dto.CancelSessionRequest;
import com.skillsync.session.dto.CreateFeedbackRequest;
import com.skillsync.session.dto.CreateHoldRequest;
import com.skillsync.session.dto.CreateSessionRequest;
import com.skillsync.session.dto.FeedbackResponse;
import com.skillsync.session.dto.RejectSessionRequest;
import com.skillsync.session.dto.SessionDetailResponse;
import com.skillsync.session.dto.SessionHoldResponse;
import com.skillsync.session.dto.SessionInternalResponse;
import com.skillsync.session.dto.SessionResponse;
import com.skillsync.session.dto.SessionSummaryResponse;
import com.skillsync.session.dto.UpdateSessionIntegrationRequest;
import com.skillsync.session.model.Session;
import com.skillsync.session.model.enums.SessionStatus;
import com.skillsync.session.service.FeedbackService;
import com.skillsync.session.service.HoldService;
import com.skillsync.session.service.SessionService;
import com.skillsync.session.util.RequestHeaderUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final HoldService holdService;
    private final SessionService sessionService;
    private final FeedbackService feedbackService;

    @PostMapping("/holds")
    public ResponseEntity<ApiResponse<SessionHoldResponse>> createHold(
            HttpServletRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateHoldRequest createHoldRequest
    ) {
        SessionHoldResponse response = holdService.createHold(
                RequestHeaderUtils.extractUserId(request),
                createHoldRequest,
                requireIdempotencyKey(idempotencyKey)
        );
        return ResponseEntity.ok(ApiResponse.ok("Session hold created successfully", response));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SessionResponse>> createSession(
            HttpServletRequest request,
            @Valid @RequestBody CreateSessionRequest createSessionRequest
    ) {
        SessionResponse response = sessionService.createSession(
                RequestHeaderUtils.extractUserId(request),
                createSessionRequest
        );
        return ResponseEntity.ok(ApiResponse.ok("Session created successfully", response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SessionDetailResponse>> getSessionById(
            HttpServletRequest request,
            @PathVariable Long id
    ) {
        SessionDetailResponse response = sessionService.getSessionById(
                id,
                RequestHeaderUtils.extractUserId(request),
                RequestHeaderUtils.hasAdminRole(request)
        );
        return ResponseEntity.ok(ApiResponse.ok("Session fetched successfully", response));
    }

    @GetMapping("/internal/{id}")
    public ResponseEntity<ApiResponse<SessionInternalResponse>> getInternalSessionById(@PathVariable Long id) {
        Session session = sessionService.getRequiredSession(id);
        SessionInternalResponse response = SessionInternalResponse.builder()
                .id(session.getId())
                .mentorId(session.getMentorId())
                .learnerId(session.getLearnerId())
                .skillId(session.getSkillId())
                .startAt(session.getStartAt())
                .endAt(session.getEndAt())
                .status(session.getStatus().name())
                .zoomLink(session.getZoomLink())
                .calendarEventId(session.getCalendarEventId())
                .build();
        return ResponseEntity.ok(ApiResponse.ok("Internal session fetched successfully", response));
    }

    @PatchMapping("/internal/{id}/integration")
    public ResponseEntity<ApiResponse<Void>> updateSessionIntegration(
            @PathVariable Long id,
            @RequestBody UpdateSessionIntegrationRequest request
    ) {
        sessionService.updateIntegrationFields(id, request.getZoomLink(), request.getCalendarEventId());
        return ResponseEntity.ok(ApiResponse.ok("Session integration fields updated", null));
    }

    @GetMapping("/internal/count")
    public ResponseEntity<ApiResponse<Long>> countSessions(
            @RequestParam Long learnerId,
            @RequestParam Long skillId,
            @RequestParam SessionStatus status
    ) {
        long count = sessionService.countByLearnerIdAndSkillIdAndStatus(learnerId, skillId, status);
        return ResponseEntity.ok(ApiResponse.ok("Session count fetched successfully", count));
    }

    @GetMapping("/internal/upcoming")
    public ResponseEntity<ApiResponse<List<SessionInternalResponse>>> getUpcomingSessions(
            @RequestParam(defaultValue = "60") int withinMinutes
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Upcoming sessions fetched",
                sessionService.getUpcomingSessions(withinMinutes)
        ));
    }

    @GetMapping("/me")
    public ResponseEntity<PagedResponse<SessionSummaryResponse>> getMySessions(
            HttpServletRequest request,
            @RequestParam(required = false) SessionStatus status,
            @RequestParam(required = false) String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startAt"));
        return ResponseEntity.ok(sessionService.getMySessions(
                RequestHeaderUtils.extractUserId(request),
                role,
                status,
                pageable
        ));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<PagedResponse<SessionSummaryResponse>> getUserSessions(
            HttpServletRequest request,
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        RequestHeaderUtils.requireAdmin(request);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startAt"));
        return ResponseEntity.ok(sessionService.getSessionsForUser(userId, pageable));
    }

    @GetMapping("/admin")
    public ResponseEntity<PagedResponse<SessionSummaryResponse>> getSessionsAdmin(
            HttpServletRequest request,
            @RequestParam(required = false) SessionStatus status,
            @RequestParam(required = false) Long mentorId,
            @RequestParam(required = false) Long learnerId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        RequestHeaderUtils.requireAdmin(request);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startAt"));
        return ResponseEntity.ok(sessionService.getSessionsAdmin(status, mentorId, learnerId, from, to, pageable));
    }

    @PutMapping("/{id}/accept")
    public ResponseEntity<ApiResponse<SessionResponse>> acceptSession(HttpServletRequest request, @PathVariable Long id) {
        SessionResponse response = sessionService.acceptSession(id, RequestHeaderUtils.extractUserId(request));
        return ResponseEntity.ok(ApiResponse.ok("Session accepted successfully", response));
    }

    @PutMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<SessionResponse>> rejectSession(
            HttpServletRequest request,
            @PathVariable Long id,
            @Valid @RequestBody RejectSessionRequest rejectSessionRequest
    ) {
        SessionResponse response = sessionService.rejectSession(
                id,
                RequestHeaderUtils.extractUserId(request),
                rejectSessionRequest.getReason()
        );
        return ResponseEntity.ok(ApiResponse.ok("Session rejected successfully", response));
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<SessionResponse>> cancelSession(
            HttpServletRequest request,
            @PathVariable Long id,
            @Valid @RequestBody CancelSessionRequest cancelSessionRequest
    ) {
        SessionResponse response = sessionService.cancelSession(
                id,
                RequestHeaderUtils.extractUserId(request),
                RequestHeaderUtils.hasAdminRole(request),
                cancelSessionRequest.getReason()
        );
        return ResponseEntity.ok(ApiResponse.ok("Session cancelled successfully", response));
    }

    @PutMapping("/{id}/complete")
    public ResponseEntity<ApiResponse<SessionResponse>> completeSession(HttpServletRequest request, @PathVariable Long id) {
        SessionResponse response = sessionService.completeSession(
                id,
                RequestHeaderUtils.extractUserId(request),
                RequestHeaderUtils.hasAdminRole(request)
        );
        return ResponseEntity.ok(ApiResponse.ok("Session completed successfully", response));
    }

    @PostMapping("/{id}/feedback")
    public ResponseEntity<ApiResponse<FeedbackResponse>> submitFeedback(
            HttpServletRequest request,
            @PathVariable Long id,
            @Valid @RequestBody CreateFeedbackRequest createFeedbackRequest
    ) {
        FeedbackResponse response = feedbackService.submitFeedback(
                id,
                RequestHeaderUtils.extractUserId(request),
                createFeedbackRequest
        );
        return ResponseEntity.ok(ApiResponse.ok("Feedback submitted successfully", response));
    }

    @GetMapping("/{id}/feedback")
    public ResponseEntity<ApiResponse<List<FeedbackResponse>>> getFeedbackForSession(
            HttpServletRequest request,
            @PathVariable Long id
    ) {
        Session session = sessionService.getRequiredSession(id);
        Long userId = RequestHeaderUtils.extractUserId(request);
        boolean admin = RequestHeaderUtils.hasAdminRole(request);
        if (!admin && !session.getLearnerId().equals(userId) && !session.getMentorId().equals(userId)) {
            throw new UnauthorizedException("You are not allowed to access this session");
        }
        return ResponseEntity.ok(ApiResponse.ok("Feedback fetched successfully", feedbackService.getFeedbackForSession(id)));
    }

    @PutMapping("/internal/{id}/mark-paid")
    public ResponseEntity<ApiResponse<SessionResponse>> markPaid(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Session marked as paid", sessionService.markPaid(id)));
    }

    @PutMapping("/internal/{id}/payment-failed")
    public ResponseEntity<ApiResponse<SessionResponse>> markPaymentFailed(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Session marked as payment failed", sessionService.markPaymentFailed(id)));
    }

    private String requireIdempotencyKey(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new BadRequestException("Idempotency-Key header is required");
        }
        return idempotencyKey.trim();
    }
}
