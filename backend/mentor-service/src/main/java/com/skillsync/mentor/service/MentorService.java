package com.skillsync.mentor.service;

import com.skillsync.common.dto.PagedResponse;
import com.skillsync.common.exception.BadRequestException;
import com.skillsync.common.exception.ConflictException;
import com.skillsync.common.exception.ResourceNotFoundException;
import com.skillsync.common.exception.UnauthorizedException;
import com.skillsync.mentor.client.SkillValidationClient;
import com.skillsync.mentor.dto.MentorApplicationRequest;
import com.skillsync.mentor.dto.MentorDetailResponse;
import com.skillsync.mentor.dto.MentorResponse;
import com.skillsync.mentor.dto.MentorSummaryResponse;
import com.skillsync.mentor.dto.RejectMentorRequest;
import com.skillsync.mentor.dto.UpdateMentorRequest;
import com.skillsync.mentor.dto.UpdateSkillsRequest;
import com.skillsync.mentor.mapper.MentorMapper;
import com.skillsync.mentor.model.Mentor;
import com.skillsync.mentor.model.enums.MentorStatus;
import com.skillsync.mentor.repository.MentorAvailabilityRepository;
import com.skillsync.mentor.repository.MentorRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class MentorService {

    private static final Pattern EXPERIENCE_PREFIX_PATTERN = Pattern.compile("^(\\d+)");

    private final MentorRepository mentorRepository;
    private final MentorAvailabilityRepository mentorAvailabilityRepository;
    private final SkillValidationClient skillValidationClient;
    private final EventPublisherService eventPublisherService;
    private final MentorMapper mentorMapper;

    @Transactional
    @CacheEvict(cacheNames = "mentorSearch", allEntries = true)
    public MentorResponse applyAsMentor(Long userId, String userName, MentorApplicationRequest request) {
        if (mentorRepository.existsByUserId(userId)) {
            throw new ConflictException("User has already applied as a mentor");
        }

        Set<Long> skillIds = normalizeSkillIds(request.getSkillIds());
        skillValidationClient.validateSkillIds(skillIds);
        Mentor mentor = Mentor.builder()
                .userId(userId)
                .headline(normalizeOptionalText(request.getHeadline(), "Headline must not be blank"))
                .bio(requireText(request.getBio(), "Bio is required"))
                .experienceYears(resolveExperienceYears(request))
                .hourlyRate(request.getHourlyRate().setScale(2, RoundingMode.HALF_UP))
                .skillIds(new LinkedHashSet<>(skillIds))
                .status(MentorStatus.PENDING)
                .build();
        Mentor savedMentor = mentorRepository.save(mentor);
        eventPublisherService.publishMentorApplied(savedMentor, userName);
        return mentorMapper.toMentorResponse(savedMentor, userName);
    }

    @Transactional(readOnly = true)
    public MentorDetailResponse getMentorById(Long id) {
        return getMentorById(id, null);
    }

    @Transactional(readOnly = true)
    public MentorDetailResponse getMentorById(Long id, String userName) {
        Mentor mentor = getRequiredMentor(id);
        return mentorMapper.toMentorDetailResponse(
                mentor,
                userName,
                mentorAvailabilityRepository.findByMentorIdAndIsActiveTrue(id).stream()
                        .map(mentorMapper::toAvailabilityResponse)
                        .toList()
        );
    }

    @Transactional(readOnly = true)
    public Mentor getMentorByUserId(Long userId) {
        return mentorRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Mentor", "userId", userId));
    }

    @Transactional(readOnly = true)
    public MentorDetailResponse getMentorByUserId(Long userId, String userName) {
        Mentor mentor = getMentorByUserId(userId);
        return getMentorById(mentor.getId(), userName);
    }

    @Transactional
    @CacheEvict(cacheNames = "mentorSearch", allEntries = true)
    public MentorResponse updateMentor(Long id, Long userId, String userName, UpdateMentorRequest request) {
        Mentor mentor = getRequiredMentor(id);
        assertOwner(mentor, userId);

        if (request.getBio() != null) {
            mentor.setBio(requireText(request.getBio(), "Bio must not be blank"));
        }
        if (request.getHeadline() != null) {
            mentor.setHeadline(normalizeOptionalText(request.getHeadline(), "Headline must not be blank"));
        }
        if (request.getExperienceYears() != null) {
            mentor.setExperienceYears(request.getExperienceYears());
        }
        if (request.getHourlyRate() != null) {
            mentor.setHourlyRate(request.getHourlyRate().setScale(2, RoundingMode.HALF_UP));
        }

        return mentorMapper.toMentorResponse(mentorRepository.save(mentor), userName);
    }

    @Transactional
    @CacheEvict(cacheNames = "mentorSearch", allEntries = true)
    public MentorResponse updateMentorSkills(Long id, Long userId, String userName, UpdateSkillsRequest request) {
        Mentor mentor = getRequiredMentor(id);
        assertOwner(mentor, userId);
        skillValidationClient.validateSkillIds(request.getSkillIds());
        mentor.setSkillIds(new LinkedHashSet<>(request.getSkillIds()));
        return mentorMapper.toMentorResponse(mentorRepository.save(mentor), userName);
    }

    @Transactional(readOnly = true)
    @Cacheable("mentorSearch")
    public PagedResponse<MentorSummaryResponse> searchMentors(
            Long skillId,
            BigDecimal minRating,
            BigDecimal maxPrice,
            Pageable pageable
    ) {
        Page<Mentor> mentors = mentorRepository.searchMentors(skillId, minRating, maxPrice, pageable);
        return PagedResponse.<MentorSummaryResponse>builder()
                .content(mentors.stream().map(mentor -> mentorMapper.toMentorSummaryResponse(mentor, null)).toList())
                .page(mentors.getNumber())
                .size(mentors.getSize())
                .totalElements(mentors.getTotalElements())
                .totalPages(mentors.getTotalPages())
                .last(mentors.isLast())
                .build();
    }

    @Transactional
    @CacheEvict(cacheNames = "mentorSearch", allEntries = true)
    public MentorResponse approveMentor(Long id, Long adminUserId, String email) {
        Mentor mentor = getRequiredMentor(id);
        mentor.setStatus(MentorStatus.APPROVED);
        mentor.setApprovedAt(Instant.now());
        mentor.setApprovedBy(adminUserId);
        mentor.setRejectionReason(null);
        Mentor savedMentor = mentorRepository.save(mentor);
        eventPublisherService.publishMentorApproved(savedMentor, email);
        return mentorMapper.toMentorResponse(savedMentor, null);
    }

    @Transactional
    @CacheEvict(cacheNames = "mentorSearch", allEntries = true)
    public MentorResponse rejectMentor(Long id, Long adminUserId, RejectMentorRequest request, String email) {
        Mentor mentor = getRequiredMentor(id);
        mentor.setStatus(MentorStatus.REJECTED);
        mentor.setApprovedBy(adminUserId);
        mentor.setApprovedAt(null);
        mentor.setRejectionReason(requireText(request.getReason(), "Reason is required"));
        Mentor savedMentor = mentorRepository.save(mentor);
        eventPublisherService.publishMentorRejected(savedMentor, email);
        return mentorMapper.toMentorResponse(savedMentor, null);
    }

    @Transactional
    @CacheEvict(cacheNames = "mentorSearch", allEntries = true)
    public MentorResponse banMentor(Long id, Long adminUserId) {
        Mentor mentor = getRequiredMentor(id);
        mentor.setStatus(MentorStatus.BANNED);
        mentor.setApprovedBy(adminUserId);
        return mentorMapper.toMentorResponse(mentorRepository.save(mentor), null);
    }

    @Transactional
    public void updateRating(Long mentorId, Integer rating) {
        Mentor mentor = getRequiredMentor(mentorId);
        int totalReviews = mentor.getTotalReviews() != null ? mentor.getTotalReviews() : 0;
        BigDecimal currentAverage = mentor.getAvgRating() != null ? mentor.getAvgRating() : BigDecimal.ZERO;
        BigDecimal updatedAverage = currentAverage.multiply(BigDecimal.valueOf(totalReviews))
                .add(BigDecimal.valueOf(rating))
                .divide(BigDecimal.valueOf(totalReviews + 1L), 2, RoundingMode.HALF_UP);
        mentor.setAvgRating(updatedAverage);
        mentor.setTotalReviews(totalReviews + 1);
        mentorRepository.save(mentor);
    }

    @Transactional(readOnly = true)
    public PagedResponse<MentorResponse> listMentors(MentorStatus status, Pageable pageable) {
        Page<Mentor> mentors = status != null ? mentorRepository.findByStatus(status, pageable) : mentorRepository.findAll(pageable);
        return PagedResponse.<MentorResponse>builder()
                .content(mentors.stream().map(mentor -> mentorMapper.toMentorResponse(mentor, null)).toList())
                .page(mentors.getNumber())
                .size(mentors.getSize())
                .totalElements(mentors.getTotalElements())
                .totalPages(mentors.getTotalPages())
                .last(mentors.isLast())
                .build();
    }

    @Transactional(readOnly = true)
    public Mentor getRequiredMentor(Long id) {
        return mentorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Mentor", "id", id));
    }

    public void assertOwner(Mentor mentor, Long userId) {
        if (!mentor.getUserId().equals(userId)) {
            throw new UnauthorizedException("You are not allowed to modify this mentor");
        }
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BadRequestException(message);
        }
        return value.trim();
    }

    private String normalizeOptionalText(String value, String message) {
        if (value == null) {
            return null;
        }
        if (!StringUtils.hasText(value)) {
            throw new BadRequestException(message);
        }
        return value.trim();
    }

    private Integer resolveExperienceYears(MentorApplicationRequest request) {
        if (request.getExperienceYears() != null) {
            return request.getExperienceYears();
        }
        if (!StringUtils.hasText(request.getExperience())) {
            throw new BadRequestException("Experience years is required");
        }

        Matcher matcher = EXPERIENCE_PREFIX_PATTERN.matcher(request.getExperience().trim());
        if (!matcher.find()) {
            throw new BadRequestException("Experience must start with the number of years");
        }
        return Integer.parseInt(matcher.group(1));
    }

    private Set<Long> normalizeSkillIds(Set<Long> skillIds) {
        if (skillIds == null || skillIds.isEmpty()) {
            return Set.of();
        }
        return new LinkedHashSet<>(skillIds);
    }
}
