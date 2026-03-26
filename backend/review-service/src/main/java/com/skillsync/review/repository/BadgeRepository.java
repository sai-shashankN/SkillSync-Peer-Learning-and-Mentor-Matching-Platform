package com.skillsync.review.repository;

import com.skillsync.review.model.Badge;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BadgeRepository extends JpaRepository<Badge, Integer> {

    List<Badge> findBySkillId(Long skillId);

    Optional<Badge> findBySkillIdAndTier(Long skillId, String tier);
}
