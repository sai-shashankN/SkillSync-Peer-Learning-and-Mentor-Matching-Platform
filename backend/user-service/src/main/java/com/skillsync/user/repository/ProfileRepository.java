package com.skillsync.user.repository;

import com.skillsync.user.model.Profile;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProfileRepository extends JpaRepository<Profile, Long> {

    Optional<Profile> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    @Query("""
            SELECT p
            FROM Profile p
            WHERE (
                :search IS NULL
                OR LOWER(COALESCE(p.bio, '')) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%'))
                OR LOWER(COALESCE(p.phone, '')) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%'))
                OR LOWER(COALESCE(p.avatarUrl, '')) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%'))
                OR LOWER(COALESCE(p.timezone, '')) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%'))
                OR LOWER(COALESCE(p.language, '')) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%'))
            )
            AND (
                CAST(:status AS text) IS NULL
                OR (CAST(:status AS text) = 'ACTIVE' AND p.deletedAt IS NULL)
                OR (CAST(:status AS text) = 'BANNED' AND p.deletedAt IS NOT NULL)
            )
            """)
    Page<Profile> searchProfiles(@Param("search") String search, @Param("status") String status, Pageable pageable);
}
