package com.skillsync.review.service;

import com.skillsync.common.dto.PagedResponse;
import com.skillsync.common.exception.BadRequestException;
import com.skillsync.common.exception.ConflictException;
import com.skillsync.common.exception.ResourceNotFoundException;
import com.skillsync.review.client.SessionClient;
import com.skillsync.review.dto.CreateReviewRequest;
import com.skillsync.review.dto.ModerateReviewRequest;
import com.skillsync.review.dto.RatingAverageResponse;
import com.skillsync.review.dto.ReviewResponse;
import com.skillsync.review.mapper.ReviewMapper;
import com.skillsync.review.model.Review;
import com.skillsync.review.repository.ReviewRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ReviewMapper reviewMapper;
    private final SessionClient sessionClient;
    private final EventPublisherService eventPublisherService;
    private final BadgeService badgeService;

    @Transactional
    public ReviewResponse submitReview(Long learnerId, CreateReviewRequest request) {
        SessionClient.SessionSnapshot session = sessionClient.getSession(request.getSessionId());
        validateReviewRequest(learnerId, request, session);

        if (reviewRepository.findBySessionIdAndLearnerId(request.getSessionId(), learnerId).isPresent()) {
            throw new ConflictException("A review has already been submitted for this session");
        }

        Review savedReview = reviewRepository.save(Review.builder()
                .mentorId(session.mentorId())
                .learnerId(learnerId)
                .sessionId(session.sessionId())
                .rating(request.getRating())
                .comment(request.getComment())
                .build());

        eventPublisherService.publishReviewSubmitted(savedReview, session);
        badgeService.checkAndAwardBadges(learnerId, session.skillId());
        return reviewMapper.toReviewResponse(savedReview);
    }

    @Transactional(readOnly = true)
    public PagedResponse<ReviewResponse> getMentorReviews(Long mentorId, Pageable pageable) {
        Page<Review> page = reviewRepository.findByMentorIdAndIsVisibleTrue(mentorId, pageable);
        return PagedResponse.<ReviewResponse>builder()
                .content(page.getContent().stream().map(reviewMapper::toReviewResponse).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    @Transactional(readOnly = true)
    public RatingAverageResponse getMentorAverageRating(Long mentorId) {
        return RatingAverageResponse.builder()
                .mentorId(mentorId)
                .averageRating(reviewRepository.getAverageRatingByMentorId(mentorId))
                .totalReviews(reviewRepository.countByMentorIdAndIsVisibleTrue(mentorId))
                .build();
    }

    @Transactional
    public ReviewResponse moderateReview(Long reviewId, Long adminId, ModerateReviewRequest request) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review", "id", reviewId));

        review.setIsVisible(request.getIsVisible());
        review.setIsModerated(true);
        review.setModeratedBy(adminId);
        review.setModeratedAt(Instant.now());
        review.setModerationReason(request.getReason());
        return reviewMapper.toReviewResponse(reviewRepository.save(review));
    }

    private void validateReviewRequest(
            Long learnerId,
            CreateReviewRequest request,
            SessionClient.SessionSnapshot session
    ) {
        if (!"COMPLETED".equalsIgnoreCase(session.status())) {
            throw new BadRequestException("Only completed sessions can be reviewed");
        }
        if (!session.learnerId().equals(learnerId)) {
            throw new BadRequestException("You can only review sessions booked by you");
        }
        if (!session.mentorId().equals(request.getMentorId())) {
            throw new BadRequestException("Mentor does not match the session");
        }
    }
}
