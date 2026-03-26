package com.skillsync.mentor.service;

import com.skillsync.common.exception.BadRequestException;
import com.skillsync.mentor.dto.AvailabilityResponse;
import com.skillsync.mentor.dto.SetAvailabilityRequest;
import com.skillsync.mentor.dto.UnavailabilityRequest;
import com.skillsync.mentor.dto.UnavailabilityResponse;
import com.skillsync.mentor.mapper.MentorMapper;
import com.skillsync.mentor.model.Mentor;
import com.skillsync.mentor.model.MentorAvailability;
import com.skillsync.mentor.model.MentorUnavailability;
import com.skillsync.mentor.repository.MentorAvailabilityRepository;
import com.skillsync.mentor.repository.MentorUnavailabilityRepository;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AvailabilityService {

    private final MentorService mentorService;
    private final MentorAvailabilityRepository mentorAvailabilityRepository;
    private final MentorUnavailabilityRepository mentorUnavailabilityRepository;
    private final MentorMapper mentorMapper;

    @Transactional
    @CacheEvict(cacheNames = "mentorSearch", allEntries = true)
    public List<AvailabilityResponse> setAvailability(Long mentorId, Long userId, SetAvailabilityRequest request) {
        Mentor mentor = mentorService.getRequiredMentor(mentorId);
        mentorService.assertOwner(mentor, userId);

        Set<String> seenSlots = new HashSet<>();
        List<MentorAvailability> availabilities = request.getSlots().stream()
                .map(slot -> {
                    if (!slot.getEndTime().isAfter(slot.getStartTime())) {
                        throw new BadRequestException("End time must be after start time");
                    }
                    String key = slot.getDayOfWeek() + ":" + slot.getStartTime();
                    if (!seenSlots.add(key)) {
                        throw new BadRequestException("Duplicate availability slots are not allowed");
                    }
                    return MentorAvailability.builder()
                            .mentorId(mentorId)
                            .dayOfWeek(slot.getDayOfWeek())
                            .startTime(slot.getStartTime())
                            .endTime(slot.getEndTime())
                            .isActive(true)
                            .build();
                })
                .toList();

        mentorAvailabilityRepository.deleteByMentorId(mentorId);
        return mentorAvailabilityRepository.saveAll(availabilities).stream()
                .map(mentorMapper::toAvailabilityResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AvailabilityResponse> getAvailability(Long mentorId) {
        mentorService.getRequiredMentor(mentorId);
        return mentorAvailabilityRepository.findByMentorIdAndIsActiveTrue(mentorId).stream()
                .map(mentorMapper::toAvailabilityResponse)
                .toList();
    }

    @Transactional
    public UnavailabilityResponse addUnavailability(Long mentorId, Long userId, UnavailabilityRequest request) {
        Mentor mentor = mentorService.getRequiredMentor(mentorId);
        mentorService.assertOwner(mentor, userId);

        if (!request.getBlockedTo().isAfter(request.getBlockedFrom())) {
            throw new BadRequestException("Blocked to must be after blocked from");
        }

        MentorUnavailability unavailability = MentorUnavailability.builder()
                .mentorId(mentorId)
                .blockedFrom(request.getBlockedFrom())
                .blockedTo(request.getBlockedTo())
                .reason(normalizeOptionalText(request.getReason()))
                .build();
        return mentorMapper.toUnavailabilityResponse(mentorUnavailabilityRepository.save(unavailability));
    }

    @Transactional(readOnly = true)
    public List<UnavailabilityResponse> getUnavailability(Long mentorId) {
        mentorService.getRequiredMentor(mentorId);
        return mentorUnavailabilityRepository.findByMentorIdAndBlockedToAfter(mentorId, Instant.now()).stream()
                .map(mentorMapper::toUnavailabilityResponse)
                .toList();
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        if (!StringUtils.hasText(value)) {
            throw new BadRequestException("Reason must not be blank");
        }
        return value.trim();
    }
}
