package com.skillsync.session.service;

import com.skillsync.common.exception.BadRequestException;
import com.skillsync.common.exception.ConflictException;
import com.skillsync.common.exception.ResourceNotFoundException;
import com.skillsync.session.client.MentorClient;
import com.skillsync.session.client.MentorClient.MentorSnapshot;
import com.skillsync.session.dto.CreateHoldRequest;
import com.skillsync.session.dto.SessionHoldResponse;
import com.skillsync.session.mapper.SessionMapper;
import com.skillsync.session.model.SessionBookingHold;
import com.skillsync.session.model.enums.HoldStatus;
import com.skillsync.session.repository.SessionBookingHoldRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class HoldService {

    private static final List<Integer> ALLOWED_DURATIONS = List.of(30, 60, 90);

    private final SessionBookingHoldRepository holdRepository;
    private final SessionMapper sessionMapper;
    private final MentorClient mentorClient;

    @Transactional
    public SessionHoldResponse createHold(Long learnerId, CreateHoldRequest request, String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new BadRequestException("Idempotency-Key header is required");
        }

        SessionBookingHold existingHold = holdRepository.findByIdempotencyKey(idempotencyKey.trim()).orElse(null);
        if (existingHold != null) {
            if (!existingHold.getLearnerId().equals(learnerId)) {
                throw new ConflictException("Idempotency key is already associated with another learner");
            }
            return sessionMapper.toHoldResponse(existingHold);
        }

        return createNewHold(learnerId, request, idempotencyKey.trim());
    }

    @Transactional
    public void cancelHold(Long holdId, Long userId) {
        SessionBookingHold hold = getRequiredHold(holdId);
        if (!hold.getLearnerId().equals(userId)) {
            throw new BadRequestException("You are not allowed to cancel this hold");
        }
        if (hold.getStatus() != HoldStatus.ACTIVE) {
            throw new BadRequestException("Only active holds can be cancelled");
        }
        hold.setStatus(HoldStatus.CANCELLED);
        holdRepository.save(hold);
    }

    @Transactional
    @Scheduled(fixedRate = 60000)
    public void expireHolds() {
        List<SessionBookingHold> expiredHolds = holdRepository.findByStatusAndExpiresAtBefore(HoldStatus.ACTIVE, Instant.now());
        expiredHolds.forEach(hold -> hold.setStatus(HoldStatus.EXPIRED));
        holdRepository.saveAll(expiredHolds);
    }

    public SessionBookingHold getRequiredHold(Long holdId) {
        return holdRepository.findById(holdId)
                .orElseThrow(() -> new ResourceNotFoundException("SessionBookingHold", "id", holdId));
    }

    private SessionHoldResponse createNewHold(Long learnerId, CreateHoldRequest request, String idempotencyKey) {
        validateHoldRequest(request);

        MentorSnapshot mentor = mentorClient.getMentor(request.getMentorId());
        if (!"APPROVED".equalsIgnoreCase(mentor.status())) {
            throw new BadRequestException("Mentor is not approved for bookings");
        }

        Instant now = Instant.now();
        Instant endAt = request.getStartAt().plus(Duration.ofMinutes(request.getDurationMinutes()));
        BigDecimal quotedAmount = mentor.hourlyRate()
                .multiply(BigDecimal.valueOf(request.getDurationMinutes()))
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);

        SessionBookingHold hold = SessionBookingHold.builder()
                .mentorId(request.getMentorId())
                .learnerId(learnerId)
                .skillId(request.getSkillId())
                .startAt(request.getStartAt())
                .endAt(endAt)
                .quotedAmount(quotedAmount)
                .expiresAt(now.plus(Duration.ofMinutes(15)))
                .status(HoldStatus.ACTIVE)
                .idempotencyKey(idempotencyKey)
                .build();

        try {
            return sessionMapper.toHoldResponse(holdRepository.saveAndFlush(hold));
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Time slot is not available");
        }
    }

    private void validateHoldRequest(CreateHoldRequest request) {
        if (!ALLOWED_DURATIONS.contains(request.getDurationMinutes())) {
            throw new BadRequestException("Duration must be one of 30, 60, or 90 minutes");
        }

        Instant minimumStart = Instant.now().plus(Duration.ofHours(1));
        if (request.getStartAt().isBefore(minimumStart)) {
            throw new BadRequestException("Session must be booked at least 1 hour in advance");
        }
    }
}
