package com.skillsync.skill.repository;

import com.skillsync.skill.model.SkillCategory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SkillCategoryRepository extends JpaRepository<SkillCategory, Integer> {

    List<SkillCategory> findByIsActiveTrueOrderByDisplayOrderAsc();

    Optional<SkillCategory> findBySlug(String slug);

    boolean existsByName(String name);

    boolean existsBySlug(String slug);
}
