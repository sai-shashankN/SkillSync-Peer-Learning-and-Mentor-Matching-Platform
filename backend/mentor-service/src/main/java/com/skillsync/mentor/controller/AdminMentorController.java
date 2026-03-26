package com.skillsync.mentor.controller;

import com.skillsync.common.dto.ApiResponse;
import com.skillsync.common.dto.PagedResponse;
import com.skillsync.common.exception.BadRequestException;
import com.skillsync.mentor.dto.MentorDetailResponse;
import com.skillsync.mentor.dto.MentorResponse;
import com.skillsync.mentor.model.enums.MentorStatus;
import com.skillsync.mentor.service.MentorService;
import com.skillsync.mentor.util.RequestHeaderUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/mentors")
@RequiredArgsConstructor
public class AdminMentorController {

    private final MentorService mentorService;

    @GetMapping
    public ResponseEntity<PagedResponse<MentorResponse>> listMentors(
            HttpServletRequest request,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        RequestHeaderUtils.requireAdmin(request);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(mentorService.listMentors(parseStatus(status), pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<MentorDetailResponse>> getMentorById(
            HttpServletRequest request,
            @PathVariable Long id
    ) {
        RequestHeaderUtils.requireAdmin(request);
        return ResponseEntity.ok(ApiResponse.ok("Mentor fetched successfully", mentorService.getMentorById(id)));
    }

    private MentorStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return MentorStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid mentor status");
        }
    }
}
