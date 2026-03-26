package com.skillsync.review.controller;

import com.skillsync.common.dto.ApiResponse;
import com.skillsync.common.dto.PagedResponse;
import com.skillsync.review.dto.BadgeResponse;
import com.skillsync.review.dto.CreateReviewRequest;
import com.skillsync.review.dto.ModerateReviewRequest;
import com.skillsync.review.dto.RatingAverageResponse;
import com.skillsync.review.dto.ReviewResponse;
import com.skillsync.review.dto.UserBadgeResponse;
import com.skillsync.review.service.BadgeService;
import com.skillsync.review.service.ReviewService;
import com.skillsync.review.util.RequestHeaderUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;
    private final BadgeService badgeService;

    @PostMapping
    public ResponseEntity<ApiResponse<ReviewResponse>> submitReview(
            HttpServletRequest request,
            @Valid @RequestBody CreateReviewRequest createReviewRequest
    ) {
        ReviewResponse response = reviewService.submitReview(
                RequestHeaderUtils.extractUserId(request),
                createReviewRequest
        );
        return ResponseEntity.ok(ApiResponse.ok("Review submitted successfully", response));
    }

    @GetMapping("/mentor/{mentorId}")
    public ResponseEntity<ApiResponse<PagedResponse<ReviewResponse>>> getMentorReviews(
            @PathVariable Long mentorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(ApiResponse.ok(
                "Reviews fetched successfully",
                reviewService.getMentorReviews(mentorId, pageable)
        ));
    }

    @GetMapping("/mentor/{mentorId}/average")
    public ResponseEntity<ApiResponse<RatingAverageResponse>> getMentorAverageRating(@PathVariable Long mentorId) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Average rating fetched successfully",
                reviewService.getMentorAverageRating(mentorId)
        ));
    }

    @PostMapping("/{id}/moderate")
    public ResponseEntity<ApiResponse<ReviewResponse>> moderateReview(
            HttpServletRequest request,
            @PathVariable Long id,
            @Valid @RequestBody ModerateReviewRequest moderateReviewRequest
    ) {
        RequestHeaderUtils.requireAdmin(request);
        ReviewResponse response = reviewService.moderateReview(
                id,
                RequestHeaderUtils.extractUserId(request),
                moderateReviewRequest
        );
        return ResponseEntity.ok(ApiResponse.ok("Review moderated successfully", response));
    }

    @GetMapping("/badges/me")
    public ResponseEntity<ApiResponse<List<UserBadgeResponse>>> getMyBadges(HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                "User badges fetched successfully",
                badgeService.getUserBadges(RequestHeaderUtils.extractUserId(request))
        ));
    }

    @GetMapping("/badges/user/{userId}")
    public ResponseEntity<ApiResponse<List<UserBadgeResponse>>> getUserBadges(
            HttpServletRequest request,
            @PathVariable Long userId
    ) {
        RequestHeaderUtils.requireAdmin(request);
        return ResponseEntity.ok(ApiResponse.ok("User badges fetched successfully", badgeService.getUserBadges(userId)));
    }

    @GetMapping("/badges/available")
    public ResponseEntity<ApiResponse<List<BadgeResponse>>> getAvailableBadges() {
        return ResponseEntity.ok(ApiResponse.ok("Available badges fetched successfully", badgeService.getAvailableBadges()));
    }
}
