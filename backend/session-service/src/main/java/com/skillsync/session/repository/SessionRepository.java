package com.skillsync.session.repository;

import com.skillsync.session.model.Session;
import com.skillsync.session.model.enums.SessionStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SessionRepository extends JpaRepository<Session, Long> {

    Optional<Session> findByBookingReference(String bookingReference);

    Optional<Session> findByHoldId(Long holdId);

    Page<Session> findByLearnerIdAndStatusIn(Long learnerId, List<SessionStatus> statuses, Pageable pageable);

    Page<Session> findByMentorIdAndStatusIn(Long mentorId, List<SessionStatus> statuses, Pageable pageable);

    Page<Session> findByLearnerId(Long learnerId, Pageable pageable);

    Page<Session> findByMentorId(Long mentorId, Pageable pageable);

    long countByLearnerIdAndSkillIdAndStatus(Long learnerId, Long skillId, SessionStatus status);

    @Query("SELECT s FROM Session s WHERE s.mentorId = :userId OR s.learnerId = :userId")
    Page<Session> findByUserId(@Param("userId") Long userId, Pageable pageable);

    List<Session> findByStatusAndPaymentDeadlineAtBefore(SessionStatus status, Instant now);

    List<Session> findByStatusAndStartAtBetween(SessionStatus status, Instant start, Instant end);

    @Query("SELECT s FROM Session s WHERE "
            + "(:status IS NULL OR s.status = :status) "
            + "AND (:mentorId IS NULL OR s.mentorId = :mentorId) "
            + "AND (:learnerId IS NULL OR s.learnerId = :learnerId) "
            + "AND (:from IS NULL OR s.startAt >= :from) "
            + "AND (:to IS NULL OR s.startAt <= :to)")
    Page<Session> findByFilters(
            @Param("status") SessionStatus status,
            @Param("mentorId") Long mentorId,
            @Param("learnerId") Long learnerId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable
    );
}
