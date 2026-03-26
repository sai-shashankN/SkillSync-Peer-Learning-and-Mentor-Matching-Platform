package com.skillsync.review.repository;

import com.skillsync.review.model.UserBadge;
import com.skillsync.review.model.UserBadgeId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserBadgeRepository extends JpaRepository<UserBadge, UserBadgeId> {

    List<UserBadge> findByUserId(Long userId);

    boolean existsByUserIdAndBadgeId(Long userId, Integer badgeId);

    long countByUserId(Long userId);
}
