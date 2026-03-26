package com.skillsync.mentor.repository;

import com.skillsync.mentor.model.Mentor;
import com.skillsync.mentor.model.enums.MentorStatus;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MentorRepository extends JpaRepository<Mentor, Long> {

    Optional<Mentor> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    Page<Mentor> findByStatus(MentorStatus status, Pageable pageable);

    @Query("SELECT m FROM Mentor m WHERE m.status = 'APPROVED' "
            + "AND (:skillId IS NULL OR :skillId IN (SELECT s FROM m.skillIds s)) "
            + "AND (:minRating IS NULL OR m.avgRating >= :minRating) "
            + "AND (:maxPrice IS NULL OR m.hourlyRate <= :maxPrice)")
    Page<Mentor> searchMentors(
            @Param("skillId") Long skillId,
            @Param("minRating") BigDecimal minRating,
            @Param("maxPrice") BigDecimal maxPrice,
            Pageable pageable
    );
}
