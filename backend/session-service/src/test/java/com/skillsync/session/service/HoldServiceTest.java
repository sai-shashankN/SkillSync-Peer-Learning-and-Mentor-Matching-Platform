package com.skillsync.session.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.skillsync.common.exception.BadRequestException;
import com.skillsync.common.exception.ConflictException;
import com.skillsync.session.client.MentorClient;
import com.skillsync.session.dto.CreateHoldRequest;
import com.skillsync.session.dto.SessionHoldResponse;
import com.skillsync.session.mapper.SessionMapper;
import com.skillsync.session.model.SessionBookingHold;
import com.skillsync.session.model.enums.HoldStatus;
import com.skillsync.session.repository.SessionBookingHoldRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HoldServiceTest {

    @Mock
    private SessionBookingHoldRepository holdRepository;

    @Mock
    private SessionMapper sessionMapper;

    @Mock
    private MentorClient mentorClient;

    private HoldService holdService;

    @BeforeEach
    void setUp() {
        holdService = new HoldService(holdRepository, sessionMapper, mentorClient);
    }

    @Test
    void createHoldReturnsExistingHoldForSameLearnerAndKey() {
        SessionBookingHold existingHold = SessionBookingHold.builder()
                .id(3L)
                .mentorId(7L)
                .learnerId(11L)
                .skillId(5L)
                .startAt(Instant.now().plus(Duration.ofHours(2)))
                .endAt(Instant.now().plus(Duration.ofHours(3)))
                .quotedAmount(new BigDecimal("250.00"))
                .expiresAt(Instant.now().plus(Duration.ofMinutes(15)))
                .status(HoldStatus.ACTIVE)
                .idempotencyKey("hold-key")
                .build();
        SessionHoldResponse mappedResponse = SessionHoldResponse.builder()
                .id(3L)
                .idempotencyKey("hold-key")
                .status(HoldStatus.ACTIVE)
                .build();

        when(holdRepository.findByIdempotencyKey("hold-key")).thenReturn(Optional.of(existingHold));
        when(sessionMapper.toHoldResponse(existingHold)).thenReturn(mappedResponse);

        SessionHoldResponse result = holdService.createHold(11L, validRequest(), " hold-key ");

        assertThat(result).isEqualTo(mappedResponse);
        verify(sessionMapper).toHoldResponse(existingHold);
        verify(holdRepository, never()).saveAndFlush(any(SessionBookingHold.class));
        verifyNoInteractions(mentorClient);
    }

    @Test
    void createHoldRejectsIdempotencyKeyReuseAcrossLearners() {
        SessionBookingHold existingHold = SessionBookingHold.builder()
                .learnerId(99L)
                .idempotencyKey("hold-key")
                .build();

        when(holdRepository.findByIdempotencyKey("hold-key")).thenReturn(Optional.of(existingHold));

        assertThatThrownBy(() -> holdService.createHold(11L, validRequest(), "hold-key"))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Idempotency key is already associated with another learner");

        verifyNoInteractions(sessionMapper, mentorClient);
    }

    @Test
    void createHoldBuildsQuotedAmountForApprovedMentor() {
        CreateHoldRequest request = validRequest();
        when(holdRepository.findByIdempotencyKey("hold-key")).thenReturn(Optional.empty());
        when(mentorClient.getMentor(7L)).thenReturn(
                new MentorClient.MentorSnapshot(7L, 21L, new BigDecimal("400.00"), "APPROVED")
        );
        when(holdRepository.saveAndFlush(any(SessionBookingHold.class))).thenAnswer(invocation -> {
            SessionBookingHold hold = invocation.getArgument(0);
            hold.setId(44L);
            return hold;
        });
        when(sessionMapper.toHoldResponse(any(SessionBookingHold.class))).thenAnswer(invocation -> {
            SessionBookingHold hold = invocation.getArgument(0);
            return SessionHoldResponse.builder()
                    .id(hold.getId())
                    .mentorId(hold.getMentorId())
                    .learnerId(hold.getLearnerId())
                    .skillId(hold.getSkillId())
                    .startAt(hold.getStartAt())
                    .endAt(hold.getEndAt())
                    .quotedAmount(hold.getQuotedAmount())
                    .expiresAt(hold.getExpiresAt())
                    .status(hold.getStatus())
                    .idempotencyKey(hold.getIdempotencyKey())
                    .build();
        });

        SessionHoldResponse result = holdService.createHold(11L, request, " hold-key ");

        ArgumentCaptor<SessionBookingHold> holdCaptor = ArgumentCaptor.forClass(SessionBookingHold.class);
        verify(holdRepository).saveAndFlush(holdCaptor.capture());
        SessionBookingHold savedHold = holdCaptor.getValue();

        assertThat(savedHold.getMentorId()).isEqualTo(7L);
        assertThat(savedHold.getLearnerId()).isEqualTo(11L);
        assertThat(savedHold.getSkillId()).isEqualTo(5L);
        assertThat(savedHold.getStartAt()).isEqualTo(request.getStartAt());
        assertThat(savedHold.getEndAt()).isEqualTo(request.getStartAt().plus(Duration.ofMinutes(90)));
        assertThat(savedHold.getQuotedAmount()).isEqualByComparingTo("600.00");
        assertThat(savedHold.getStatus()).isEqualTo(HoldStatus.ACTIVE);
        assertThat(savedHold.getIdempotencyKey()).isEqualTo("hold-key");

        assertThat(result.getId()).isEqualTo(44L);
        assertThat(result.getQuotedAmount()).isEqualByComparingTo("600.00");
    }

    @Test
    void createHoldRequiresBookingAtLeastOneHourAhead() {
        CreateHoldRequest request = CreateHoldRequest.builder()
                .mentorId(7L)
                .skillId(5L)
                .startAt(Instant.now().plus(Duration.ofMinutes(30)))
                .durationMinutes(60)
                .build();

        when(holdRepository.findByIdempotencyKey("hold-key")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> holdService.createHold(11L, request, "hold-key"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Session must be booked at least 1 hour in advance");

        verifyNoInteractions(mentorClient, sessionMapper);
    }

    private CreateHoldRequest validRequest() {
        return CreateHoldRequest.builder()
                .mentorId(7L)
                .skillId(5L)
                .startAt(Instant.now().plus(Duration.ofHours(2)))
                .durationMinutes(90)
                .build();
    }
}
