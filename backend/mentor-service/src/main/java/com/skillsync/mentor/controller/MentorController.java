package com.skillsync.mentor.controller;

import com.skillsync.common.dto.ApiResponse;
import com.skillsync.common.dto.PagedResponse;
import com.skillsync.common.exception.BadRequestException;
import com.skillsync.common.exception.UnauthorizedException;
import com.skillsync.mentor.dto.AvailabilityResponse;
import com.skillsync.mentor.dto.MentorApplicationRequest;
import com.skillsync.mentor.dto.MentorDetailResponse;
import com.skillsync.mentor.dto.MentorResponse;
import com.skillsync.mentor.dto.MentorSummaryResponse;
import com.skillsync.mentor.dto.RejectMentorRequest;
import com.skillsync.mentor.dto.SetAvailabilityRequest;
import com.skillsync.mentor.dto.UnavailabilityRequest;
import com.skillsync.mentor.dto.UnavailabilityResponse;
import com.skillsync.mentor.dto.UpdateMentorRequest;
import com.skillsync.mentor.dto.UpdateSkillsRequest;
import com.skillsync.mentor.dto.WaitlistResponse;
import com.skillsync.mentor.model.Mentor;
import com.skillsync.mentor.service.AvailabilityService;
import com.skillsync.mentor.service.MentorService;
import com.skillsync.mentor.service.WaitlistService;
import com.skillsync.mentor.util.RequestHeaderUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mentors")
@RequiredArgsConstructor
public class MentorController {

    private final MentorService mentorService;
    private final AvailabilityService availabilityService;
    private final WaitlistService waitlistService;

    @PostMapping("/apply")
    public ResponseEntity<ApiResponse<MentorResponse>> applyAsMentor(
            HttpServletRequest request,
            @Valid @RequestBody MentorApplicationRequest mentorApplicationRequest
    ) {
        MentorResponse response = mentorService.applyAsMentor(
                RequestHeaderUtils.extractUserId(request),
                RequestHeaderUtils.extractUserName(request),
                mentorApplicationRequest
        );
        return ResponseEntity.ok(ApiResponse.ok("Mentor application submitted successfully", response));
    }

    @GetMapping
    public ResponseEntity<PagedResponse<MentorSummaryResponse>> searchMentors(
            @RequestParam(required = false) Long skillId,
            @RequestParam(required = false) BigDecimal minRating,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "avgRating,desc") String sort
    ) {
        return ResponseEntity.ok(mentorService.searchMentors(skillId, minRating, maxPrice, buildPageable(page, size, sort)));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MentorDetailResponse>> getMyMentorProfile(HttpServletRequest request) {
        MentorDetailResponse response = mentorService.getMentorByUserId(
                RequestHeaderUtils.extractUserId(request),
                RequestHeaderUtils.extractUserName(request)
        );
        return ResponseEntity.ok(ApiResponse.ok("Mentor fetched successfully", response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<MentorDetailResponse>> getMentorById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Mentor fetched successfully", mentorService.getMentorById(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<MentorResponse>> updateMentor(
            HttpServletRequest request,
            @PathVariable Long id,
            @Valid @RequestBody UpdateMentorRequest updateMentorRequest
    ) {
        MentorResponse response = mentorService.updateMentor(
                id,
                RequestHeaderUtils.extractUserId(request),
                RequestHeaderUtils.extractUserName(request),
                updateMentorRequest
        );
        return ResponseEntity.ok(ApiResponse.ok("Mentor updated successfully", response));
    }

    @PutMapping("/{id}/skills")
    public ResponseEntity<ApiResponse<MentorResponse>> updateMentorSkills(
            HttpServletRequest request,
            @PathVariable Long id,
            @Valid @RequestBody UpdateSkillsRequest updateSkillsRequest
    ) {
        MentorResponse response = mentorService.updateMentorSkills(
                id,
                RequestHeaderUtils.extractUserId(request),
                RequestHeaderUtils.extractUserName(request),
                updateSkillsRequest
        );
        return ResponseEntity.ok(ApiResponse.ok("Mentor skills updated successfully", response));
    }

    @PutMapping("/{id}/availability")
    public ResponseEntity<ApiResponse<List<AvailabilityResponse>>> setAvailability(
            HttpServletRequest request,
            @PathVariable Long id,
            @Valid @RequestBody SetAvailabilityRequest setAvailabilityRequest
    ) {
        List<AvailabilityResponse> response = availabilityService.setAvailability(
                id,
                RequestHeaderUtils.extractUserId(request),
                setAvailabilityRequest
        );
        return ResponseEntity.ok(ApiResponse.ok("Availability updated successfully", response));
    }

    @GetMapping("/{id}/availability")
    public ResponseEntity<ApiResponse<List<AvailabilityResponse>>> getAvailability(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Availability fetched successfully", availabilityService.getAvailability(id)));
    }

    @PostMapping("/{id}/unavailability")
    public ResponseEntity<ApiResponse<UnavailabilityResponse>> addUnavailability(
            HttpServletRequest request,
            @PathVariable Long id,
            @Valid @RequestBody UnavailabilityRequest unavailabilityRequest
    ) {
        UnavailabilityResponse response = availabilityService.addUnavailability(
                id,
                RequestHeaderUtils.extractUserId(request),
                unavailabilityRequest
        );
        return ResponseEntity.ok(ApiResponse.ok("Unavailability added successfully", response));
    }

    @GetMapping("/{id}/unavailability")
    public ResponseEntity<ApiResponse<List<UnavailabilityResponse>>> getUnavailability(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Unavailability fetched successfully",
                availabilityService.getUnavailability(id)
        ));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<MentorResponse>> approveMentor(HttpServletRequest request, @PathVariable Long id) {
        RequestHeaderUtils.requireAdmin(request);
        MentorResponse response = mentorService.approveMentor(
                id,
                RequestHeaderUtils.extractUserId(request),
                RequestHeaderUtils.extractUserEmail(request)
        );
        return ResponseEntity.ok(ApiResponse.ok("Mentor approved successfully", response));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<MentorResponse>> rejectMentor(
            HttpServletRequest request,
            @PathVariable Long id,
            @Valid @RequestBody RejectMentorRequest rejectMentorRequest
    ) {
        RequestHeaderUtils.requireAdmin(request);
        MentorResponse response = mentorService.rejectMentor(
                id,
                RequestHeaderUtils.extractUserId(request),
                rejectMentorRequest,
                RequestHeaderUtils.extractUserEmail(request)
        );
        return ResponseEntity.ok(ApiResponse.ok("Mentor rejected successfully", response));
    }

    @PostMapping("/{id}/ban")
    public ResponseEntity<ApiResponse<MentorResponse>> banMentor(HttpServletRequest request, @PathVariable Long id) {
        RequestHeaderUtils.requireAdmin(request);
        MentorResponse response = mentorService.banMentor(id, RequestHeaderUtils.extractUserId(request));
        return ResponseEntity.ok(ApiResponse.ok("Mentor banned successfully", response));
    }

    @PostMapping("/{id}/waitlist")
    public ResponseEntity<ApiResponse<WaitlistResponse>> joinWaitlist(HttpServletRequest request, @PathVariable Long id) {
        WaitlistResponse response = waitlistService.joinWaitlist(id, RequestHeaderUtils.extractUserId(request));
        return ResponseEntity.ok(ApiResponse.ok("Waitlist joined successfully", response));
    }

    @GetMapping("/{id}/waitlist")
    public ResponseEntity<ApiResponse<List<WaitlistResponse>>> getWaitlistEntries(
            HttpServletRequest request,
            @PathVariable Long id
    ) {
        Mentor mentor = mentorService.getRequiredMentor(id);
        Long userId = RequestHeaderUtils.extractUserId(request);
        if (!mentor.getUserId().equals(userId) && !RequestHeaderUtils.hasAdminRole(request)) {
            throw new UnauthorizedException("You are not allowed to view this waitlist");
        }
        return ResponseEntity.ok(ApiResponse.ok("Waitlist fetched successfully", waitlistService.getWaitlistEntries(id)));
    }

    private Pageable buildPageable(int page, int size, String sort) {
        String[] sortParts = sort.split(",");
        if (sortParts.length != 2) {
            throw new BadRequestException("Sort must be in the format field,direction");
        }
        Sort.Direction direction = Sort.Direction.fromString(sortParts[1].trim());
        return PageRequest.of(page, size, Sort.by(direction, sortParts[0].trim()));
    }
}
