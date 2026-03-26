package com.skillsync.skill.repository;

import com.skillsync.skill.model.Skill;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SkillRepository extends JpaRepository<Skill, Long> {

    List<Skill> findByIsActiveTrueOrderByNameAsc();

    List<Skill> findByCategoryIdAndIsActiveTrueOrderByNameAsc(Integer categoryId);

    List<Skill> findByNameContainingIgnoreCaseAndIsActiveTrue(String search);

    Optional<Skill> findBySlug(String slug);

    boolean existsByName(String name);

    boolean existsBySlug(String slug);

    List<Skill> findByIdIn(List<Long> ids);
}
