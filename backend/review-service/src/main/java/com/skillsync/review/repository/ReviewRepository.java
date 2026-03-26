package com.skillsync.review.repository;

import com.skillsync.review.model.Review;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    Page<Review> findByMentorIdAndIsVisibleTrue(Long mentorId, Pageable pageable);

    Optional<Review> findBySessionIdAndLearnerId(Long sessionId, Long learnerId);

    long countByMentorIdAndIsVisibleTrue(Long mentorId);

    @Query("SELECT COALESCE(AVG(r.rating), 0) FROM Review r WHERE r.mentorId = :mentorId AND r.isVisible = true")
    Double getAverageRatingByMentorId(@Param("mentorId") Long mentorId);
}
