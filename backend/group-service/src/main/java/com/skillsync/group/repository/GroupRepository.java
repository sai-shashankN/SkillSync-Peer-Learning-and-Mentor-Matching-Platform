package com.skillsync.group.repository;

import com.skillsync.group.model.Group;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupRepository extends JpaRepository<Group, Long> {

    Page<Group> findByIsActiveTrue(Pageable pageable);

    Optional<Group> findBySlug(String slug);

    boolean existsBySlug(String slug);

    @Query("SELECT g FROM StudyGroup g WHERE g.isActive = true "
            + "AND (:search IS NULL OR LOWER(g.name) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%')))")
    Page<Group> searchGroups(@Param("search") String search, Pageable pageable);

    @Query("SELECT DISTINCT g FROM StudyGroup g LEFT JOIN g.skillIds skillId "
            + "WHERE g.isActive = true "
            + "AND (:search IS NULL OR LOWER(g.name) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%'))) "
            + "AND (:skillId IS NULL OR skillId = :skillId)")
    Page<Group> searchGroups(@Param("search") String search, @Param("skillId") Long skillId, Pageable pageable);
}
