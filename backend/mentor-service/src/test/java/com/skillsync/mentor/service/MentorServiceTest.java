package com.skillsync.mentor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.skillsync.common.exception.BadRequestException;
import com.skillsync.common.exception.ConflictException;
import com.skillsync.common.exception.UnauthorizedException;
import com.skillsync.mentor.client.SkillValidationClient;
import com.skillsync.mentor.dto.MentorApplicationRequest;
import com.skillsync.mentor.dto.MentorResponse;
import com.skillsync.mentor.dto.UpdateSkillsRequest;
import com.skillsync.mentor.mapper.MentorMapper;
import com.skillsync.mentor.model.Mentor;
import com.skillsync.mentor.model.enums.MentorStatus;
import com.skillsync.mentor.repository.MentorAvailabilityRepository;
import com.skillsync.mentor.repository.MentorRepository;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MentorServiceTest {

    @Mock
    private MentorRepository mentorRepository;

    @Mock
    private MentorAvailabilityRepository mentorAvailabilityRepository;

    @Mock
    private SkillValidationClient skillValidationClient;

    @Mock
    private EventPublisherService eventPublisherService;

    @Mock
    private MentorMapper mentorMapper;

    private MentorService mentorService;

    @BeforeEach
    void setUp() {
        mentorService = new MentorService(
                mentorRepository,
                mentorAvailabilityRepository,
                skillValidationClient,
                eventPublisherService,
                mentorMapper
        );
    }

    @Test
    void applyAsMentorNormalizesFieldsAndPublishesEvent() {
        MentorApplicationRequest request = MentorApplicationRequest.builder()
                .headline("  Senior Java Mentor  ")
                .bio("  Helping learners grow  ")
                .experience("10 years in mentoring")
                .hourlyRate(new BigDecimal("500"))
                .skillIds(new LinkedHashSet<>(Set.of(3L, 7L)))
                .build();
        MentorResponse mappedResponse = MentorResponse.builder()
                .id(55L)
                .userId(11L)
                .userName("Priya")
                .status("PENDING")
                .build();

        when(mentorRepository.existsByUserId(11L)).thenReturn(false);
        when(mentorRepository.save(any(Mentor.class))).thenAnswer(invocation -> {
            Mentor mentor = invocation.getArgument(0);
            mentor.setId(55L);
            return mentor;
        });
        when(mentorMapper.toMentorResponse(any(Mentor.class), eq("Priya"))).thenReturn(mappedResponse);

        MentorResponse result = mentorService.applyAsMentor(11L, "Priya", request);

        ArgumentCaptor<Mentor> mentorCaptor = ArgumentCaptor.forClass(Mentor.class);
        verify(mentorRepository).save(mentorCaptor.capture());
        Mentor savedMentor = mentorCaptor.getValue();

        assertThat(savedMentor.getUserId()).isEqualTo(11L);
        assertThat(savedMentor.getHeadline()).isEqualTo("Senior Java Mentor");
        assertThat(savedMentor.getBio()).isEqualTo("Helping learners grow");
        assertThat(savedMentor.getExperienceYears()).isEqualTo(10);
        assertThat(savedMentor.getHourlyRate()).isEqualByComparingTo("500.00");
        assertThat(savedMentor.getSkillIds()).containsExactlyInAnyOrder(3L, 7L);
        assertThat(savedMentor.getStatus()).isEqualTo(MentorStatus.PENDING);

        verify(skillValidationClient).validateSkillIds(new LinkedHashSet<>(Set.of(3L, 7L)));
        verify(eventPublisherService).publishMentorApplied(savedMentor, "Priya");
        assertThat(result).isEqualTo(mappedResponse);
    }

    @Test
    void applyAsMentorRejectsDuplicateApplication() {
        MentorApplicationRequest request = MentorApplicationRequest.builder()
                .bio("Bio")
                .experienceYears(5)
                .hourlyRate(new BigDecimal("100"))
                .skillIds(Set.of())
                .build();

        when(mentorRepository.existsByUserId(11L)).thenReturn(true);

        assertThatThrownBy(() -> mentorService.applyAsMentor(11L, "Priya", request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("User has already applied as a mentor");

        verifyNoInteractions(skillValidationClient, eventPublisherService, mentorMapper);
    }

    @Test
    void applyAsMentorRequiresParsableExperienceWhenYearsMissing() {
        MentorApplicationRequest request = MentorApplicationRequest.builder()
                .bio("Bio")
                .experience("Several years")
                .hourlyRate(new BigDecimal("100"))
                .skillIds(Set.of())
                .build();

        when(mentorRepository.existsByUserId(11L)).thenReturn(false);

        assertThatThrownBy(() -> mentorService.applyAsMentor(11L, "Priya", request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Experience must start with the number of years");
    }

    @Test
    void updateRatingRecalculatesAverageAndReviewCount() {
        Mentor mentor = Mentor.builder()
                .id(7L)
                .userId(15L)
                .avgRating(new BigDecimal("4.50"))
                .totalReviews(2)
                .build();

        when(mentorRepository.findById(7L)).thenReturn(Optional.of(mentor));
        when(mentorRepository.save(any(Mentor.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mentorService.updateRating(7L, 3);

        assertThat(mentor.getAvgRating()).isEqualByComparingTo("4.00");
        assertThat(mentor.getTotalReviews()).isEqualTo(3);
        verify(mentorRepository).save(mentor);
    }

    @Test
    void updateMentorSkillsRejectsNonOwner() {
        Mentor mentor = Mentor.builder()
                .id(5L)
                .userId(99L)
                .skillIds(new LinkedHashSet<>(Set.of(1L)))
                .build();
        UpdateSkillsRequest request = UpdateSkillsRequest.builder()
                .skillIds(Set.of(2L, 3L))
                .build();

        when(mentorRepository.findById(5L)).thenReturn(Optional.of(mentor));

        assertThatThrownBy(() -> mentorService.updateMentorSkills(5L, 100L, "Priya", request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("You are not allowed to modify this mentor");

        verifyNoInteractions(skillValidationClient, eventPublisherService, mentorMapper);
    }
}
